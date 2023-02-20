package com.collibra.plugin.avro.interaction

import au.com.dius.pact.core.model.PathExpressionsKt._
import au.com.dius.pact.core.model.matchingrules
import au.com.dius.pact.core.model.matchingrules.expressions.MatchingRuleDefinition
import au.com.dius.pact.core.model.matchingrules.{MatchingRule => _, MatchingRules => _, _}
import com.collibra.plugin.avro.utils.AvroSupportImplicits._
import com.collibra.plugin.avro.utils.AvroUtils._
import com.collibra.plugin.avro.utils.Util._
import com.collibra.plugin.avro.utils._
import com.collibra.plugin.avro.{AvroField, AvroFieldValue, AvroSchemaBase16Hash}
import com.google.protobuf.struct.Value.Kind
import com.google.protobuf.struct.{Struct, Value}
import com.typesafe.scalalogging.StrictLogging
import io.pact.plugin.Body.ContentTypeHint
import io.pact.plugin._
import org.apache.avro.Schema
import org.apache.avro.Schema.Type._
import org.apache.avro.generic.GenericData

import scala.jdk.CollectionConverters._

object InteractionBuilder extends StrictLogging {

  def build(
    schema: Schema,
    recordName: String,
    avroSchemaHash: AvroSchemaBase16Hash,
    configuration: Struct
  ): Either[Seq[PluginError[_]], InteractionResponse] = {
    schema.getType match {
      case UNION =>
        schema.getTypes.asScala.find(s => s.getType == RECORD && s.getName == recordName) match {
          case Some(value) => buildInteractionResponse(value, recordName, avroSchemaHash, configuration)
          case None        => Left(Seq(PluginErrorMessage(s"Avro union schema didn't contain record: '$recordName'")))
        }
      case RECORD if schema.getName == recordName =>
        buildInteractionResponse(schema, recordName, avroSchemaHash, configuration)
      case RECORD if schema.getName != recordName =>
        Left(Seq(PluginErrorMessage(s"Record '$recordName' was not found in avro Schema provided")))
      case t =>
        Left(Seq(PluginErrorMessage(s"Schema provided is of type: '$t', but expected to be $UNION/$RECORD")))
    }
  }

  private def buildInteractionResponse(
    schema: Schema,
    recordName: String,
    avroSchemaHash: AvroSchemaBase16Hash,
    configuration: Struct
  ): Either[Seq[PluginError[_]], InteractionResponse] = {
    val matchingRules: MatchingRuleCategory = new MatchingRuleCategory("body")
    val schemaFields: Seq[Schema.Field] = schema.getFields.asScala.toSeq
    val record: GenericData.Record = new GenericData.Record(schema)

    configuration.fields
      .filter(!_._1.startsWith("pact:"))
      .map { case (key, value) =>
        logger.debug(s"Configuration Key: $key Value: $value")
        configurationFieldToAvro(recordName, schemaFields, key, value, matchingRules, record)
      }
      .partitionMap(identity) match {
      case (errors, _) if errors.nonEmpty => Left(errors.flatten.toSeq)
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
              },
              pluginConfiguration = Some(
                PluginConfiguration(
                  interactionConfiguration = Some(
                    Struct(
                      Map(
                        "record" -> Value(Value.Kind.StringValue(recordName)),
                        "avroSchemaKey" -> Value(Value.Kind.StringValue(avroSchemaHash.value))
                      )
                    )
                  )
                )
              )
            )
          }
          .left
          .map(e => Seq(e))
    }
  }

  private def configurationFieldToAvro(
    recordName: String,
    schemaFields: Seq[Schema.Field],
    key: String,
    inValue: Value,
    matchingRules: MatchingRuleCategory,
    record: GenericData.Record
  ): Either[Seq[PluginError[_]], Unit] = {
    schemaFields.find(_.name() == key) match {
      case Some(field) =>
        field.schema().getType match {
          case UNION  => Left(Seq(PluginErrorMessage(s"'UNION' is not a support field type - FIELD '$key'")))
          case RECORD => Left(Seq(PluginErrorMessage(s"'RECORD' is not a support field type - FIELD '$key'")))
          case ARRAY  => buildArrayFieldValue(field, key, inValue, matchingRules, record)
          case MAP    => Left(Seq(PluginErrorMessage(s"'MAP' is not a support field type - FIELD '$key'")))
          case _ =>
            buildFieldValue("$", field.name(), field.schema().getType, inValue) map { avroField =>
              logger.debug(s"Setting field $field to value '${avroField.value}'")
              avroField.rules.foreach(rule => matchingRules.addRule(avroField.path, rule))
              record.put(field.name(), avroField.value.value)
              ()
            }
        }
      case None =>
        Left(Seq(PluginErrorMessage(s"Record $recordName has no field $key")))
    }
  }

  private def buildArrayFieldValue(
    field: Schema.Field,
    key: String,
    inValue: Value,
    matchingRules: MatchingRuleCategory,
    record: GenericData.Record
  ): Either[Seq[PluginError[_]], Unit] = {
    inValue.kind match {
      case Kind.Empty =>
        record.put(field.name(), Seq.empty)
        Right(())
      case Kind.NullValue(_) =>
        record.put(field.name(), Seq.empty)
        Right(())
      case Kind.ListValue(listValue) =>
        listValue.values
          .map { singleValue =>
            buildFieldValue("$", field.name(), field.schema().getElementType.getType, singleValue)
          }
          .partitionMap(identity) match {
          case (errors, _) if errors.nonEmpty =>
            Left(errors.flatten)
          case (_, fields) =>
            val fieldPath = constructValidPath(field.name(), "$")
            val values = fields.map { avroField =>
              avroField.rules.foreach(rule => matchingRules.addRule(fieldPath, rule))
              avroField.value.value
            }.asJava
            logger.debug(s"Setting field $field to value '$values'")
            record.put(field.name(), values)
            Right(())
        }
      case _ => Left(Seq(PluginErrorMessage(s"Expected list value for field '$key' but got '${inValue.kind}'")))
    }
  }

  private def buildFieldValue(
    rootPath: String,
    fieldName: String,
    schemaType: Schema.Type,
    inValue: Value
  ): Either[Seq[PluginError[_]], AvroField[_]] = {
    val path = constructValidPath(fieldName, rootPath)
    logger.debug(s">>> buildFieldValue($path, $fieldName, $inValue)")
    inValue.kind match {
      case Kind.Empty          => Left(Seq(PluginErrorMessage(s"Empty kind value for field is not supported")))
      case Kind.NullValue(_)   => Left(Seq(PluginErrorMessage(s"Null kind value for field is not supported")))
      case Kind.NumberValue(_) => Left(Seq(PluginErrorMessage(s"Number kind value for field is not supported")))
      case Kind.StringValue(_) =>
        parseRules(inValue)
          .flatMap { case (fieldValue, rules) =>
            AvroFieldValue.from(schemaType, fieldValue) match {
              case Some(value) => Right(AvroField(path, value, rules))
              case None        => Left(PluginErrorMessage(s"Field value failed to build: $path, $fieldName, $inValue"))
            }
          }
          .left
          .map(e => Seq(e))
      case Kind.BoolValue(_)   => Left(Seq(PluginErrorMessage(s"Bool kind value for field is not supported")))
      case Kind.StructValue(_) => Left(Seq(PluginErrorMessage(s"Struct kind value for field is not supported")))
      case Kind.ListValue(listValue) =>
        (listValue.values.map(parseRules).partitionMap(identity) match {
          case (errors, _) if errors.nonEmpty => Left(errors)
          case (_, result)                    => Right(result)
        }).map { result =>
          result.foldLeft((Seq.empty[String], Seq.empty[matchingrules.MatchingRule])) { (acc, values) =>
            (acc._1 :+ values._1) -> (acc._2 ++ values._2)
          }
        }.map { case (fieldValues, rules) =>
          AvroFieldValue.from(schemaType, fieldValues) match {
            case Some(value) => Right(AvroField(path, value, rules))
            case None        => Left(PluginErrorMessage(s"Field value failed to build: $path, $fieldName, $inValue"))
          }
        } match {
          case Right(Right(value)) => Right(value)
          case Right(Left(error))  => Left(Seq(error))
          case Left(errors)        => Left(errors)
        }
    }

  }

  private def parseRules(inValue: Value): Either[PluginError[_], (String, Seq[matchingrules.MatchingRule])] = {
    fromPactResult(MatchingRuleDefinition.parseMatchingRuleDefinition(inValue.getStringValue)) match {
      case Right(ok) =>
        ok.getRules.asScala.toSeq
          .map(fromPactEither)
          .map {
            case Left(rule)  => Right(rule)
            case Right(rule) => Left(s"Rule '$rule' not supported for now")
          }
          .partitionMap(identity) match {
          case (errors, _) if errors.nonEmpty => Left(PluginErrorMessages(errors))
          case (_, rules)                     => Right((ok.getValue, rules))
        }
      case Left(err) =>
        Left(PluginErrorMessage(s"'${inValue.getStringValue}' is not a valid matching rule definition - $err"))
    }
  }
}
