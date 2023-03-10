package com.collibra.plugin.avro.matchers

import au.com.dius.pact.core.matchers.{BodyMismatch => AvroBodyMismatch}

object BodyMismatch {

  def apply[T](expected: T, actual: T, mismatch: String, path: String, diff: String) = new AvroBodyMismatch(expected, actual, mismatch, path, diff)

  def apply[T](expected: T, actual: T, mismatch: String, path: String) = new AvroBodyMismatch(expected, actual, mismatch, path)

  def apply[T](expected: T, actual: T, mismatch: String) = new AvroBodyMismatch(expected, actual, mismatch)

  def expectedNullMismatch[T](expected: T, mismatch: String, path: String, diff: String) = new AvroBodyMismatch(expected, null, mismatch, path, diff)

  def expectedNullMismatch[T](expected: T, mismatch: String, path: String) = new AvroBodyMismatch(expected, null, mismatch, path)

}
