package com.collibra.plugin.avro.utils

import org.apache.avro.Schema
import org.apache.avro.generic.{GenericDatumReader, GenericRecord}
import org.apache.avro.io.DecoderFactory

import java.io.{ByteArrayInputStream, File, InputStream}
import scala.util._

object AvroUtils {

  def parseSchema(file: File): Either[PluginErrorException, Schema] =
    Try(new Schema.Parser().parse(file)) match {
      case Success(schema)    => Right(schema)
      case Failure(exception) => Left(PluginErrorException(exception))
    }

  def parseSchema(schemaString: String): Either[PluginErrorException, Schema] =
    Try(new Schema.Parser().parse(schemaString)) match {
      case Success(schema)    => Right(schema)
      case Failure(exception) => Left(PluginErrorException(exception))
    }

  def deserialize(schema: Schema, stream: InputStream): Either[PluginErrorException, GenericRecord] = {
    val datumReader: GenericDatumReader[GenericRecord] = new GenericDatumReader[GenericRecord](schema)
    Using(stream) { in =>
      datumReader.read(null, DecoderFactory.get().binaryDecoder(in, null))
    } match {
      case Success(record)    => Right(record)
      case Failure(exception) => Left(PluginErrorException(exception))
    }
  }

  def deserialize(schema: Schema, data: Array[Byte]): Either[PluginErrorException, GenericRecord] =
    deserialize(schema, new ByteArrayInputStream(data))

}
