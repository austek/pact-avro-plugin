package com.collibra.plugin.avro.compare

import au.com.dius.pact.core.matchers.MatchingContext
import au.com.dius.pact.core.model.matchingrules.{MatchingRule, MatchingRuleCategory, MatchingRuleGroup}
import com.collibra.plugin.avro.AvroPluginConstants.MatchingRuleCategoryName
import com.collibra.plugin.avro.ContentTypeConstants._
import com.collibra.plugin.avro.utils._
import com.google.protobuf.ByteString
import com.google.protobuf.struct.Struct.toJavaProto
import com.typesafe.scalalogging.StrictLogging
import io.pact.plugin._
import io.pact.plugins.jvm.core.Utils.{INSTANCE => PactCoreUtils}
import org.apache.avro.Schema
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
      actual <- AvroUtils.deserialize(avroSchema, actualBody.getContent.newInput())
      expected <- AvroUtils.deserialize(avroSchema, expectedBody.getContent.newInput())
      response <- buildResponse(request, actual, expected)
    } yield response
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
      ContentTypes.contains(body.contentType),
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
