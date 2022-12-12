//! Decoder for encoded Avro messages using the descriptors

use std::fmt::{Display, Formatter};
use std::mem;
use std::str::from_utf8;

use anyhow::anyhow;
use bytes::{Buf, Bytes, BytesMut};
use itertools::Itertools;
use prost::encoding::{decode_key, decode_varint, encode_varint, WireType};
use prost_types::{DescriptorProto, EnumDescriptorProto, FieldDescriptorProto, FileDescriptorSet};
use prost_types::field_descriptor_proto::Type;
use tracing::{debug, error, trace, warn};

use crate::utils::{
  as_hex,
  find_enum_by_name,
  find_enum_by_name_in_message,
  find_message_type_by_name,
  is_repeated_field,
  last_name,
  should_be_packed_type
};

/// Decoded Avro field
#[derive(Clone, Debug, PartialEq)]
pub struct AvroField {
  /// Field number
  pub field_num: u32,
  /// Wire type for the field
  pub wire_type: WireType,
  /// Field data
  pub data: AvroFieldData
}

impl AvroField {
  /// Create a copy of this field with the value replaced with the default
  pub fn default_field_value(&self, descriptor: &FieldDescriptorProto) -> AvroField {
    AvroField {
      field_num: self.field_num,
      wire_type: self.wire_type,
      data: self.data.default_field_value(descriptor)
    }
  }
}

impl Display for AvroField {
  fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
    write!(f, "{}:({:?}, {}) = {}", self.field_num, self.wire_type, self.data.type_name(), self.data)
  }
}

/// Decoded Avro field data
#[derive(Clone, Debug, PartialEq)]
pub enum AvroFieldData {
  /// String value
  String(String),
  /// Boolean value
  Boolean(bool),
  /// Unsigned 32 bit integer
  UInteger32(u32),
  /// Signed 32 bit integer
  Integer32(i32),
  /// Unsigned 64 bit integer
  UInteger64(u64),
  /// Signed 64 bit integer
  Integer64(i64),
  /// 32 bit floating point number
  Float(f32),
  /// 64 bit floating point number
  Double(f64),
  /// Array of bytes
  Bytes(Vec<u8>),
  /// Enum value
  Enum(i32, EnumDescriptorProto),
  /// Embedded message
  Message(Vec<u8>, DescriptorProto),
  /// For field data that does not match the descriptor
  Unknown(Vec<u8>)
}

impl AvroFieldData {
  /// Returns the type name of the field.
  pub fn type_name(&self) -> &'static str {
    match self {
      AvroFieldData::String(_) => "String",
      AvroFieldData::Boolean(_) => "Boolean",
      AvroFieldData::UInteger32(_) => "UInteger32",
      AvroFieldData::Integer32(_) => "Integer32",
      AvroFieldData::UInteger64(_) => "UInteger64",
      AvroFieldData::Integer64(_) => "Integer64",
      AvroFieldData::Float(_) => "Float",
      AvroFieldData::Double(_) => "Double",
      AvroFieldData::Bytes(_) => "Bytes",
      AvroFieldData::Enum(_, _) => "Enum",
      AvroFieldData::Message(_, _) => "Message",
      AvroFieldData::Unknown(_) => "Unknown"
    }
  }

  /// Converts the data for this value into a byte array
  pub fn as_bytes(&self) -> Vec<u8> {
    match self {
      AvroFieldData::String(s) => s.as_bytes().to_vec(),
      AvroFieldData::Boolean(b) => vec![ *b as u8 ],
      AvroFieldData::UInteger32(n) => n.to_le_bytes().to_vec(),
      AvroFieldData::Integer32(n) => n.to_le_bytes().to_vec(),
      AvroFieldData::UInteger64(n) => n.to_le_bytes().to_vec(),
      AvroFieldData::Integer64(n) => n.to_le_bytes().to_vec(),
      AvroFieldData::Float(n) => n.to_le_bytes().to_vec(),
      AvroFieldData::Double(n) => n.to_le_bytes().to_vec(),
      AvroFieldData::Bytes(b) => b.clone(),
      AvroFieldData::Enum(_, _) => self.to_string().as_bytes().to_vec(),
      AvroFieldData::Message(b, _) => b.clone(),
      AvroFieldData::Unknown(data) => data.clone()
    }
  }

  /// Return the default value for this field data
  pub fn default_field_value(&self, descriptor: &FieldDescriptorProto) -> AvroFieldData {
    match &descriptor.default_value {
      Some(s) => {
        // For numeric types, contains the original text representation of the value.
        // For booleans, "true" or "false".
        // For strings, contains the default text contents (not escaped in any way).
        // For bytes, contains the C escaped value.  All bytes >= 128 are escaped.
        match self {
          AvroFieldData::String(_) => AvroFieldData::String(s.clone()),
          AvroFieldData::Boolean(_) => AvroFieldData::Boolean(s == "true"),
          AvroFieldData::UInteger32(_) => AvroFieldData::UInteger32(s.parse().unwrap_or_default()),
          AvroFieldData::Integer32(_) => AvroFieldData::Integer32(s.parse().unwrap_or_default()),
          AvroFieldData::UInteger64(_) => AvroFieldData::UInteger64(s.parse().unwrap_or_default()),
          AvroFieldData::Integer64(_) => AvroFieldData::Integer64(s.parse().unwrap_or_default()),
          AvroFieldData::Float(_) => AvroFieldData::Float(s.parse().unwrap_or_default()),
          AvroFieldData::Double(_) => AvroFieldData::Double(s.parse().unwrap_or_default()),
          AvroFieldData::Bytes(_) => AvroFieldData::Bytes(s.as_bytes().to_vec()),
          AvroFieldData::Enum(_, descriptor) => AvroFieldData::Enum(s.parse().unwrap_or_default(), descriptor.clone()),
          AvroFieldData::Message(_, descriptor) => AvroFieldData::Message(Default::default(), descriptor.clone()),
          AvroFieldData::Unknown(_) => AvroFieldData::Unknown(Default::default())
        }
      }
      None => {
        // For strings, the default value is the empty string.
        // For bytes, the default value is empty bytes.
        // For bools, the default value is false.
        // For numeric types, the default value is zero.
        // For enums, the default value is the first defined enum value, which must be 0.
        // For message fields, the field is not set. Its exact value is language-dependent.
        match self {
          AvroFieldData::String(_) => AvroFieldData::String(Default::default()),
          AvroFieldData::Boolean(_) => AvroFieldData::Boolean(false),
          AvroFieldData::UInteger32(_) => AvroFieldData::UInteger32(0),
          AvroFieldData::Integer32(_) => AvroFieldData::Integer32(0),
          AvroFieldData::UInteger64(_) => AvroFieldData::UInteger64(0),
          AvroFieldData::Integer64(_) => AvroFieldData::Integer64(0),
          AvroFieldData::Float(_) => AvroFieldData::Float(0.0),
          AvroFieldData::Double(_) => AvroFieldData::Double(0.0),
          AvroFieldData::Bytes(_) => AvroFieldData::Bytes(Default::default()),
          AvroFieldData::Enum(_, descriptor) => AvroFieldData::Enum(0, descriptor.clone()),
          AvroFieldData::Message(_, descriptor) => AvroFieldData::Message(Default::default(), descriptor.clone()),
          AvroFieldData::Unknown(_) => AvroFieldData::Unknown(Default::default())
        }
      }
    }
  }
}

impl Display for AvroFieldData {
  fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
    match self {
      AvroFieldData::String(s) => write!(f, "\"{}\"", s),
      AvroFieldData::Boolean(b) => write!(f, "{}", b),
      AvroFieldData::UInteger32(n) => write!(f, "{}", n),
      AvroFieldData::Integer32(n) => write!(f, "{}", n),
      AvroFieldData::UInteger64(n) => write!(f, "{}", n),
      AvroFieldData::Integer64(n) => write!(f, "{}", n),
      AvroFieldData::Float(n) => write!(f, "{}", n),
      AvroFieldData::Double(n) => write!(f, "{}", n),
      AvroFieldData::Bytes(b) => if b.len() <= 16 {
        write!(f, "{}", as_hex(b.as_slice()))
      } else {
        write!(f, "{}... ({} bytes)", as_hex(&b[0..16]), b.len())
      },
      AvroFieldData::Enum(n, descriptor) => {
        let enum_value_name = descriptor.value.iter()
          .find(|v| v.number.is_some() && v.number.as_ref().unwrap() == n)
          .map(|v| v.name.clone().unwrap_or_default()).unwrap_or_else(|| "unknown".to_string());
        write!(f, "{}", enum_value_name)
      },
      AvroFieldData::Message(_, descriptor) => {
        write!(f, "{}", descriptor.name.clone().unwrap_or_else(|| "unknown".to_string()))
      }
      AvroFieldData::Unknown(b) => if b.len() <= 16 {
        write!(f, "{}", as_hex(b.as_slice()))
      } else {
        write!(f, "{}... ({} bytes)", as_hex(&b[0..16]), b.len())
      }
    }
  }
}

/// Decodes the Avro message using the descriptors
#[tracing::instrument(ret, skip_all)]
pub fn decode_message<B>(
  buffer: &mut B,
  descriptor: &DescriptorProto,
  descriptors: &FileDescriptorSet
) -> anyhow::Result<Vec<AvroField>>
  where B: Buf {
  let mut fields = vec![];

  while buffer.has_remaining() {
    let (field_num, wire_type) = decode_key(buffer)?;
    trace!(field_num, ?wire_type, "read field header, bytes remaining = {}", buffer.remaining());

    match find_field_descriptor(field_num as i32, descriptor) {
      Ok(field_descriptor) => {
        let data = match wire_type {
          WireType::Varint => {
            let varint = decode_varint(buffer)?;
            let t: Type = field_descriptor.r#type();
            match t {
              Type::Int64 => vec![ (AvroFieldData::Integer64(varint as i64), wire_type) ],
              Type::Uint64 => vec![ (AvroFieldData::UInteger64(varint), wire_type) ],
              Type::Int32 => vec![ (AvroFieldData::Integer32(varint as i32), wire_type) ],
              Type::Bool => vec![ (AvroFieldData::Boolean(varint > 0), wire_type) ],
              Type::Uint32 => vec![ (AvroFieldData::UInteger32(varint as u32), wire_type) ],
              Type::Enum => {
                let enum_type_name = field_descriptor.type_name.clone().unwrap_or_default();
                let enum_proto = find_enum_by_name_in_message(&descriptor.enum_type, enum_type_name.as_str())
                  .or_else(|| find_enum_by_name(descriptors, enum_type_name.as_str()))
                  .ok_or_else(|| anyhow!("Did not find the enum {} for the field {} in the Avro descriptor", enum_type_name, field_num))?;
                vec![ (AvroFieldData::Enum(varint as i32, enum_proto.clone()), wire_type) ]
              },
              Type::Sint32 => {
                let value = varint as u32;
                vec![ (AvroFieldData::Integer32(((value >> 1) as i32) ^ (-((value & 1) as i32))), wire_type) ]
              },
              Type::Sint64 => vec![ (AvroFieldData::Integer64(((varint >> 1) as i64) ^ (-((varint & 1) as i64))), wire_type) ],
              _ => {
                error!("Was expecting {:?} but received an unknown varint type", t);
                vec![ (AvroFieldData::Unknown(varint.to_le_bytes().to_vec()), wire_type) ]
              }
            }
          }
          WireType::SixtyFourBit => {
            let t: Type = field_descriptor.r#type();
            match t {
              Type::Double => vec![ (AvroFieldData::Double(buffer.get_f64_le()), wire_type) ],
              Type::Fixed64 => vec![ (AvroFieldData::UInteger64(buffer.get_u64_le()), wire_type) ],
              Type::Sfixed64 => vec![ (AvroFieldData::Integer64(buffer.get_i64_le()), wire_type) ],
              _ => {
                error!("Was expecting {:?} but received an unknown 64 bit type", t);
                let value = buffer.get_u64_le();
                vec![ (AvroFieldData::Unknown(value.to_le_bytes().to_vec()), wire_type) ]
              }
            }
          }
          WireType::LengthDelimited => {
            let data_length = decode_varint(buffer)?;
            let mut data_buffer = if buffer.remaining() >= data_length as usize {
              buffer.copy_to_bytes(data_length as usize)
            } else {
              return Err(anyhow!("Insufficient data remaining ({} bytes) to read {} bytes for field {}", buffer.remaining(), data_length, field_num));
            };
            let t: Type = field_descriptor.r#type();
            match t {
              Type::String => vec![ (AvroFieldData::String(from_utf8(&data_buffer)?.to_string()), wire_type) ],
              Type::Message => {
                let type_name = field_descriptor.type_name.as_ref().map(|v| last_name(v.as_str()).to_string());
                let message_proto = descriptor.nested_type.iter()
                  .find(|message_descriptor| message_descriptor.name == type_name)
                  .cloned()
                  .or_else(|| find_message_type_by_name(&type_name.unwrap_or_default(), descriptors).ok())
                  .ok_or_else(|| anyhow!("Did not find the embedded message {:?} for the field {} in the Avro descriptor", field_descriptor.type_name, field_num))?;
                vec![ (AvroFieldData::Message(data_buffer.to_vec(), message_proto), wire_type) ]
              }
              Type::Bytes => vec![ (AvroFieldData::Bytes(data_buffer.to_vec()), wire_type) ],
              _ => if should_be_packed_type(t) && is_repeated_field(&field_descriptor) {
                debug!("Reading length delimited field as a packed repeated field");
                decode_packed_field(field_descriptor, &mut data_buffer)?
              } else {
                error!("Was expecting {:?} but received an unknown length-delimited type", t);
                let mut buf = BytesMut::with_capacity((data_length + 8) as usize);
                encode_varint(data_length, &mut buf);
                buf.extend_from_slice(&*data_buffer);
                vec![ (AvroFieldData::Unknown(buf.freeze().to_vec()), wire_type) ]
              }
            }
          }
          WireType::ThirtyTwoBit => {
            let t: Type = field_descriptor.r#type();
            match t {
              Type::Float => vec![ (AvroFieldData::Float(buffer.get_f32_le()), wire_type) ],
              Type::Fixed32 => vec![ (AvroFieldData::UInteger32(buffer.get_u32_le()), wire_type) ],
              Type::Sfixed32 => vec![ (AvroFieldData::Integer32(buffer.get_i32_le()), wire_type) ],
              _ => {
                error!("Was expecting {:?} but received an unknown fixed 32 bit type", t);
                let value = buffer.get_u32_le();
                vec![ (AvroFieldData::Unknown(value.to_le_bytes().to_vec()), wire_type) ]
              }
            }
          }
          _ => return Err(anyhow!("Messages with {:?} wire type fields are not supported", wire_type))
        };

        trace!(field_num, ?wire_type, ?data, "read field, bytes remaining = {}", buffer.remaining());
        for (data, wire_type) in data {
          fields.push(AvroField {
            field_num,
            wire_type,
            data
          });
        }
      }
      Err(err) => {
        warn!("Was not able to decode field: {}", err);
        let data = match wire_type {
          WireType::Varint => decode_varint(buffer)?.to_le_bytes().to_vec(),
          WireType::SixtyFourBit => buffer.get_u64().to_le_bytes().to_vec(),
          WireType::LengthDelimited => {
            let data_length = decode_varint(buffer)?;
            let mut buf = BytesMut::with_capacity((data_length + 8) as usize);
            encode_varint(data_length, &mut buf);
            buf.extend_from_slice(&*buffer.copy_to_bytes(data_length as usize));
            buf.freeze().to_vec()
          }
          WireType::ThirtyTwoBit => buffer.get_u32().to_le_bytes().to_vec(),
          _ => return Err(anyhow!("Messages with {:?} wire type fields are not supported", wire_type))
        };
        fields.push(AvroField {
          field_num,
          wire_type,
          data: AvroFieldData::Unknown(data)
        });
      }
    }
  }

  Ok(fields.iter().sorted_by(|a, b| Ord::cmp(&a.field_num, &b.field_num)).cloned().collect())
}

fn decode_packed_field(field: FieldDescriptorProto, data: &mut Bytes) -> anyhow::Result<Vec<(AvroFieldData, WireType)>> {
  let mut values = vec![];
  let t: Type = field.r#type();
  match t {
    Type::Double => {
      while data.remaining() >= mem::size_of::<f64>() {
        values.push((AvroFieldData::Double(data.get_f64_le()), WireType::SixtyFourBit));
      }
    }
    Type::Float => {
      while data.remaining() >= mem::size_of::<f32>() {
        values.push((AvroFieldData::Float(data.get_f32_le()), WireType::ThirtyTwoBit));
      }
    }
    Type::Int64 => {
      while data.remaining() > 0 {
        let varint = decode_varint(data)?;
        values.push((AvroFieldData::Integer64(varint as i64), WireType::Varint));
      }
    }
    Type::Uint64 => {
      while data.remaining() > 0 {
        let varint = decode_varint(data)?;
        values.push((AvroFieldData::UInteger64(varint), WireType::Varint));
      }
    }
    Type::Int32 => {
      while data.remaining() > 0 {
        let varint = decode_varint(data)?;
        values.push((AvroFieldData::Integer32(varint as i32), WireType::Varint));
      }
    }
    Type::Fixed64 => {
      while data.remaining() >= mem::size_of::<u64>() {
        values.push((AvroFieldData::UInteger64(data.get_u64_le()), WireType::SixtyFourBit));
      }
    }
    Type::Fixed32 => {
      while data.remaining() >= mem::size_of::<u32>() {
        values.push((AvroFieldData::UInteger32(data.get_u32_le()), WireType::ThirtyTwoBit));
      }
    }
    Type::Uint32 => {
      while data.remaining() > 0 {
        let varint = decode_varint(data)?;
        values.push((AvroFieldData::UInteger32(varint as u32), WireType::Varint));
      }
    }
    Type::Sfixed32 => {
      while data.remaining() >= mem::size_of::<i32>() {
        values.push((AvroFieldData::Integer32(data.get_i32_le()), WireType::ThirtyTwoBit));
      }
    }
    Type::Sfixed64 => {
      while data.remaining() >= mem::size_of::<i64>() {
        values.push((AvroFieldData::Integer64(data.get_i64_le()), WireType::SixtyFourBit));
      }
    }
    Type::Sint32 => {
      while data.remaining() > 0 {
        let varint = decode_varint(data)?;
        let value = varint as u32;
        values.push((AvroFieldData::Integer32(((value >> 1) as i32) ^ (-((value & 1) as i32))), WireType::Varint));
      }
    }
    Type::Sint64 => {
      while data.remaining() > 0 {
        let varint = decode_varint(data)?;
        values.push((AvroFieldData::Integer64(((varint >> 1) as i64) ^ (-((varint & 1) as i64))), WireType::Varint));
      }
    }
    _ => return Err(anyhow!("Field type {:?} can not be packed", t))
  };

  if data.is_empty() {
    Ok(values)
  } else {
    Err(anyhow!("Failed to decode packed repeated field, there was still {} bytes in the buffer", data.remaining()))
  }
}

fn find_field_descriptor(field_num: i32, descriptor: &DescriptorProto) -> anyhow::Result<FieldDescriptorProto> {
  descriptor.field.iter().find(|field| {
    if let Some(num)  = field.number {
      num == field_num
    } else {
      false
    }
  })
    .cloned()
    .ok_or_else(|| anyhow!("Did not find a field with number {} in the descriptor", field_num))
}

#[cfg(test)]
mod tests {
  use bytes::{BufMut, Bytes, BytesMut};
  use expectest::prelude::*;
  use pact_plugin_driver::proto::InitPluginRequest;
  use prost::encoding::WireType;
  use prost::Message;
  use prost_types::{DescriptorProto, EnumDescriptorProto, EnumValueDescriptorProto, FileDescriptorSet};

  use crate::{
    bool_field_descriptor, 
    message_field_descriptor, 
    string_field_descriptor,
    enum_field_descriptor,
    u32_field_descriptor,
    i32_field_descriptor,
    u64_field_descriptor,
    i64_field_descriptor,
    f32_field_descriptor,
    f64_field_descriptor,
    bytes_field_descriptor
  };
  use crate::message_decoder::{decode_message, AvroFieldData};
  use crate::avro::tests::DESCRIPTOR_WITH_ENUM_BYTES;

  const FIELD_1_MESSAGE: [u8; 2] = [8, 1];
  const FIELD_2_MESSAGE: [u8; 2] = [16, 55];
  const FIELD_5_MESSAGE: [u8; 3] = [0b101000, 0b10110011, 0b101011];

  #[test]
  fn decode_boolean() {
    let mut buffer = Bytes::from_static(&FIELD_1_MESSAGE);
    let descriptor = DescriptorProto {
      name: Some("TestMessage".to_string()),
      field: vec![ bool_field_descriptor!("bool_field", 1) ],
      extension: vec![],
      nested_type: vec![],
      enum_type: vec![],
      extension_range: vec![],
      oneof_decl: vec![],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };

    let result = decode_message(&mut buffer, &descriptor, &FileDescriptorSet{ file: vec![] }).unwrap();
    expect!(result.len()).to(be_equal_to(1));

    let field_result = result.first().unwrap();

    expect!(field_result.field_num).to(be_equal_to(1));
    expect!(field_result.wire_type).to(be_equal_to(WireType::Varint));
    expect!(&field_result.data).to(be_equal_to(&AvroFieldData::Boolean(true)));
  }

  #[test]
  fn decode_int32() {
    let mut buffer = Bytes::from_static(&FIELD_2_MESSAGE);
    let descriptor = DescriptorProto {
      name: Some("TestMessage".to_string()),
      field: vec![
        prost_types::FieldDescriptorProto {
          name: Some("field_1".to_string()),
          number: Some(2),
          label: Some(prost_types::field_descriptor_proto::Label::Optional as i32),
          r#type: Some(prost_types::field_descriptor_proto::Type::Int32 as i32),
          type_name: Some("Int32".to_string()),
          extendee: None,
          default_value: None,
          oneof_index: None,
          json_name: None,
          options: None,
          proto3_optional: None
        }
      ],
      extension: vec![],
      nested_type: vec![],
      enum_type: vec![],
      extension_range: vec![],
      oneof_decl: vec![],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };

    let result = decode_message(&mut buffer, &descriptor, &FileDescriptorSet{ file: vec![] }).unwrap();
    expect!(result.len()).to(be_equal_to(1));

    let field_result = result.first().unwrap();

    expect!(field_result.field_num).to(be_equal_to(2));
    expect!(field_result.wire_type).to(be_equal_to(WireType::Varint));
    expect!(&field_result.data).to(be_equal_to(&AvroFieldData::Integer32(55)));
  }

  #[test]
  fn decode_uint64() {
    let mut buffer = Bytes::from_static(&FIELD_5_MESSAGE);
    let descriptor = DescriptorProto {
      name: Some("TestMessage".to_string()),
      field: vec![
        prost_types::FieldDescriptorProto {
          name: Some("field_1".to_string()),
          number: Some(5),
          label: Some(prost_types::field_descriptor_proto::Label::Optional as i32),
          r#type: Some(prost_types::field_descriptor_proto::Type::Uint64 as i32),
          type_name: Some("Uint64".to_string()),
          extendee: None,
          default_value: None,
          oneof_index: None,
          json_name: None,
          options: None,
          proto3_optional: None
        }
      ],
      extension: vec![],
      nested_type: vec![],
      enum_type: vec![],
      extension_range: vec![],
      oneof_decl: vec![],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };

    let result = decode_message(&mut buffer, &descriptor, &FileDescriptorSet{ file: vec![] }).unwrap();
    expect!(result.len()).to(be_equal_to(1));

    let field_result = result.first().unwrap();

    expect!(field_result.field_num).to(be_equal_to(5));
    expect!(field_result.wire_type).to(be_equal_to(WireType::Varint));
    expect!(&field_result.data).to(be_equal_to(&AvroFieldData::UInteger64(5555)));
  }

  #[test]
  fn decode_enum() {
    let mut buffer = Bytes::from_static(&FIELD_2_MESSAGE);
    let enum_descriptor = EnumDescriptorProto {
      name: Some("ContentTypeHint".to_string()),
      value: vec![
        EnumValueDescriptorProto {
          name: Some("DEFAULT".to_string()),
          number: Some(0),
          options: None
        },
        EnumValueDescriptorProto {
          name: Some("TEXT".to_string()),
          number: Some(55),
          options: None
        },
        EnumValueDescriptorProto {
          name: Some("BINARY".to_string()),
          number: Some(66),
          options: None
        }
      ],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };
    let descriptor = DescriptorProto {
      name: Some("TestMessage".to_string()),
      field: vec![
        prost_types::FieldDescriptorProto {
          name: Some("field_1".to_string()),
          number: Some(2),
          label: Some(prost_types::field_descriptor_proto::Label::Optional as i32),
          r#type: Some(prost_types::field_descriptor_proto::Type::Enum as i32),
          type_name: Some("ContentTypeHint".to_string()),
          extendee: None,
          default_value: None,
          oneof_index: None,
          json_name: None,
          options: None,
          proto3_optional: None
        }
      ],
      extension: vec![],
      nested_type: vec![],
      enum_type: vec![ enum_descriptor.clone() ],
      extension_range: vec![],
      oneof_decl: vec![],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };

    let result = decode_message(&mut buffer, &descriptor, &FileDescriptorSet{ file: vec![] }).unwrap();
    expect!(result.len()).to(be_equal_to(1));

    let field_result = result.first().unwrap();

    expect!(field_result.field_num).to(be_equal_to(2));
    expect!(field_result.wire_type).to(be_equal_to(WireType::Varint));
    expect!(&field_result.data).to(be_equal_to(&AvroFieldData::Enum(55, enum_descriptor)));
  }

  #[test]
  fn decode_f32() {
    let f_value: f32 = 12.34;
    let mut buffer = BytesMut::new();
    buffer.put_u8(21);
    buffer.put_f32_le(f_value);

    let descriptor = DescriptorProto {
      name: Some("TestMessage".to_string()),
      field: vec![
        prost_types::FieldDescriptorProto {
          name: Some("field_1".to_string()),
          number: Some(2),
          label: Some(prost_types::field_descriptor_proto::Label::Optional as i32),
          r#type: Some(prost_types::field_descriptor_proto::Type::Float as i32),
          type_name: Some("Float".to_string()),
          extendee: None,
          default_value: None,
          oneof_index: None,
          json_name: None,
          options: None,
          proto3_optional: None
        }
      ],
      extension: vec![],
      nested_type: vec![],
      enum_type: vec![],
      extension_range: vec![],
      oneof_decl: vec![],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };

    let result = decode_message(&mut buffer.freeze(), &descriptor, &FileDescriptorSet{ file: vec![] }).unwrap();
    expect!(result.len()).to(be_equal_to(1));

    let field_result = result.first().unwrap();

    expect!(field_result.field_num).to(be_equal_to(2));
    expect!(field_result.wire_type).to(be_equal_to(WireType::ThirtyTwoBit));
    expect!(&field_result.data).to(be_equal_to(&AvroFieldData::Float(12.34)));
  }

  #[test]
  fn decode_f64() {
    let f_value: f64 = 12.34;
    let mut buffer = BytesMut::new();
    buffer.put_u8(17);
    buffer.put_f64_le(f_value);

    let descriptor = DescriptorProto {
      name: Some("TestMessage".to_string()),
      field: vec![
        prost_types::FieldDescriptorProto {
          name: Some("field_1".to_string()),
          number: Some(2),
          label: Some(prost_types::field_descriptor_proto::Label::Optional as i32),
          r#type: Some(prost_types::field_descriptor_proto::Type::Double as i32),
          type_name: Some("Double".to_string()),
          extendee: None,
          default_value: None,
          oneof_index: None,
          json_name: None,
          options: None,
          proto3_optional: None
        }
      ],
      extension: vec![],
      nested_type: vec![],
      enum_type: vec![],
      extension_range: vec![],
      oneof_decl: vec![],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };

    let result = decode_message(&mut buffer, &descriptor, &FileDescriptorSet{ file: vec![] }).unwrap();
    expect!(result.len()).to(be_equal_to(1));

    let field_result = result.first().unwrap();

    expect!(field_result.field_num).to(be_equal_to(2));
    expect!(field_result.wire_type).to(be_equal_to(WireType::SixtyFourBit));
    expect!(&field_result.data).to(be_equal_to(&AvroFieldData::Double(12.34)));
  }

  #[test]
  fn decode_string() {
    let str_data = "this is a string!";
    let mut buffer = BytesMut::new();
    buffer.put_u8(10);
    buffer.put_u8(str_data.len() as u8);
    buffer.put_slice(str_data.as_bytes());

    let descriptor = DescriptorProto {
      name: Some("TestMessage".to_string()),
      field: vec![
        string_field_descriptor!("type", 1)
      ],
      extension: vec![],
      nested_type: vec![],
      enum_type: vec![],
      extension_range: vec![],
      oneof_decl: vec![],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };

    let result = decode_message(&mut buffer, &descriptor, &FileDescriptorSet{ file: vec![] }).unwrap();
    expect!(result.len()).to(be_equal_to(1));

    let field_result = result.first().unwrap();

    expect!(field_result.field_num).to(be_equal_to(1));
    expect!(field_result.wire_type).to(be_equal_to(WireType::LengthDelimited));
    expect!(&field_result.data).to(be_equal_to(&AvroFieldData::String(str_data.to_string())));
  }

  #[test]
  fn decode_message_test() {
    let message = InitPluginRequest {
      implementation: "test".to_string(),
      version: "1.2.3.4".to_string()
    };

    let field1 = string_field_descriptor!("implementation", 1);
    let field2 = string_field_descriptor!("version", 2);
    let message_descriptor = DescriptorProto {
      name: Some("InitPluginRequest".to_string()),
      field: vec![
        field1.clone(),
        field2.clone()
      ],
      extension: vec![],
      nested_type: vec![],
      enum_type: vec![],
      extension_range: vec![],
      oneof_decl: vec![],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };
    let encoded = message.encode_to_vec();

    let mut buffer = BytesMut::new();
    buffer.put_u8(10);
    buffer.put_u8(encoded.len() as u8);
    buffer.put_slice(&encoded);

    let descriptor = DescriptorProto {
      name: Some("TestMessage".to_string()),
      field: vec![
        message_field_descriptor!("message", 1, "InitPluginRequest")
      ],
      extension: vec![],
      nested_type: vec![
        message_descriptor.clone()
      ],
      enum_type: vec![],
      extension_range: vec![],
      oneof_decl: vec![],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };

    let result = decode_message(&mut buffer, &descriptor, &FileDescriptorSet{ file: vec![] }).unwrap();
    expect!(result.len()).to(be_equal_to(1));

    let field_result = result.first().unwrap();

    expect!(field_result.field_num).to(be_equal_to(1));
    expect!(field_result.wire_type).to(be_equal_to(WireType::LengthDelimited));
    expect!(&field_result.data).to(be_equal_to(&AvroFieldData::Message(encoded, message_descriptor)));
  }

  #[test]
  fn decode_message_with_unknown_field() {
    let message = InitPluginRequest {
      implementation: "test".to_string(),
      version: "1.2.3.4".to_string()
    };

    let field1 = string_field_descriptor!("implementation", 1);
    let message_descriptor = DescriptorProto {
      name: Some("InitPluginRequest".to_string()),
      field: vec![
        field1.clone()
      ],
      extension: vec![],
      nested_type: vec![],
      enum_type: vec![],
      extension_range: vec![],
      oneof_decl: vec![],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };

    let mut buffer = BytesMut::from(message.encode_to_vec().as_slice());
    let result = decode_message(&mut buffer, &message_descriptor, &FileDescriptorSet{ file: vec![] }).unwrap();
    expect!(result.len()).to(be_equal_to(2));

    let field_result = result.first().unwrap();
    expect!(field_result.field_num).to(be_equal_to(1));
    expect!(field_result.wire_type).to(be_equal_to(WireType::LengthDelimited));
    expect!(&field_result.data).to(be_equal_to(&AvroFieldData::String("test".to_string())));

    let field_result = result.get(1).unwrap();
    expect!(field_result.field_num).to(be_equal_to(2));
    expect!(field_result.wire_type).to(be_equal_to(WireType::LengthDelimited));
    expect!(field_result.data.type_name()).to(be_equal_to("Unknown"));
  }

  #[test]
  fn default_field_value_test_boolean() {
    let descriptor = bool_field_descriptor!("bool_field", 1);
    expect!(AvroFieldData::Boolean(true).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Boolean(false)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("true".to_string()),
      .. bool_field_descriptor!("bool_field", 1)
    };
    expect!(AvroFieldData::Boolean(true).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Boolean(true)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("false".to_string()),
      .. bool_field_descriptor!("bool_field", 1)
    };
    expect!(AvroFieldData::Boolean(true).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Boolean(false)));
  }

  #[test]
  fn default_field_value_test_string() {
    let descriptor = string_field_descriptor!("field", 1);
    expect!(AvroFieldData::String("true".to_string()).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::String("".to_string())));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("true".to_string()),
      .. string_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::String("other".to_string()).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::String("true".to_string())));
  }

  #[test]
  fn default_field_value_test_u32() {
    let descriptor = u32_field_descriptor!("field", 1);
    expect!(AvroFieldData::UInteger32(123).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::UInteger32(0)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("100".to_string()),
      .. u32_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::UInteger32(123).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::UInteger32(100)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("sdsd".to_string()),
      .. u32_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::UInteger32(123).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::UInteger32(0)));
  }

  #[test]
  fn default_field_value_test_i32() {
    let descriptor = i32_field_descriptor!("field", 1);
    expect!(AvroFieldData::Integer32(123).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Integer32(0)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("100".to_string()),
      .. i32_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::Integer32(123).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Integer32(100)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("sdsd".to_string()),
      .. i32_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::Integer32(123).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Integer32(0)));
  }

  #[test]
  fn default_field_value_test_u64() {
    let descriptor = u64_field_descriptor!("field", 1);
    expect!(AvroFieldData::UInteger64(123).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::UInteger64(0)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("100".to_string()),
      .. u64_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::UInteger64(123).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::UInteger64(100)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("sdsd".to_string()),
      .. u64_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::UInteger64(123).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::UInteger64(0)));
  }

  #[test]
  fn default_field_value_test_i64() {
    let descriptor = i64_field_descriptor!("field", 1);
    expect!(AvroFieldData::Integer64(123).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Integer64(0)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("100".to_string()),
      .. i64_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::Integer64(123).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Integer64(100)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("sdsd".to_string()),
      .. i64_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::Integer64(123).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Integer64(0)));
  }

  #[test]
  fn default_field_value_test_f32() {
    let descriptor = f32_field_descriptor!("field", 1);
    expect!(AvroFieldData::Float(123.0).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Float(0.0)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("100".to_string()),
      .. f32_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::Float(123.0).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Float(100.0)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("sdsd".to_string()),
      .. f32_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::Float(123.0).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Float(0.0)));
  }

  #[test]
  fn default_field_value_test_f64() {
    let descriptor = f64_field_descriptor!("field", 1);
    expect!(AvroFieldData::Double(123.0).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Double(0.0)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("100".to_string()),
      .. f64_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::Double(123.0).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Double(100.0)));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("sdsd".to_string()),
      .. f64_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::Double(123.0).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Double(0.0)));
  }

  #[test]
  fn default_field_value_test_enum() {
    let enum_descriptor = prost_types::EnumDescriptorProto {
      name: Some("EnumValue".to_string()),
      value: vec![
        prost_types::EnumValueDescriptorProto {
          name: Some("OPT1".to_string()),
          number: Some(0),
          options: None
        },
        prost_types::EnumValueDescriptorProto {
          name: Some("OPT2".to_string()),
          number: Some(1),
          options: None
        },
        prost_types::EnumValueDescriptorProto {
          name: Some("OPT3".to_string()),
          number: Some(2),
          options: None
        }
      ],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };
    let descriptor = enum_field_descriptor!("field", 1, "OPT1");
    expect!(AvroFieldData::Enum(2, enum_descriptor.clone()).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Enum(0, enum_descriptor.clone())));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("1".to_string()),
      .. enum_field_descriptor!("field", 1, "OPT2")
    };
    expect!(AvroFieldData::Enum(2, enum_descriptor.clone()).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Enum(1, enum_descriptor.clone())));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("sdsd".to_string()),
      .. enum_field_descriptor!("field", 1, "OPT2")
    };
    expect!(AvroFieldData::Enum(2, enum_descriptor.clone()).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Enum(0, enum_descriptor.clone())));
  }

  #[test]
  fn default_field_value_test_bytes() {
    let descriptor = bytes_field_descriptor!("field", 1);
    expect!(AvroFieldData::Bytes(vec![1, 2, 3, 4]).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Bytes(vec![])));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("true".to_string()),
      .. bytes_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::Bytes(vec![1, 2, 3, 4]).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Bytes(vec![116, 114, 117, 101])));
  }

  #[test]
  fn default_field_value_test_message() {
    let field1 = string_field_descriptor!("implementation", 1);
    let field2 = string_field_descriptor!("version", 2);
    let message_descriptor = DescriptorProto {
      name: Some("InitPluginRequest".to_string()),
      field: vec![
        field1.clone(),
        field2.clone()
      ],
      extension: vec![],
      nested_type: vec![],
      enum_type: vec![],
      extension_range: vec![],
      oneof_decl: vec![],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };
    let descriptor = message_field_descriptor!("field", 1, "InitPluginRequest");
    expect!(AvroFieldData::Message(vec![1, 2, 3, 4], message_descriptor.clone()).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Message(vec![], message_descriptor.clone())));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("true".to_string()),
      .. message_field_descriptor!("field", 1, "InitPluginRequest")
    };
    expect!(AvroFieldData::Message(vec![1, 2, 3, 4], message_descriptor.clone()).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Message(vec![], message_descriptor.clone())));
  }

  #[test]
  fn default_field_value_test_unknown() {
    let descriptor = bytes_field_descriptor!("field", 1);
    expect!(AvroFieldData::Unknown(vec![1, 2, 3, 4]).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Unknown(vec![])));

    let descriptor = prost_types::FieldDescriptorProto {
      default_value: Some("true".to_string()),
      .. bytes_field_descriptor!("field", 1)
    };
    expect!(AvroFieldData::Unknown(vec![1, 2, 3, 4]).default_field_value(&descriptor)).to(be_equal_to(AvroFieldData::Unknown(vec![])));
  }

  #[test]
  fn decode_packed_field() {
    let f_value: f32 = 12.0;
    let f_value2: f32 = 9.0;
    let mut buffer = BytesMut::new();
    buffer.put_u8(10);
    buffer.put_u8(8);
    buffer.put_f32_le(f_value);
    buffer.put_f32_le(f_value2);

    let descriptor = DescriptorProto {
      name: Some("PackedFieldMessage".to_string()),
      field: vec![
        prost_types::FieldDescriptorProto {
          name: Some("field_1".to_string()),
          number: Some(1),
          label: Some(prost_types::field_descriptor_proto::Label::Repeated as i32),
          r#type: Some(prost_types::field_descriptor_proto::Type::Float as i32),
          type_name: Some("Float".to_string()),
          extendee: None,
          default_value: None,
          oneof_index: None,
          json_name: None,
          options: None,
          proto3_optional: None
        }
      ],
      extension: vec![],
      nested_type: vec![],
      enum_type: vec![],
      extension_range: vec![],
      oneof_decl: vec![],
      options: None,
      reserved_range: vec![],
      reserved_name: vec![]
    };

    let result = decode_message(&mut buffer, &descriptor, &FileDescriptorSet{ file: vec![] }).unwrap();
    expect!(result.len()).to(be_equal_to(2));

    let field_result = result.first().unwrap();

    expect!(field_result.field_num).to(be_equal_to(1));
    expect!(field_result.wire_type).to(be_equal_to(WireType::ThirtyTwoBit));
    expect!(&field_result.data).to(be_equal_to(&AvroFieldData::Float(12.0)));
  }

  #[test_log::test]
  fn decode_message_with_global_enum_field() {
    let bytes: &[u8] = &DESCRIPTOR_WITH_ENUM_BYTES;
    let buffer = Bytes::from(bytes);
    let fds: FileDescriptorSet = FileDescriptorSet::decode(buffer).unwrap();
    let main_descriptor = fds.file.iter()
      .find(|fd| fd.name.clone().unwrap_or_default() == "area_calculator.proto")
      .unwrap();
    let message_descriptor = main_descriptor.message_type.iter()
      .find(|md| md.name.clone().unwrap_or_default() == "Rectangle").unwrap();
    let enum_proto = main_descriptor.enum_type.first().unwrap();

    let message_bytes: &[u8] = &[13, 0, 0, 64, 64, 21, 0, 0, 128, 64, 40, 1];
    let mut buffer = Bytes::from(message_bytes);
    let result = decode_message(&mut buffer, &message_descriptor, &fds).unwrap();
    expect!(result.len()).to(be_equal_to(3));

    let field_result = result.last().unwrap();

    expect!(field_result.field_num).to(be_equal_to(5));
    expect!(field_result.wire_type).to(be_equal_to(WireType::Varint));
    expect!(&field_result.data).to(be_equal_to(&AvroFieldData::Enum(1, enum_proto.clone())));
  }
}
