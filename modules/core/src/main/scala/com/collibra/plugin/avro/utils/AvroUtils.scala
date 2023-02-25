package com.collibra.plugin.avro.utils

import org.apache.avro.Schema

import java.nio.file.Path
import scala.util._

object AvroUtils {

  def parseSchema(avroFilePath: String): Either[PluginErrorException, Schema] =
    Try(new Schema.Parser().parse(Path.of(avroFilePath).toFile)) match {
      case Success(schema)    => Right(schema)
      case Failure(exception) => Left(PluginErrorException(exception))
    }


}
