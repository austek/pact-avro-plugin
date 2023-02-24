package com.collibra.plugin.avro.utils

import com.google.protobuf.ByteString
import org.apache.avro.Schema
import org.apache.avro.generic._
import org.apache.avro.io.{DatumReader, Decoder, DecoderFactory, EncoderFactory}
import org.apache.avro.specific.SpecificDatumReader

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import scala.util._

object AvroUtils {

  def parseSchema(avroFilePath: String): Either[PluginErrorException, Schema] =
    Try(new Schema.Parser().parse(Path.of(avroFilePath).toFile)) match {
      case Success(schema)    => Right(schema)
      case Failure(exception) => Left(PluginErrorException(exception))
    }

  def schemaToByteString(schema: Schema, record: GenericData.Record): Either[PluginErrorException, ByteString] = {
    val datumWriter = new GenericDatumWriter[GenericRecord](schema)
    Using(new ByteArrayOutputStream()) { os =>
      val encoder = EncoderFactory.get.binaryEncoder(os, null)
      datumWriter.write(record, encoder)
      encoder.flush()
      os.toByteArray
    } match {
      case Success(bytes)     => Right(ByteString.copyFrom(bytes))
      case Failure(exception) => Left(PluginErrorException(exception))
    }
  }

  def recordFromByteString(schema: Schema, bytes: Array[Byte]): GenericRecord = {
    val reader: DatumReader[GenericRecord] = new SpecificDatumReader[GenericRecord](schema)
    val decoder: Decoder = DecoderFactory.get().binaryDecoder(bytes, null)
    reader.read(null, decoder)
  }

  def recordFromJson(schema: Schema, json: String): GenericRecord = {
    val reader: DatumReader[GenericRecord] = new SpecificDatumReader[GenericRecord](schema)
    val jsonDecoder = DecoderFactory.get().jsonDecoder(schema, json)
    reader.read(null, jsonDecoder)
  }
}
