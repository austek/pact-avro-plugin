package com.collibra.plugin.avro.matchers

import au.com.dius.pact.core.matchers.{BodyItemMatchResult => AvroBodyItemMatchResult, BodyMismatch}
import com.collibra.plugin.avro.implicits.RecordImplicits._
import com.collibra.plugin.avro.utils.{PluginError, PluginErrorException}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord

import scala.jdk.CollectionConverters._

object BodyItemMatchResult {

  def apply(key: String, result: List[BodyMismatch]) = new AvroBodyItemMatchResult(key, result.asJava)

  def apply(
    path: String,
    expected: GenericRecord,
    actual: GenericRecord,
    mismatch: String,
    mismatchPath: String = "/"
  ): Either[PluginError[_], List[AvroBodyItemMatchResult]] = {
    expected.diff(actual).map { diff =>
      List(
        BodyItemMatchResult(
          path,
          List(new BodyMismatch(expected, actual, mismatch, mismatchPath, diff))
        )
      )
    }
  }

  def mismatch[T](expected: Schema.Field, actual: Schema.Field, f: String => T): Either[PluginErrorException, T] =
    ???

  def mismatch[T](expected: GenericRecord, actual: GenericRecord, f: String => T): Either[PluginErrorException, T] =
    expected.diff(actual).map { diff =>
      f(diff)
    }

}
