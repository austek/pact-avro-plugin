package com.github.austek.plugin.avro.interaction

import au.com.dius.pact.core.model.matchingrules.{MatchingRule => _, MatchingRules => _, _}
import com.github.austek.plugin.avro.Avro.AvroRecord
import com.github.austek.plugin.avro.AvroPluginConstants.*
import com.github.austek.plugin.avro.AvroSchemaBase16Hash
import com.github.austek.plugin.avro.error.{PluginError, PluginErrorMessage}
import com.github.austek.plugin.avro.utils.Util.*
import com.google.protobuf.ByteString
import com.google.protobuf.struct.{Struct, Value}
import com.typesafe.scalalogging.StrictLogging
import io.pact.plugin.pact_plugin.Body.ContentTypeHint
import io.pact.plugin.pact_plugin.*
import org.apache.avro.Schema
import org.apache.avro.Schema.Type.*

import scala.jdk.CollectionConverters.*

object InteractionBuilder extends StrictLogging {

  def build(
    schema: Schema,
    recordName: String,
    avroSchemaHash: AvroSchemaBase16Hash,
    configuration: Struct
  ): Either[Seq[PluginError[?]], InteractionResponse] = {
    schema.getType match {
      case UNION                                  => buildUnionResponse(schema, recordName, avroSchemaHash, configuration)
      case RECORD if schema.getName == recordName => buildRecordResponse(schema, recordName, avroSchemaHash, configuration)
      case RECORD if schema.getName != recordName => Left(Seq(PluginErrorMessage(s"Record '$recordName' was not found in avro Schema provided")))
      case t => Left(Seq(PluginErrorMessage(s"Schema provided is of type: '$t', but expected to be ${UNION.getName}/${RECORD.getName}")))
    }
  }

  private def buildUnionResponse(schema: Schema, recordName: String, avroSchemaHash: AvroSchemaBase16Hash, configuration: Struct) =
    schema.getTypes.asScala.find(s => s.getType == RECORD && s.getName == recordName) match {
      case Some(value) => buildRecordResponse(value, recordName, avroSchemaHash, configuration)
      case None        => Left(Seq(PluginErrorMessage(s"Avro union schema didn't contain record: '$recordName'")))
    }

  private def buildRecordResponse(
    schema: Schema,
    recordName: String,
    avroSchemaHash: AvroSchemaBase16Hash,
    configuration: Struct
  ): Either[Seq[PluginError[?]], InteractionResponse] = {
    AvroRecord(schema, configuration.fields).flatMap { avroRecord =>
      avroRecord
        .toByteString(schema)
        .map { bodyContent =>
          buildInteractionResponse(recordName, avroSchemaHash, avroRecord.matchingRules, bodyContent)
        }
        .left
        .map(e => Seq(e))
    }
  }

  private def buildInteractionResponse(
    recordName: String,
    avroSchemaHash: AvroSchemaBase16Hash,
    matchingRules: MatchingRuleCategory,
    bodyContent: ByteString
  ) = {
    InteractionResponse(
      contents = Some(
        Body(
          contentType = s"avro/binary;record=$recordName",
          content = Some(bodyContent),
          contentTypeHint = ContentTypeHint.BINARY
        )
      ),
      rules = matchingRules.getMatchingRules.asScala.toMap.map { case (key, rules) =>
        key -> MatchingRules(
          rules.getRules.asScala.toSeq.map { r =>
            MatchingRule(r.getName, Option(toProtoStruct(r.getAttributes.asScala.toMap)))
          }
        )
      },
      pluginConfiguration = Some(
        PluginConfiguration(
          interactionConfiguration = Some(
            Struct(
              Map(
                Record -> Value(Value.Kind.StringValue(recordName)),
                SchemaKey -> Value(Value.Kind.StringValue(avroSchemaHash.value))
              )
            )
          )
        )
      )
    )
  }
}
