package com.collibra.plugin.avro

import akka.Done
import au.com.dius.pact.core.model.PactSpecVersion
import au.com.dius.pact.core.model.PathExpressionsKt._
import au.com.dius.pact.core.model.generators.{Generator => ModelGenerator}
import au.com.dius.pact.core.model.matchingrules.MatchingRuleCategory
import au.com.dius.pact.core.model.matchingrules.expressions.MatchingRuleDefinition
import au.com.dius.pact.core.support.Json
import com.collibra.plugin.avro.utils.AvroSupportImplicits._
import com.collibra.plugin.avro.utils.Util
import com.google.protobuf.ByteString
import com.google.protobuf.struct.{Struct, Value}
import com.typesafe.scalalogging.StrictLogging
import io.pact.plugin.Body.ContentTypeHint
import io.pact.plugin._
import org.apache.avro.Schema
import org.apache.avro.Schema.Type._
import org.apache.avro.generic.{GenericData, GenericDatumWriter, GenericRecord}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.avro.specific.SpecificDatumReader

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.ServerSocket
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import scala.util.{Failure, Success, Using}

object InteractionResponseBuilder extends StrictLogging {

  def constructAvroMessageForSchema(schema: Schema, recordName: String, configuration: Struct): Either[String, InteractionResponse] = {
    schema.getType match {
      case UNION =>
        schema.getTypes.asScala.find(s => s.getType == RECORD && s.getName == recordName) match {
          case Some(value) => constructAvroMessage(value, recordName, configuration)
          case None =>
            val msg = s"Avro union schema didn't contain record: '$recordName'"
            logger.error(msg)
            Left(msg)
        }
      case RECORD if schema.getName == recordName => constructAvroMessage(schema, recordName, configuration)
      case RECORD if schema.getName != recordName =>
        val msg = s"Record '$recordName' was not found in avro Schema provided"
        logger.error(msg)
        Left(msg)
      case t =>
        val msg = s"Schema provided is of type: '$t', but expected to be $UNION/$RECORD"
        logger.error(msg)
        Left(msg)
    }
  }

  private def constructAvroMessage(schema: Schema, recordName: String, configuration: Struct): Either[String, InteractionResponse] = {
    val matchingRules = new MatchingRuleCategory("body")
    val generators = mutable.Map.empty[String, ModelGenerator]
    val schemaFields: mutable.Seq[Schema.Field] = schema.getFields.asScala
    val record = new GenericData.Record(schema)

    configuration.fields
      .filter(!_._1.startsWith("pact:"))
      .map { case (key, value) =>
        logger.debug(s"Keys: $key Value: $value")
        schemaFields.find(_.name() == key) match {
          case Some(field) =>
            buildFieldValue(constructValidPath(key, "$"), field, value, matchingRules, generators).foreach { fieldValue =>
              logger.debug(s"Setting field $field to value '$fieldValue'")
              record.put(field.name(), fieldValue.value)
            }
            Right(Done)
          case None =>
            val msg = s"Record $recordName has no field $key"
            logger.error(msg)
            Left(msg)
        }
      }
      .partitionMap(identity) match {
      case (errors, _) if errors.nonEmpty => Left(errors.mkString(", "))
      case _ =>
        Right(
          InteractionResponse(
            contents = Some(
              Body(
                contentType = s"avro/binary;record=$recordName",
                content = avroRecordToInputStream(schema, record),
                contentTypeHint = ContentTypeHint.BINARY
              )
            ),
            rules = matchingRules.getMatchingRules.asScala.map { case (key, rules) =>
              key -> MatchingRules(rules.getRules.asScala.map { r =>
                MatchingRule(r.getName, Option(Util.toProtoStruct(r.getAttributes.asScala.toMap)))
              }.toSeq)
            }.toMap,
            generators = generators.map { case (key, generator) =>
              key -> Generator(
                generator.getType,
                Some(Util.toProtoStruct(Json.toJson(generator.toMap(PactSpecVersion.V4)).asObject().getEntries.asScala.toMap))
              )
            }.toMap
          )
        )
    }
  }

  private def avroRecordToInputStream(schema: Schema, record: GenericData.Record): Option[ByteString] = {
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

  private def buildFieldValue(
    path: String,
    field: Schema.Field,
    value: Value,
    matchingRules: MatchingRuleCategory,
    generators: mutable.Map[String, ModelGenerator]
  ): Option[FieldValue[_]] = {
    logger.debug(s">>> buildFieldValue($path, $field, $value)")

    val result: Either[String, MatchingRuleDefinition] = MatchingRuleDefinition.parseMatchingRuleDefinition(value.getStringValue)
    result match {
      case Right(ok) =>
        val fieldPath = constructValidPath(field.name, path)

        ok.getRules.asScala.map(fromPactEither).map {
          case Left(rule) => matchingRules.addRule(fieldPath, rule)
          case Right(_)   => throw new RuntimeException("Not supported for now")
        }

        val generator = ok.getGenerator
        if (generator != null) {
          generators + (fieldPath -> generator)
        }
        FieldValue.from(ok.getValue, field)

      case Left(err) =>
        val message = s"'${value.getStringValue}' is not a valid matching rule definition - $err"
        logger.error(message)
        throw new RuntimeException(message)
    }
  }
  /*
  private def valueForType(fieldValue: String, field: Schema.Field): Option[Any] = {
    logger.debug(s">>> valueForType($fieldValue, $field)")
    logger.debug(s"Creating value for type ${field.schema().getType} from '$fieldValue'")

    field.schema().getType match {
      case RECORD =>
        logger.warn("Type RECORD not supported for now")
        None
      case ENUM =>
        logger.warn("Type ENUM not supported for now")
        None
      case ARRAY =>
        logger.warn("Type ARRAY not supported for now")
        None
      case MAP =>
        logger.warn("Type MAP not supported for now")
        None
      case UNION =>
        logger.warn("Type UNION not supported for now")
        None
      case FIXED =>
        logger.warn("Type FIXED not supported for now")
        None
      case STRING => Some(fieldValue)
      case BYTES =>
        logger.warn("Type BYTES not supported for now")
        None
      case INT     => Try(parseInt(fieldValue)).toOption
      case LONG    => Try(parseLong(fieldValue)).toOption
      case FLOAT   => Try(parseFloat(fieldValue)).toOption
      case DOUBLE  => Try(parseDouble(fieldValue)).toOption
      case BOOLEAN => Some(fieldValue == "true")
      case NULL    => None

    }
  }
   */
}
