//! Shared utilities
use std::collections::BTreeMap;
use anyhow::anyhow;
use bytes::Bytes;
use pact_models::json_utils::json_to_string;
use prost::Message;
use prost_types::FileDescriptorSet;
use tracing::debug;

/// Get the encoded Avro descriptors from the Pact level configuration for the message key
pub(crate) fn get_descriptors_for_interaction(
    message_key: &str,
    plugin_config: &BTreeMap<String, serde_json::Value>
) -> anyhow::Result<FileDescriptorSet> {
    let descriptor_config = plugin_config.get(message_key)
        .ok_or_else(|| anyhow!("Plugin configuration item with key '{}' is required. Received config {:?}", message_key, plugin_config.keys()))?
        .as_object()
        .ok_or_else(|| anyhow!("Plugin configuration item with key '{}' has an invalid format", message_key))?;
    let descriptor_bytes_encoded = descriptor_config.get("protoDescriptors")
        .map(json_to_string)
        .unwrap_or_default();
    if descriptor_bytes_encoded.is_empty() {
        return Err(anyhow!("Plugin configuration item with key '{}' is required, but the descriptors were empty. Received config {:?}", message_key, plugin_config.keys()));
    }

    // The descriptor bytes will be base 64 encoded.
    let descriptor_bytes = match base64::decode(descriptor_bytes_encoded) {
        Ok(bytes) => Bytes::from(bytes),
        Err(err) => {
            return Err(anyhow!("Failed to decode the Avro descriptor - {}", err));
        }
    };
    debug!("Avro file descriptor set is {} bytes", descriptor_bytes.len());

    // Get an MD5 hash of the bytes to check that it matches the descriptor key
    let digest = md5::compute(&descriptor_bytes);
    let descriptor_hash = format!("{:x}", digest);
    if descriptor_hash != message_key {
        return Err(anyhow!("Avro descriptors checksum failed. Expected {} but got {}", message_key, descriptor_hash));
    }

    // Decode the Avro descriptors
    FileDescriptorSet::decode(descriptor_bytes)
        .map_err(|err| anyhow!(err))
}
