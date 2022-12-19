//! Module provides the main gRPC server for the plugin process

use std::collections::HashMap;
use std::fs::File;
use std::io::BufReader;
use anyhow::anyhow;
use bytes::Bytes;
use maplit::hashmap;

use pact_matching::{BodyMatchResult, Mismatch};
use pact_models::json_utils::json_to_string;
use pact_models::matchingrules::MatchingRule;
use pact_models::path_exp::DocPath;
use pact_models::prelude::{ContentType, MatchingRuleCategory, RuleLogic};
use pact_plugin_driver::plugin_models::PactPluginManifest;
use pact_plugin_driver::proto;
use pact_plugin_driver::proto::catalogue_entry::EntryType;
use pact_plugin_driver::proto::pact_plugin_server::PactPlugin;
use pact_plugin_driver::utils::{proto_struct_to_json, proto_struct_to_map, proto_value_to_json, proto_value_to_string, to_proto_value};
use serde_json::Value;
use tonic::{Request, Response, Status};
use tracing::{debug, error, info, trace};
use crate::utils::get_descriptors_for_interaction;

/// Plugin gRPC server implementation
#[derive(Debug, Default)]
pub struct AvroPactPlugin {
  manifest: PactPluginManifest
}

impl AvroPactPlugin {
  /// Create a new plugin instance
  pub fn new() -> Self {
    let manifest = File::open("./pact-plugin.json")
      .and_then(|file| {
        let reader = BufReader::new(file);
        match serde_json::from_reader::<BufReader<File>, PactPluginManifest>(reader) {
          Ok(manifest) => Ok(manifest),
          Err(err) => Err(err.into())
        }
      })
      .unwrap_or_default();
    AvroPactPlugin { manifest }
  }

  /// Return a Tonic error response for the given error
  fn error_response<E>(err: E) -> Result<Response<proto::CompareContentsResponse>, Status>
    where E: Into<String> {
    Ok(Response::new(proto::CompareContentsResponse {
      error: err.into(),
      ..proto::CompareContentsResponse::default()
    }))
  }

  /// Returns the configured hostname to bind to from the configuration in the manifest.
  pub fn host_to_bind_to(&self) -> Option<String> {
    self.manifest.plugin_config
      .get("hostToBindTo")
      .map(|host| json_to_string(host))
  }

  /// Returns any additional include paths from the configuration in the manifest to add to the
  /// Avro compiler call.
  pub fn additional_includes(&self) -> Vec<String> {
    self.manifest.plugin_config
      .get("additionalIncludes")
      .map(|includes| {
        match includes {
          Value::Array(list) => list.iter().map(|v| json_to_string(v)).collect(),
          _ => vec![json_to_string(includes)]
        }
      })
      .unwrap_or_default()
  }

  fn get_mock_server_results(results: &HashMap<String, (usize, Vec<BodyMatchResult>)>) -> (bool, Vec<proto::MockServerResult>) {
    // All OK if there are no mismatches and all routes got at least one request
    let ok = results.iter().all(|(_, (req, r))| {
      *req > 0 && r.iter().all(|r| *r == BodyMatchResult::Ok)
    });
    let results = results.iter().flat_map(|(path, (req, r))| {
      let mut route_results = vec![];

      if *req == 0 {
        route_results.push(proto::MockServerResult {
          path: path.clone(),
          error: format!("Did not receive any requests for path '{}'", path),
          ..proto::MockServerResult::default()
        });
      } else {
        route_results.push(proto::MockServerResult {
          path: path.clone(),
          mismatches: r.iter().flat_map(|result| {
            let mismatches = result.mismatches();
            mismatches.iter().map(|m| {
              match m {
                Mismatch::BodyMismatch { path, mismatch, expected, actual } => {
                  proto::ContentMismatch {
                    expected: expected.as_ref().map(|d| d.to_vec()),
                    actual: actual.as_ref().map(|d| d.to_vec()),
                    mismatch: mismatch.clone(),
                    path: path.clone(),
                    ..proto::ContentMismatch::default()
                  }
                }
                _ => proto::ContentMismatch {
                  mismatch: m.description(),
                  ..proto::ContentMismatch::default()
                }
              }
            })
            .collect::<Vec<proto::ContentMismatch>>()
          }).collect(),
          ..proto::MockServerResult::default()
        });
      }

      route_results
    }).collect();
    (ok, results)
  }
}

#[tonic::async_trait]
impl PactPlugin for AvroPactPlugin {
  async fn init_plugin(&self, request: Request<proto::InitPluginRequest>) -> Result<Response<proto::InitPluginResponse>, Status> {
    let message = request.get_ref();
    debug!("Init request from {}/{}", message.implementation, message.version);

    // Return an entry for a content matcher and content generator for Avro messages
    Ok(Response::new(proto::InitPluginResponse {
      catalogue: vec![
        proto::CatalogueEntry {
          r#type: EntryType::ContentMatcher as i32,
          key: "avro".to_string(),
          values: hashmap! {
            "content-types".to_string() => "application/avro;application/grpc".to_string()
          }
        },
        proto::CatalogueEntry {
          r#type: EntryType::ContentGenerator as i32,
          key: "avro".to_string(),
          values: hashmap! {
            "content-types".to_string() => "application/avro;application/grpc".to_string()
          }
        },
        proto::CatalogueEntry {
          r#type: EntryType::Transport as i32,
          key: "grpc".to_string(),
          values: hashmap! {}
        }
      ]
    }))
  }

  async fn update_catalogue(&self, _request: Request<proto::Catalogue>) -> Result<Response<()>, Status> {
    debug!("Update catalogue request");

    // currently a no-op
    Ok(Response::new(()))
  }

  async fn compare_contents(&self, request: Request<proto::CompareContentsRequest>) -> Result<Response<proto::CompareContentsResponse>, Status> {
    trace!("Got compare_contents request {:?}", request.get_ref());

    let request = request.get_ref();

    // Check for the plugin specific configuration for the interaction
    let plugin_configuration = request.plugin_configuration.clone().unwrap_or_default();
    let interaction_config = plugin_configuration.interaction_configuration.as_ref()
        .map(|config| &config.fields);
    let interaction_config = match interaction_config {
      Some(config) => config,
      None => {
        error!("Plugin configuration for the interaction is required");
        return Self::error_response("Plugin configuration for the interaction is required")
      }
    };

    // From the plugin configuration for the interaction, get the descriptor key. This key is used
    // to lookup the encoded Avro descriptors in the Pact level plugin configuration
    let message_key = match interaction_config.get("descriptorKey").and_then(proto_value_to_string) {
      Some(key) => key,
      None => {
        error!("Plugin configuration item with key 'descriptorKey' is required");
        return Self::error_response("Plugin configuration item with key 'descriptorKey' is required");
      }
    };
    debug!("compare_contents: message_key = {}", message_key);

    // From the plugin configuration for the interaction, there should be either a message type name
    // or a service name. Check for either.
    let message = interaction_config.get("message").and_then(proto_value_to_string);
    let service = interaction_config.get("service").and_then(proto_value_to_string);
    if message.is_none() && service.is_none() {
      error!("Plugin configuration item with key 'message' or 'service' is required");
      return Self::error_response("Plugin configuration item with key 'message' or 'service' is required");
    }

    let pact_configuration = plugin_configuration.pact_configuration.unwrap_or_default();
    debug!("Pact level configuration keys: {:?}", pact_configuration.fields.keys());

    let config_for_interaction = pact_configuration.fields.iter()
        .map(|(key, config)| (key.clone(), proto_value_to_json(config)))
        .collect();
    let descriptors = match get_descriptors_for_interaction(message_key.as_str(), &config_for_interaction) {
      Ok(descriptors) => descriptors,
      Err(err) => return Self::error_response(err.to_string())
    };

    let mut expected_body = request.expected.as_ref()
        .and_then(|body| body.content.clone().map(Bytes::from))
        .unwrap_or_default();
    let mut actual_body = request.actual.as_ref()
        .map(|body| body.content.clone().map(Bytes::from))
        .flatten()
        .unwrap_or_default();
    let mut matching_rules = MatchingRuleCategory::empty("body");
    for (key, rules) in &request.rules {
      for rule in &rules.rule {
        let values = rule.values.as_ref().map(proto_struct_to_json).unwrap_or_default();
        let doc_path = match DocPath::new(key) {
          Ok(path) => path,
          Err(err) => return Self::error_response(err.to_string())
        };
        let matching_rule = match MatchingRule::create(&rule.r#type, &values) {
          Ok(rule) => rule,
          Err(err) => return Self::error_response(err.to_string())
        };
        matching_rules.add_rule(doc_path, matching_rule, RuleLogic::And);
      }
    }

    let result = if let Some(message_name) = message {
      debug!("Received compareContents request for message {}", message_name);
      // match_message(
      //   message_name.as_str(),
      //   &descriptors,
      //   &mut expected_body,
      //   &mut actual_body,
      //   &matching_rules,
      //   request.allow_unexpected_keys
      // )
      Ok(BodyMatchResult::Ok)
    } else if let Some(service_name) = service {
      debug!("Received compareContents request for service {}", service_name);
      let (service, method) = match service_name.split_once('/') {
        Some(result) => result,
        None => return Self::error_response(format!("Service name '{}' is not valid, it should be of the form <SERVICE>/<METHOD>", service_name))
      };
      let content_type = request.expected.as_ref().map(|body| body.content_type.clone())
          .unwrap_or_default();
      let expected_content_type = match ContentType::parse(content_type.as_str()) {
        Ok(ct) => ct,
        Err(err) => return Self::error_response(format!("Expected content type is not set or not valid - {}", err))
      };
      // match_service(
      //   service,
      //   method,
      //   &descriptors,
      //   &mut expected_body,
      //   &mut actual_body,
      //   &matching_rules,
      //   request.allow_unexpected_keys,
      //   &expected_content_type
      // )
      Ok(BodyMatchResult::Ok)
    } else {
      Err(anyhow!("Did not get a message or service to match"))
    };

    return match result {
      Ok(result) => match result {
        BodyMatchResult::Ok => Ok(Response::new(proto::CompareContentsResponse::default())),
        BodyMatchResult::BodyTypeMismatch { message, expected_type, actual_type, .. } => {
          error!("Got a BodyTypeMismatch - {}", message);
          Ok(Response::new(proto::CompareContentsResponse {
            type_mismatch: Some(proto::ContentTypeMismatch {
              expected: expected_type,
              actual: actual_type
            }),
            .. proto::CompareContentsResponse::default()
          }))
        }
        BodyMatchResult::BodyMismatches(mismatches) => {
          Ok(Response::new(proto::CompareContentsResponse {
            results: mismatches.iter().map(|(k, v)| {
              (k.clone(), proto::ContentMismatches {
                mismatches: v.iter().map(mismatch_to_proto_mismatch).collect()
              })
            }).collect(),
            .. proto::CompareContentsResponse::default()
          }))
        }
      }
      Err(err) => Self::error_response(format!("Failed to compare the Avro messages - {}", err))
    }
  }

  async fn configure_interaction(&self, request: Request<proto::ConfigureInteractionRequest>) -> Result<Response<proto::ConfigureInteractionResponse>, Status> {
    let message = request.get_ref();
    debug!("Configure interaction request for content type '{}'", message.content_type);
    todo!()
    /*// Check for the "pact:proto" key
    let fields = message.contents_config.as_ref().map(|config| config.fields.clone()).unwrap_or_default();
    let proto_file = match fields.get("pact:proto").and_then(proto_value_to_string) {
      Some(pf) => pf,
      None => {
        error!("Config item with key 'pact:proto' and path to the proto file is required");
        return Ok(Response::new(proto::ConfigureInteractionResponse {
          error: "Config item with key 'pact:proto' and path to the proto file is required".to_string(),
          .. proto::ConfigureInteractionResponse::default()
        }))
      }
    };

    // Check for either the message type or proto service
    if !fields.contains_key("pact:message-type") && !fields.contains_key("pact:proto-service") {
      let message = "Config item with key 'pact:message-type' and the avro message name or 'pact:proto-service' and the service name is required".to_string();
      error!("{}", message);
      return Ok(Response::new(proto::ConfigureInteractionResponse {
        error: message,
        .. proto::ConfigureInteractionResponse::default()
      }))
    }

    // Make sure we can execute the avro compiler
    let protoc = match setup_protoc(&self.manifest.plugin_config, &self.additional_includes()).await {
      Ok(protoc) => protoc,
      Err(err) => {
        error!("Failed to invoke protoc: {}", err);
        return Ok(Response::new(proto::ConfigureInteractionResponse {
          error: format!("Failed to invoke protoc: {}", err),
          .. proto::ConfigureInteractionResponse::default()
        }))
      }
    };

    // Process the proto file and configure the interaction
    match process_proto(proto_file, &protoc, &fields).await {
      Ok((interactions, plugin_config)) => {
        Ok(Response::new(proto::ConfigureInteractionResponse {
          interaction: interactions,
          plugin_configuration: Some(plugin_config),
          .. proto::ConfigureInteractionResponse::default()
        }))
      }
      Err(err) => {
        error!("Failed to process avro: {}", err);
        Ok(Response::new(proto::ConfigureInteractionResponse {
          error: format!("Failed to process avro: {}", err),
          .. proto::ConfigureInteractionResponse::default()
        }))
      }
    }*/
  }

  async fn generate_content(&self, request: Request<proto::GenerateContentRequest>) -> Result<Response<proto::GenerateContentResponse>, Status> {
    debug!("Generate content request");
    let message = request.get_ref();
    // TODO: apply any generators here
    Ok(Response::new(proto::GenerateContentResponse {
      contents: message.contents.clone()
    }))
  }

  async fn start_mock_server(&self, request: Request<proto::StartMockServerRequest>) -> Result<Response<proto::StartMockServerResponse>, Status> {
    debug!("Start mock server");
    return Ok(tonic::Response::new(proto::StartMockServerResponse {
      response: Some(proto::start_mock_server_response::Response::Error(format!("Mock server not supported"))),
      .. proto::StartMockServerResponse::default()
    }));
  }

  async fn shutdown_mock_server(&self, request: Request<proto::ShutdownMockServerRequest>) -> Result<Response<proto::ShutdownMockServerResponse>, Status> {
    debug!("Shutdown mock server");
    return Ok(Response::new(proto::ShutdownMockServerResponse {
      ok: false,
      results: vec![
        proto::MockServerResult {
          error: format!("Did not find any mock server results for a server with ID {}", "request.server_key"),
          .. proto::MockServerResult::default()
        }
      ]
    }));
  }

  async fn get_mock_server_results(&self, request: Request<proto::MockServerRequest>) -> Result<Response<proto::MockServerResults>, Status> {
    debug!("Get mock server results");
    Ok(Response::new(proto::MockServerResults {
      ok: false,
      results: vec![
        proto::MockServerResult {
          error: format!("Did not find any mock server results for a server with ID {}", "request.server_key"),
          .. proto::MockServerResult::default()
        }
      ]
    }))
  }

  async fn prepare_interaction_for_verification(&self, request: Request<proto::VerificationPreparationRequest>) -> Result<Response<proto::VerificationPreparationResponse>, Status> {
    todo!()
  }

  async fn verify_interaction(&self, request: Request<proto::VerifyInteractionRequest>) -> Result<Response<proto::VerifyInteractionResponse>, Status> {
    todo!()
  }
}

fn mismatch_to_proto_mismatch(mismatch: &Mismatch) -> proto::ContentMismatch {
  match mismatch {
    Mismatch::MethodMismatch { expected, actual } => {
      proto::ContentMismatch {
        expected: Some(expected.as_bytes().to_vec()),
        actual: Some(actual.as_bytes().to_vec()),
        mismatch: "Method mismatch".to_string(),
        ..proto::ContentMismatch::default()
      }
    }
    Mismatch::PathMismatch { expected, actual, mismatch } => {
      proto::ContentMismatch {
        expected: Some(expected.as_bytes().to_vec()),
        actual: Some(actual.as_bytes().to_vec()),
        mismatch: mismatch.clone(),
        ..proto::ContentMismatch::default()
      }
    }
    Mismatch::StatusMismatch { expected, actual, mismatch } => {
      proto::ContentMismatch {
        expected: Some(expected.to_string().as_bytes().to_vec()),
        actual: Some(actual.to_string().as_bytes().to_vec()),
        mismatch: mismatch.clone(),
        ..proto::ContentMismatch::default()
      }
    }
    Mismatch::QueryMismatch { expected, actual, mismatch, .. } => {
      proto::ContentMismatch {
        expected: Some(expected.as_bytes().to_vec()),
        actual: Some(actual.as_bytes().to_vec()),
        mismatch: mismatch.clone(),
        ..proto::ContentMismatch::default()
      }
    }
    Mismatch::HeaderMismatch { expected, actual, mismatch, .. } => {
      proto::ContentMismatch {
        expected: Some(expected.as_bytes().to_vec()),
        actual: Some(actual.as_bytes().to_vec()),
        mismatch: mismatch.clone(),
        ..proto::ContentMismatch::default()
      }
    }
    Mismatch::BodyTypeMismatch { expected, actual, mismatch, .. } => {
      proto::ContentMismatch {
        expected: Some(expected.as_bytes().to_vec()),
        actual: Some(actual.as_bytes().to_vec()),
        mismatch: mismatch.clone(),
        ..proto::ContentMismatch::default()
      }
    }
    Mismatch::BodyMismatch { path, expected, actual, mismatch } => {
      proto::ContentMismatch {
        expected: expected.as_ref().map(|v| v.to_vec()),
        actual: actual.as_ref().map(|v| v.to_vec()),
        mismatch: mismatch.clone(),
        path: path.clone(),
        ..proto::ContentMismatch::default()
      }
    }
    Mismatch::MetadataMismatch { key, expected, actual, mismatch } => {
      proto::ContentMismatch {
        expected: Some(expected.as_bytes().to_vec()),
        actual: Some(actual.as_bytes().to_vec()),
        mismatch: mismatch.clone(),
        path: key.clone(),
        ..proto::ContentMismatch::default()
      }
    }
  }
}

#[cfg(test)]
#[allow(non_snake_case)]
mod tests {
  use expectest::prelude::*;
  use maplit::{btreemap, hashmap};
  use pact_matching::{BodyMatchResult, Mismatch};
  use pact_plugin_driver::plugin_models::PactPluginManifest;
  use pact_plugin_driver::proto;
  use pact_plugin_driver::proto::catalogue_entry::EntryType;
  use pact_plugin_driver::proto::pact_plugin_server::PactPlugin;
  use pact_plugin_driver::proto::start_mock_server_response;
  use serde_json::json;
  use tonic::Request;

  use crate::server::AvroPactPlugin;

  #[tokio::test]
  async fn init_plugin_test() {
    let plugin = AvroPactPlugin { manifest: Default::default() };
    let request = proto::InitPluginRequest {
      implementation: "test".to_string(),
      version: "0".to_string()
    };

    let response = plugin.init_plugin(Request::new(request)).await.unwrap();
    let response_message = response.get_ref();
    expect!(response_message.catalogue.iter()).to(have_count(3));

    let first = &response_message.catalogue.get(0).unwrap();
    expect!(first.key.as_str()).to(be_equal_to("avro"));
    expect!(first.r#type).to(be_equal_to(EntryType::ContentMatcher as i32));
    expect!(first.values.get("content-types")).to(be_some().value(&"application/avro;application/grpc".to_string()));

    let second = &response_message.catalogue.get(1).unwrap();
    expect!(second.key.as_str()).to(be_equal_to("avro"));
    expect!(second.r#type).to(be_equal_to(EntryType::ContentGenerator as i32));
    expect!(second.values.get("content-types")).to(be_some().value(&"application/avro;application/grpc".to_string()));

    let third = &response_message.catalogue.get(2).unwrap();
    expect!(third.key.as_str()).to(be_equal_to("grpc"));
    expect!(third.r#type).to(be_equal_to(EntryType::Transport as i32));
    expect!(third.values.iter()).to(be_empty());
  }

  #[tokio::test]
  async fn configure_interaction_test__with_no_config() {
    let plugin = AvroPactPlugin { manifest: Default::default() };
    let request = proto::ConfigureInteractionRequest {
      content_type: "text/test".to_string(),
      contents_config: Some(prost_types::Struct {
        fields: btreemap!{}
      })
    };

    let response = plugin.configure_interaction(Request::new(request)).await.unwrap();
    let response_message = response.get_ref();
    expect!(&response_message.error).to(
      be_equal_to("Config item with key 'pact:proto' and path to the proto file is required"));
  }

  #[tokio::test]
  async fn configure_interaction_test__with_missing_message_or_service_name() {
    let plugin = AvroPactPlugin { manifest: Default::default() };
    let request = proto::ConfigureInteractionRequest {
      content_type: "text/test".to_string(),
      contents_config: Some(prost_types::Struct {
        fields: btreemap!{
          "pact:proto".to_string() => prost_types::Value { kind: Some(prost_types::value::Kind::StringValue("test.proto".to_string())) }
        }
      })
    };

    let response = plugin.configure_interaction(Request::new(request)).await.unwrap();
    let response_message = response.get_ref();
    expect!(&response_message.error).to(
      be_equal_to("Config item with key 'pact:message-type' and the avro message name or 'pact:proto-service' and the service name is required"));
  }

  #[test]
  fn AvroPactPlugin__host_to_bind_to__default() {
    let plugin = AvroPactPlugin { manifest: Default::default() };
    expect!(plugin.host_to_bind_to()).to(be_none());
  }

  #[test]
  fn AvroPactPlugin__host_to_bind_to__with_string_value() {
    let manifest = PactPluginManifest {
      plugin_config: hashmap! {
        "hostToBindTo".to_string() => json!("127.0.1.1")
      },
      .. PactPluginManifest::default()
    };
    let plugin = AvroPactPlugin { manifest };
    expect!(plugin.host_to_bind_to()).to(be_some().value("127.0.1.1".to_string()));
  }

  #[test]
  fn AvroPactPlugin__host_to_bind_to__with_non_string_value() {
    let manifest = PactPluginManifest {
      plugin_config: hashmap! {
        "hostToBindTo".to_string() => json!("127")
      },
      .. PactPluginManifest::default()
    };
    let plugin = AvroPactPlugin { manifest };
    expect!(plugin.host_to_bind_to()).to(be_some().value("127".to_string()));
  }

  #[test]
  fn AvroPactPlugin__additional_includes__default() {
    let plugin = AvroPactPlugin { manifest: Default::default() };
    expect!(plugin.additional_includes().iter()).to(be_empty());
  }

  #[test]
  fn AvroPactPlugin__additional_includes__with_string_value() {
    let manifest = PactPluginManifest {
      plugin_config: hashmap! {
        "additionalIncludes".to_string() => json!("/some/path")
      },
      .. PactPluginManifest::default()
    };
    let plugin = AvroPactPlugin { manifest };
    expect!(plugin.additional_includes()).to(be_equal_to(vec!["/some/path".to_string()]));
  }

  #[test]
  fn AvroPactPlugin__additional_includes__with_list_value() {
    let manifest = PactPluginManifest {
      plugin_config: hashmap! {
        "additionalIncludes".to_string() => json!(["/path1", "/path2"])
      },
      .. PactPluginManifest::default()
    };
    let plugin = AvroPactPlugin { manifest };
    expect!(plugin.additional_includes()).to(be_equal_to(vec![
      "/path1".to_string(),
      "/path2".to_string()
    ]));
  }

  #[test]
  fn AvroPactPlugin__additional_includes__with_non_string_values() {
    let manifest = PactPluginManifest {
      plugin_config: hashmap! {
        "additionalIncludes".to_string() => json!(["/path1", 200])
      },
      .. PactPluginManifest::default()
    };
    let plugin = AvroPactPlugin { manifest };
    expect!(plugin.additional_includes()).to(be_equal_to(vec![
      "/path1".to_string(),
      "200".to_string()
    ]));
  }
}
