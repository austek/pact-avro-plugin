//! Module provides the main gRPC server for the plugin process

use std::collections::HashMap;
use std::fs::File;
use std::io::BufReader;
use maplit::hashmap;

use pact_matching::{BodyMatchResult, Mismatch};
use pact_models::json_utils::json_to_string;
use pact_plugin_driver::plugin_models::PactPluginManifest;
use pact_plugin_driver::proto;
use pact_plugin_driver::proto::catalogue_entry::EntryType;
use pact_plugin_driver::proto::pact_plugin_server::PactPlugin;
use serde_json::Value;
use tonic::{Request, Response, Status};
use tracing::{debug, error, info, trace};

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

    // Return an entry for a content matcher and content generator for Protobuf messages
    Ok(Response::new(proto::InitPluginResponse {
      catalogue: vec![
        proto::CatalogueEntry {
          r#type: EntryType::ContentMatcher as i32,
          key: "protobuf".to_string(),
          values: hashmap! {
            "content-types".to_string() => "application/protobuf;application/grpc".to_string()
          }
        },
        proto::CatalogueEntry {
          r#type: EntryType::ContentGenerator as i32,
          key: "protobuf".to_string(),
          values: hashmap! {
            "content-types".to_string() => "application/protobuf;application/grpc".to_string()
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

  async fn update_catalogue(&self, request: Request<proto::Catalogue>) -> Result<Response<()>, Status> {
    debug!("Update catalogue request");

    // currently a no-op
    Ok(Response::new(()))
  }

  async fn compare_contents(&self, request: Request<proto::CompareContentsRequest>) -> Result<Response<proto::CompareContentsResponse>, Status> {
    todo!()
  }

  async fn configure_interaction(&self, request: Request<proto::ConfigureInteractionRequest>) -> Result<Response<proto::ConfigureInteractionResponse>, Status> {
    todo!()
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
          error: format!("Did not find any mock server results for a server with ID {}", request.server_key),
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
          error: format!("Did not find any mock server results for a server with ID {}", request.server_key),
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
