package com.collibra.plugin.avro.utils

import au.com.dius.pact.core.support.json.JsonValue
import com.google.protobuf.struct.{ListValue, NullValue, Struct, Value}

import scala.jdk.CollectionConverters._
object Util {

  def jsonToValue(json: JsonValue): Value = {
    json match {
      case integer: JsonValue.Integer => Value(Value.Kind.NumberValue(integer.toBigInteger.doubleValue()))
      case decimal: JsonValue.Decimal => Value(Value.Kind.NumberValue(decimal.toBigDecimal.doubleValue()))
      case str: JsonValue.StringValue => Value(Value.Kind.StringValue(str.toString))
      case _: JsonValue.True          => Value(Value.Kind.BoolValue(true))
      case _: JsonValue.False         => Value(Value.Kind.BoolValue(false))
      case _: JsonValue.Null          => Value(Value.Kind.NullValue(NullValue.NULL_VALUE))
      case array: JsonValue.Array     => Value(Value.Kind.ListValue(ListValue(array.getValues.asScala.map(jsonToValue).toSeq)))
      case value: JsonValue.Object    => Value(Value.Kind.StructValue(toProtoStruct(value.getEntries.asScala.toMap)))
      case _                          => Value(Value.Kind.StringValue(json.toString)) // This is here to suppress error 'match may not be exhaustive'
    }
  }

  def toProtoStruct(attributes: Map[String, JsonValue]): Struct =
    Struct(attributes.map { case (key, value) =>
      key -> jsonToValue(value)
    })
}
