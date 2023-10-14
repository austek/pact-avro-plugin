package com.github.austek.plugin.avro.utils

import com.github.austek.plugin.avro.error.PluginErrorMessage
import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericDatumReader, GenericRecord}
import org.apache.avro.io.DecoderFactory

import java.io.{ByteArrayInputStream, File, InputStream}
import scala.util._

object AvroUtils extends StrictLogging {

  def parseSchema(file: File): Either[PluginErrorMessage, Schema] =
    Try(new Schema.Parser().parse(file)) match {
      case Success(schema) => Right(schema)
      case Failure(exception) =>
        val msg = s"Failed to parse avro schema from file: ${file.getAbsolutePath}"
        logger.error(msg, exception)
        Left(PluginErrorMessage(msg))
    }

  def parseSchema(schemaString: String): Either[PluginErrorMessage, Schema] =
    Try(new Schema.Parser().parse(schemaString)) match {
      case Success(schema) => Right(schema)
      case Failure(exception) =>
        val msg = "Failed to parse avro schema from string"
        logger.error(msg, exception)
        Left(PluginErrorMessage(msg))
    }

  def deserialize(schema: Schema, stream: InputStream): Either[PluginErrorMessage, GenericRecord] = {
    val datumReader: GenericDatumReader[GenericRecord] = new GenericDatumReader[GenericRecord](schema)
    Using(stream) { in =>
      datumReader.read(null, DecoderFactory.get().binaryDecoder(in, null))
    } match {
      case Success(record) => Right(record)
      case Failure(exception) =>
        val msg = "Failed to deserialize avro schema"
        logger.error(msg, exception)
        Left(PluginErrorMessage(msg))
    }
  }

  def deserialize(schema: Schema, data: Array[Byte]): Either[PluginErrorMessage, GenericRecord] =
    deserialize(schema, new ByteArrayInputStream(data))

}
