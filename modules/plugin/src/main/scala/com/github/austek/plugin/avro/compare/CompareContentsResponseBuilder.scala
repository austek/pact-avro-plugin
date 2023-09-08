package com.github.austek.plugin.avro.compare

import au.com.dius.pact.core.matchers.MatchingContext
import au.com.dius.pact.core.model.matchingrules.{MatchingRule, MatchingRuleCategory, MatchingRuleGroup}
import au.com.dius.pact.core.support.Json
import com.github.austek.plugin.avro.AvroPluginConstants.MatchingRuleCategoryName
import com.github.austek.plugin.avro.ContentTypeConstants._
import com.github.austek.plugin.avro.utils._
import com.google.protobuf.ByteString
import com.google.protobuf.struct.Struct.toJavaProto
import com.typesafe.scalalogging.StrictLogging
import io.pact.plugin.pact_plugin._
import io.pact.plugins.jvm.core.Utils.{INSTANCE => PactCoreUtils}
import org.apache.avro.Schema
import org.apache.avro.Schema.Type.{RECORD, UNION}
import org.apache.avro.generic.GenericRecord

import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

object CompareContentsResponseBuilder extends StrictLogging {

  private val ContentTypeRegex: Regex = raw"(\w+/\*?\+?\w+);\s*record=(\w+)".r

  def build(request: CompareContentsRequest, avroSchema: Schema): Either[PluginError[_], CompareContentsResponse] = {
    for {
      actualBody <- getBody(request.actual, "Actual body required")
      expectedBody <- getBody(request.expected, "Expected body required")
      recordName <- extractRecordName(actualBody, expectedBody)
      schema <- recordSchema(avroSchema, recordName)
      actual <- AvroUtils.deserialize(schema, actualBody.getContent.toByteArray)
      expected <- AvroUtils.deserialize(schema, expectedBody.getContent.toByteArray)
      response <- buildResponse(request, actual, expected)
    } yield response
  }

  private def recordSchema(schema: Schema, recordName: String): Either[PluginErrorMessage, Schema] = {
    schema.getType match {
      case UNION =>
        schema.getTypes.asScala.find(s => s.getType == RECORD && s.getName == recordName) match {
          case Some(value) => Right(value)
          case None        => Left(PluginErrorMessage(s"Avro union schema didn't contain record: '$recordName'"))
        }
      case RECORD if schema.getName == recordName => Right(schema)
      case RECORD if schema.getName != recordName =>
        Left(PluginErrorMessage(s"Record '$recordName' was not found in avro Schema provided"))
      case t =>
        Left(PluginErrorMessage(s"Schema provided is of type: '$t', but expected to be ${UNION.getName}/${RECORD.getName}"))
    }
  }

  private def buildResponse(
    request: CompareContentsRequest,
    actual: GenericRecord,
    expected: GenericRecord
  ): Either[PluginError[_], CompareContentsResponse] = {
    val matchingContext = buildMatchingContext(request)
    AvroContentMatcher.compare(expected, actual, matchingContext).map { bodyMatchResult =>
      CompareContentsResponse(
        results = bodyMatchResult.getBodyResults.asScala.map { item =>
          item.getKey -> ContentMismatches(
            item.getResult.asScala.toSeq.map { result =>
              ContentMismatch(
                expected = Option(ByteString.copyFromUtf8(result.getExpected.toString)),
                actual = Option(ByteString.copyFromUtf8(result.getActual.toString)),
                mismatch = result.getMismatch,
                path = result.getPath,
                diff = result.getDiff
              )
            }
          )
        }.toMap
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

  private def buildMatchingContext(request: CompareContentsRequest) = {
    new MatchingContext(
      new MatchingRuleCategory(
        MatchingRuleCategoryName,
        request.rules.map { case (key, rules) =>
          key -> new MatchingRuleGroup(
            rules.rule.flatMap { matchingRule =>
              matchingRule.values.map { struct =>
                MatchingRule.fromJson(
                  Option(PactCoreUtils.structToJson(toJavaProto(struct)))
                    .filterNot(ruleJson => ruleJson.size() == 0 && matchingRule.`type`.nonEmpty) match {
                    case Some(value) => value
                    case None        => Json.toJson(Map("match" -> matchingRule.`type`).asJava)
                  }
                )
              }
            }.asJava
          )
        }.asJava
      ),
      request.allowUnexpectedKeys
    )
  }

  private def extractRecordName(actual: Body, expected: Body): Either[PluginError[_], String] = {
    def extract(body: Body, name: String): Either[PluginError[_], String] = {
      body.contentType match {
        case ContentTypeRegex(contentType, recordName) =>
          Either.cond(
            ContentTypes.contains(contentType),
            recordName,
            PluginErrorMessage(
              s"$name body is not one of '$ContentTypesStr' content type"
            )
          )
        case _ => Left(PluginErrorMessage(s"$name body content type didn't match expected template of 'content/type; record=NameOfRecord'"))
      }
    }

    extract(actual, "Actual").flatMap { actualRecordName =>
      extract(expected, "Expected").flatMap { expectedRecordName =>
        if (actualRecordName == expectedRecordName) {
          Right(expectedRecordName)
        } else {
          Left(PluginErrorMessage(s"Record names don't match, actual: '$actualRecordName' expected: '$expectedRecordName'"))
        }
      }
    }
  }

  private def getBody(body: Option[Body], msg: String): Either[PluginError[_], Body] = {
    Either.cond(
      body.isDefined,
      body.get,
      PluginErrorMessage(msg)
    )
  }
}
