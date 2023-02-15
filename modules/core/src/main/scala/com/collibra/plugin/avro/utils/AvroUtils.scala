package com.collibra.plugin.avro.utils

import com.google.protobuf.ByteString
import org.apache.avro.Schema
import org.apache.avro.generic._
import org.apache.avro.io.EncoderFactory

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import scala.util._

object AvroUtils {

  def parseSchema(avroFilePath: String): Either[PluginErrorMessage, Schema] = {
    Try(new Schema.Parser().parse(Path.of(avroFilePath).toFile)) match {
      case Success(schema) => Right(schema)
      case Failure(exception) =>
        exception.printStackTrace()
        Left(PluginErrorMessage(exception.getMessage))
    }
  }

  def schemaToByteString(schema: Schema, record: GenericData.Record): Option[ByteString] = {
    val datumWriter = new GenericDatumWriter[GenericRecord](schema)
    Using(new ByteArrayOutputStream()) { os =>
      val encoder = EncoderFactory.get.binaryEncoder(os, null)
      datumWriter.write(record, encoder)
      encoder.flush()
      os.toByteArray
    } match {
      case Success(bytes) => Some(ByteString.copyFrom(bytes))
      case Failure(exception) =>
        exception.printStackTrace()
        None
    }
  }
}
