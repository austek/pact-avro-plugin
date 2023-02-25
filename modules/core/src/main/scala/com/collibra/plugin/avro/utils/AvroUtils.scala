package com.collibra.plugin.avro.utils

import org.apache.avro.Schema

import java.io.File
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

}
