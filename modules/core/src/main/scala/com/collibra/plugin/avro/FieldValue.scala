package com.collibra.plugin.avro

import org.apache.avro.Schema
import org.apache.avro.Schema.Type._

import scala.util.Try

case class FieldValue[T](value: T)

object FieldValue {
  def from(fieldValue: String, field: Schema.Field): Option[FieldValue[_ >: String with Int with Long with Float with Double with Boolean]] = {
    field.schema().getType match {
      case RECORD  => None
      case ENUM    => None
      case ARRAY   => None
      case MAP     => None
      case UNION   => None
      case FIXED   => None
      case STRING  => Some(FieldValue(fieldValue))
      case BYTES   => None
      case INT     => Try(fieldValue.toInt).toOption.map(FieldValue.apply)
      case LONG    => Try(fieldValue.toLong).toOption.map(FieldValue.apply)
      case FLOAT   => Try(fieldValue.toFloat).toOption.map(FieldValue.apply)
      case DOUBLE  => Try(fieldValue.toDouble).toOption.map(FieldValue.apply)
      case BOOLEAN => Some(fieldValue.toLowerCase == "true").map(FieldValue.apply)
      case NULL    => None
    }
  }
}
