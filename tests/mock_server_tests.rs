use std::fs;
use std::panic::catch_unwind;
use std::path::Path;
use anyhow::anyhow;

use expectest::prelude::*;
use pact_consumer::mock_server::StartMockServerAsync;
use pact_consumer::prelude::PactBuilderAsync;
use serde_json::json;
use futures::FutureExt;

async fn mock_server_block() {
  let mut pact_builder = PactBuilderAsync::new_v4("null-and-void", "avro-plugin");
  let _mock_server = pact_builder
    .using_plugin("avro", Some("0".to_string())).await
    .synchronous_message_interaction("doesn't matter, won't be called", |mut i| async move {
      let proto_file = Path::new("tests/simple.proto")
        .canonicalize().unwrap().to_string_lossy().to_string();
      i.contents_from(json!({
        "pact:proto": proto_file,
        "pact:content-type": "application/avro",
        "pact:proto-service": "Test/GetTest",

        "request": {
          "in": "matching(boolean, true)"
        },

        "response": {
          "out": "matching(boolean, true)"
        }
      })).await;
      i
    })
    .await
    .start_mock_server_async(Some("avro/transport/grpc"))
    .await;

  // Should fail as we have not made a request to the mock server when the mock server is dropped
  // at the end of this function
}

#[test_log::test]
fn mock_server_with_no_requests() {
  let current_exe = std::env::current_exe().unwrap();
  let current_dir = current_exe.parent().unwrap();
  let test_bin_dir = current_dir.parent().unwrap();
  let plugin_bin = if cfg!(windows) {
    test_bin_dir.join("pact-avro-plugin.exe")
  } else {
    test_bin_dir.join("pact-avro-plugin")
  };

  if plugin_bin.exists() {
    let plugin_dir = home::home_dir().unwrap().join(".pact/plugins/avro-0");
    fs::create_dir_all(plugin_dir.clone()).unwrap();
    let manifest_file = plugin_dir.join("pact-plugin.json");
    fs::write(manifest_file, json!({
      "manifestVersion": 1,
      "pluginInterfaceVersion": 1,
      "name": "avro",
      "version": "0",
      "executableType": "exec",
      "entryPoint": "pact-avro-plugin"
    }).to_string()).unwrap();
    let plugin_file = plugin_dir.join("pact-avro-plugin");
    fs::copy(plugin_bin, plugin_file).unwrap();

    let result = catch_unwind(|| {
      let runtime = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()
        .expect("new runtime");
      runtime.block_on(mock_server_block())
    });

    fs::remove_dir_all(plugin_dir).unwrap();

    let error = result.unwrap_err();
    let error_message = panic_message::panic_message(&error);
    expect!(error_message).to(be_equal_to("plugin mock server failed verification:\n    1) Test/GetTest: Did not receive any requests for path\n"));
  }
}
