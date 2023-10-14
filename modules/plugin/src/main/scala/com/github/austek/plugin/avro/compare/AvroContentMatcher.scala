package com.github.austek.plugin.avro.compare

import au.com.dius.pact.core.matchers._
import com.github.austek.plugin.avro.error.PluginError
import com.github.austek.plugin.avro.implicits.RecordImplicits._
import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.generic.GenericRecord

import scala.jdk.CollectionConverters._

object AvroContentMatcher extends StrictLogging {

  def compare(expected: GenericRecord, actual: GenericRecord, context: MatchingContext): Either[Seq[PluginError[_]], BodyMatchResult] =
    expected.compare(List("$"), actual)(context).map { result =>
      new BodyMatchResult(null, result.asJava)
    }
}
