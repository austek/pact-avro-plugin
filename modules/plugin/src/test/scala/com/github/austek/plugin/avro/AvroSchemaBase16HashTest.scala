package com.github.austek.plugin.avro

import com.github.austek.plugin.avro.utils.AvroUtils
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.nio.file.Path

class AvroSchemaBase16HashTest extends AnyFlatSpecLike with Matchers with EitherValues {

  "AvroSchemaBase16Hash" should "calculate MD5 hash for a single schema" in {
    val itemSchema = AvroUtils.parseSchema(Path.of(getClass.getResource("/item.avsc").toURI).toFile)
    itemSchema shouldBe Symbol("right")
    AvroSchemaBase16Hash(itemSchema.value).value shouldBe "dbb7fa0e6af50783affd6c86ef21fda8"
  }

  it should "calculate MD5 hash for multiple schemas" in {
    val itemSchema = AvroUtils.parseSchema(Path.of(getClass.getResource("/item.avsc").toURI).toFile)
    itemSchema shouldBe Symbol("right")
    AvroSchemaBase16Hash(itemSchema.value).value shouldBe "dbb7fa0e6af50783affd6c86ef21fda8"

    val secondSchema = AvroUtils.parseSchema(Path.of(getClass.getResource("/schemas.avsc").toURI).toFile)
    secondSchema shouldBe Symbol("right")
    AvroSchemaBase16Hash(secondSchema.value).value shouldBe "6d9ace89f6337b8ff48236c5532b639d"
  }
}
