package com.github.austek.plugin.avro.compare

import au.com.dius.pact.core.matchers.MatchingContext
import au.com.dius.pact.core.model.matchingrules.{MatchingRule, MatchingRuleCategory, MatchingRuleGroup}
import com.github.austek.plugin.avro.AvroPluginConstants.MatchingRuleCategoryName
import com.github.austek.plugin.avro.ContentTypeConstants._
import com.github.austek.plugin.avro.utils._
import com.google.protobuf.ByteString
import com.google.protobuf.struct.Struct.toJavaProto
import com.typesafe.scalalogging.StrictLogging
import io.pact.plugin._
import io.pact.plugins.jvm.core.Utils.{INSTANCE => PactCoreUtils}
import org.apache.avro.Schema
import org.apache.avro.Schema.Type.{RECORD, UNION}
import org.apache.avro.generic.GenericRecord

import scala.jdk.CollectionConverters._

object CompareContentsResponseBuilder extends StrictLogging {

  def build(request: CompareContentsRequest, avroSchema: Schema): Either[PluginError[_], CompareContentsResponse] = {
    for {
      actualBody <- getBody(request.actual, "Actual body required")
      expectedBody <- getBody(request.expected, "Expected body required")
      _ <- contentTypesMatch(actualBody, expectedBody)
      _ <- correctContentType(actualBody, "Actual")
      _ <- correctContentType(expectedBody, "Expected")
      schema <- recordSchema(avroSchema, "Order")
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
                MatchingRule.fromJson(PactCoreUtils.structToJson(toJavaProto(struct)))
              }
            }.asJava
          )
        }.asJava
      ),
      request.allowUnexpectedKeys
    )
  }

  private def contentTypesMatch(actual: Body, expected: Body): Either[PluginError[_], Unit] = {
    Either.cond(
      actual.contentType == expected.contentType,
      (),
      PluginErrorMessage("Content types don't match")
    )
  }

  private def correctContentType(body: Body, name: String): Either[PluginError[_], Unit] = {
    Either.cond(
//      ContentTypes.contains(body.contentType), //TODO Uncomment after bug is fixed
      body.getContent != null,
      (),
      PluginErrorMessage(
        s"$name body is not one of '$ContentTypesStr' content type"
      )
    )
  }

  private def getBody(body: Option[Body], msg: String): Either[PluginError[_], Body] = {
    Either.cond(
      body.isDefined,
      body.get,
      PluginErrorMessage(msg)
    )
  }
}
