package com.collibra.plugin.avro.interaction

import au.com.dius.pact.core.model.PathExpressionsKt._
import au.com.dius.pact.core.model.matchingrules.expressions.MatchingRuleDefinition
import au.com.dius.pact.core.model.matchingrules.{MatchingRule => CoreMatchingRule, MatchingRules => _, _}
import com.collibra.plugin.avro.FieldValue
import com.collibra.plugin.avro.utils.AvroSupportImplicits._
import com.collibra.plugin.avro.utils.AvroUtils._
import com.collibra.plugin.avro.utils.Util._
import com.collibra.plugin.avro.utils._
import com.google.protobuf.struct.{Struct, Value}
import com.typesafe.scalalogging.StrictLogging
import io.pact.plugin.Body.ContentTypeHint
import io.pact.plugin._
import org.apache.avro.Schema
import org.apache.avro.Schema.Type._
import org.apache.avro.generic.GenericData

import scala.jdk.CollectionConverters._

object InteractionBuilder extends StrictLogging {

  def constructAvroMessageForSchema(schema: Schema, recordName: String, configuration: Struct): Either[Seq[PluginError[_]], InteractionResponse] = {
    schema.getType match {
      case UNION =>
        schema.getTypes.asScala.find(s => s.getType == RECORD && s.getName == recordName) match {
          case Some(value) => constructAvroMessage(value, recordName, configuration)
          case None        => Left(Seq(PluginErrorMessage(s"Avro union schema didn't contain record: '$recordName'")))
        }
      case RECORD if schema.getName == recordName =>
        constructAvroMessage(schema, recordName, configuration)
      case RECORD if schema.getName != recordName =>
        Left(Seq(PluginErrorMessage(s"Record '$recordName' was not found in avro Schema provided")))
      case t =>
        Left(Seq(PluginErrorMessage(s"Schema provided is of type: '$t', but expected to be $UNION/$RECORD")))
    }
  }

  private def constructAvroMessage(schema: Schema, recordName: String, configuration: Struct): Either[Seq[PluginError[_]], InteractionResponse] = {
    val matchingRules = new MatchingRuleCategory("body")
    val schemaFields: Seq[Schema.Field] = schema.getFields.asScala.toSeq
    val record = new GenericData.Record(schema)

    configuration.fields
      .filter(!_._1.startsWith("pact:"))
      .map { case (key, value) =>
        logger.debug(s"Keys: $key Value: $value")
        schemaFields.find(_.name() == key) match {
          case Some(field) =>
            val fieldPath = constructValidPath(key, "$")
            buildFieldValue(fieldPath, field, value) map { case (fieldValue, rules) =>
              logger.debug(s"Setting field $field to value '$fieldValue'")
              rules.foreach(rule => matchingRules.addRule(fieldPath, rule))
              record.put(field.name(), fieldValue.value)
              ()
            }
          case None =>
            Left(PluginErrorMessage(s"Record $recordName has no field $key"))
        }
      }
      .partitionMap(identity) match {
      case (errors, _) if errors.nonEmpty => Left(errors.toSeq)
      case _ =>
        schemaToByteString(schema, record)
          .map { bodyContent =>
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
              }
            )
          }
          .left
          .map(e => Seq(e))
    }
  }

  private def buildFieldValue(
    path: String,
    field: Schema.Field,
    value: Value
  ): Either[PluginError[_], (FieldValue[_], Seq[CoreMatchingRule])] = {
    logger.debug(s">>> buildFieldValue($path, $field, $value)")
    fromPactResult(MatchingRuleDefinition.parseMatchingRuleDefinition(value.getStringValue)) match {
      case Right(ok) =>
        ok.getRules.asScala.toSeq
          .map(fromPactEither)
          .map {
            case Left(rule)  => Right(rule)
            case Right(rule) => Left(s"Rule '$rule' not supported for now")
          }
          .partitionMap(identity) match {
          case (errors, _) if errors.nonEmpty => Left(PluginErrorMessages(errors))
          case (_, rules) =>
            FieldValue.from(ok.getValue, field) match {
              case Some(value) => Right(value -> rules)
              case None        => Left(PluginErrorMessage(s"Field value failed to build: $path, $field, $value"))
            }
        }
      case Left(err) =>
        Left(PluginErrorMessage(s"'${value.getStringValue}' is not a valid matching rule definition - $err"))
    }
  }
}
