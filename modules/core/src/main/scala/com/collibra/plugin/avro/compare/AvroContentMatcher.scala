package com.collibra.plugin.avro.compare

import au.com.dius.pact.core.matchers._
import com.collibra.plugin.avro.implicits.AvroRecordImplicits._
import com.collibra.plugin.avro.utils.PluginError
import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.generic.GenericRecord

import scala.jdk.CollectionConverters._

object AvroContentMatcher extends StrictLogging {

  def compare(expected: GenericRecord, actual: GenericRecord, context: MatchingContext): Either[Seq[PluginError[_]], BodyMatchResult] =
    expected.compare(List("$"), actual)(context).map { result =>
      new BodyMatchResult(null, result.asJava)
    }
  /*
    private def compareValue[T](
      path: List[String],
      field: Schema.Field,
      expected: T,
      actual: T,
      diffCallback: () => String,
      context: MatchingContext
    ): List[BodyItemMatchResult] = {
      val valuePath = path.constructPath
      logger.debug(s">>> compareValue($path, $field, $expected, $actual, $context)")
      if (context.matcherDefined(path.asJava)) {
        logger.debug(s"compareValue: Matcher defined for path $path")
        List(
          new BodyItemMatchResult(
            valuePath,
            Matchers.domatch(
              context,
              path.asJava,
              expected,
              actual,
              (expected: Any, actual: Any, message: String, path: util.List[String]) =>
                new BodyMismatch(expected, actual, message, constructPath(path), diffCallback())
            )
          )
        )
      } else {
        logger.debug(s"compareValue: No matcher defined for path $path, using equality")
        if (expected == actual) {
          List(new BodyItemMatchResult(valuePath, List().asJava))
        } else {
          List(
            new BodyItemMatchResult(
              valuePath,
              List(
                new BodyMismatch(
                  expected,
                  actual,
                  s"Expected '$expected' ($field) but received value '$actual'",
                  valuePath,
                  diffCallback()
                )
              ).asJava
            )
          )
        }
      }
    }*/
}
