package com.github.austek.plugin.avro.interaction

import com.github.austek.plugin.avro.AvroPluginConstants.*
import com.github.austek.plugin.avro.AvroSchemaBase16Hash
import com.github.austek.plugin.avro.error.{PluginErrorException, PluginErrorMessage, PluginErrorMessages}
import com.google.protobuf.struct.{Struct, Value}
import com.typesafe.scalalogging.StrictLogging
import io.pact.plugin.pact_plugin.*
import org.apache.avro.Schema

object InteractionResponseBuilder extends StrictLogging {

  def build(configuration: Struct, avroSchema: Schema, recordName: String): Either[PluginErrorMessage, ConfigureInteractionResponse] = {
    logger.debug("Start to build response")
    val avroSchemaHash = AvroSchemaBase16Hash(avroSchema)
    InteractionBuilder
      .build(avroSchema, recordName, avroSchemaHash, configuration)
      .map { interactionResponse =>
        ConfigureInteractionResponse(
          interaction = Seq(interactionResponse),
          pluginConfiguration = buildInteractionResponsePluginConfiguration(avroSchema, avroSchemaHash)
        )
      } match {
      case Right(value) => Right(value)
      case Left(errors) =>
        errors.foreach {
          case PluginErrorMessage(value)       => logger.error(value)
          case PluginErrorMessages(values)     => values.foreach(v => logger.error(v))
          case PluginErrorException(exception) => logger.error("Failed to build interaction response", exception)
        }
        Left(PluginErrorMessage("Multiple errors detected and logged, please check logs"))
    }
  }

  private def buildInteractionResponsePluginConfiguration(avroSchema: Schema, avroSchemaHash: AvroSchemaBase16Hash): Option[PluginConfiguration] = {
    Option(
      PluginConfiguration(
        pactConfiguration = Some(
          Struct(
            Map(
              avroSchemaHash.value -> Value(
                Value.Kind.StructValue(
                  Struct(
                    Map(
                      AvroSchema -> Value(Value.Kind.StringValue(avroSchema.toString))
                    )
                  )
                )
              )
            )
          )
        )
      )
    )
  }
}
