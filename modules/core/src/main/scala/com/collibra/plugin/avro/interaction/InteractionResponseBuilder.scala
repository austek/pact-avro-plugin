package com.collibra.plugin.avro.interaction

import com.collibra.plugin.avro.utils.{PluginErrorMessage, PluginErrorMessages}
import com.google.common.io.BaseEncoding
import com.google.protobuf.struct.{Struct, Value}
import com.typesafe.scalalogging.StrictLogging
import io.pact.plugin._
import org.apache.avro.Schema

import java.security.MessageDigest

object InteractionResponseBuilder extends StrictLogging {
  def buildInteractionResponse(configuration: Struct, avroSchema: Schema, recordName: String): Either[PluginErrorMessage, ConfigureInteractionResponse] = {
    logger.debug("Digest avro file")
    val digest: MessageDigest = MessageDigest.getInstance("MD5")
    digest.update(avroSchema.toString.getBytes)
    val avroSchemaHash: String = BaseEncoding.base16().lowerCase().encode(digest.digest())

    logger.debug("Start to build response")
    logger.debug("return response")
    InteractionBuilder
      .constructAvroMessageForSchema(avroSchema, recordName, configuration)
      .map { interactionResponse =>
        ConfigureInteractionResponse(
          interaction = Seq(
            interactionResponse
          ),
          pluginConfiguration = Some(
            PluginConfiguration(
              pactConfiguration = Some(
                Struct(
                  Map(
                    avroSchemaHash -> Value(
                      Value.Kind.StructValue(
                        Struct(
                          Map(
                            "avroSchema" -> Value(Value.Kind.StringValue(avroSchema.toString))
                          )
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      } match {
      case Right(value) => Right(value)
      case Left(errors)      =>
        errors.foreach {
          case PluginErrorMessage(value) => logger.error(value)
          case PluginErrorMessages(values) => values.foreach(v => logger.error(v))
        }
        Left(PluginErrorMessage("Multiple errors detected and logged, please check logs"))
    }
  }
}
