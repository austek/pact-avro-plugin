package com.collibra.plugin.avro

import org.apache.avro.Schema
import org.apache.avro.Schema.Type._

import scala.util.Try

case class AvroFieldValue[+T](value: T)

object AvroFieldValue {
  def from(schemaType: Schema.Type, fieldValue: String): Option[AvroFieldValue[_ >: String with Int with Long with Float with Double with Boolean]] =
    getAvroValue(schemaType, fieldValue).map(AvroFieldValue.apply)
  def from(
    schemaType: Schema.Type,
    fieldValues: Seq[String]
  ): Option[AvroFieldValue[Seq[_ >: String with Int with Long with Float with Double with Boolean]]] = {
    schemaType match {
      case ARRAY =>
        seqToOpt(fieldValues.map(fieldValue => getAvroValue(schemaType, fieldValue)))
          .map(AvroFieldValue.apply)
      case _ => None
    }
  }

  private def getAvroValue(schemaType: Schema.Type, fieldValue: String): Option[_ >: String with Int with Long with Float with Double with Boolean] = {
    schemaType match {
      case STRING  => Some(fieldValue)
      case INT     => Try(fieldValue.toInt).toOption
      case LONG    => Try(fieldValue.toLong).toOption
      case FLOAT   => Try(fieldValue.toFloat).toOption
      case DOUBLE  => Try(fieldValue.toDouble).toOption
      case BOOLEAN => Some(fieldValue.toLowerCase == "true")
      case _       => None
    }
  }

  private def seqToOpt[A](seq: Seq[Option[A]]): Option[Seq[A]] = {
    val flatten = seq.flatten
    if (flatten.isEmpty) None
    else Some(flatten)
  }
}
