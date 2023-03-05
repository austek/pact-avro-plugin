package com.collibra.plugin.avro

import au.com.dius.pact.core.model.matchingrules.NumberTypeMatcher.NumberType
import au.com.dius.pact.core.model.matchingrules.{BooleanMatcher, EqualsMatcher, NotEmptyMatcher, NumberTypeMatcher}
import com.collibra.plugin.avro.utils.AvroUtils
import com.collibra.plugin.avro.utils.StringUtils._
import com.google.protobuf.struct.Value.Kind._
import com.google.protobuf.struct.{ListValue => StructListValue, Struct, Value}
import org.apache.avro.generic.GenericData
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.util
import scala.jdk.CollectionConverters._

class AvroRecordTest extends AnyFlatSpecLike with Matchers with EitherValues {

  "AvroRecord" should "support 'string' field" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "StringField",
    |  "fields": [
    |    {"name": "street", "type": "string"}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map("street" -> Value(StringValue("matching(equalTo, 'hello')")))

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("street") shouldBe "hello"
    val rules = avroRecord.value("$.street".toPactPath).rules
    rules should have size 1
    rules shouldBe Seq(EqualsMatcher.INSTANCE)
  }

  "AvroRecord" should "support 'string' field with default value" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "StringField",
    |  "fields": [
    |    {"name": "street", "type": "string", "default": "NONE"}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map()

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("street") shouldBe "NONE"
    avroRecord.value("$.street".toPactPath).rules shouldBe empty
  }

  "AvroRecord" should "support 'int' field" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "IntField",
    |  "fields": [
    |    {"name": "no", "type": "int"}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map("no" -> Value(StringValue("matching(integer, 121)")))

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("no") shouldBe 121
    val rules = avroRecord.value("$.no".toPactPath).rules
    rules should have size 1
    rules shouldBe Seq(new NumberTypeMatcher(NumberType.INTEGER))
  }

  it should "support 'int' field with default value" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "IntField",
    |  "fields": [
    |    {"name": "no", "type": "int", "default": 5}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map()

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("no") shouldBe 5
    avroRecord.value("$.no".toPactPath).rules shouldBe empty
  }

  it should "support 'long' field" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "LongField",
    |  "fields": [
    |    {"name": "id", "type": "long"}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map("id" -> Value(StringValue("notEmpty('100')")))

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("id") shouldBe 100
    val rules = avroRecord.value(PactFieldPath("$.id")).rules
    rules should have size 1
    rules shouldBe Seq(NotEmptyMatcher.INSTANCE)
  }

  it should "support 'long' field with default value" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "LongField",
    |  "fields": [
    |    {"name": "id", "type": "long", "default": 100}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map()

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("id") shouldBe 100
    avroRecord.value(PactFieldPath("$.id")).rules shouldBe empty
  }

  it should "support 'double' field" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "DoubleField",
    |  "fields": [
    |    {"name": "width", "type": "double"}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map("width" -> Value(StringValue("matching(decimal, 1.8)")))

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("width") shouldBe 1.8d
    val rules = avroRecord.value(PactFieldPath("$.width")).rules
    rules should have size 1
    rules shouldBe Seq(new NumberTypeMatcher(NumberType.DECIMAL))
  }

  it should "support 'double' field with default value" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "DoubleField",
    |  "fields": [
    |    {"name": "width", "type": "double", "default": 1.8}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map()

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("width") shouldBe 1.8d
    avroRecord.value(PactFieldPath("$.width")).rules shouldBe empty
  }

  it should "support 'float' field" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "FloatField",
    |  "fields": [
    |    {"name": "height", "type": "float"}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map("height" -> Value(StringValue("matching(decimal, 15.8)")))

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("height") shouldBe 15.8f
    val rules = avroRecord.value(PactFieldPath("$.height")).rules
    rules should have size 1
    rules shouldBe Seq(new NumberTypeMatcher(NumberType.DECIMAL))
  }

  it should "support 'float' field with default value" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "FloatField",
    |  "fields": [
    |    {"name": "height", "type": "float", "default": 15.8}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map()

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("height") shouldBe 15.8f
    avroRecord.value(PactFieldPath("$.height")).rules shouldBe empty
  }

  it should "support 'boolean' field" in {
    val schemaStr =
      """{
        |  "namespace": "com.example",
        |  "type": "record",
        |  "name": "BooleanField",
        |  "fields": [
        |    {"name": "enabled", "type": "boolean"}
        |  ]
        |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map("enabled" -> Value(StringValue("matching(boolean, true)")))

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("enabled") shouldBe true
    val rules = avroRecord.value(PactFieldPath("$.enabled")).rules
    rules should have size 1
    rules shouldBe Seq(BooleanMatcher.INSTANCE)
  }

  it should "support 'boolean' field with default value" in {
    val schemaStr =
      """{
        |  "namespace": "com.example",
        |  "type": "record",
        |  "name": "BooleanField",
        |  "fields": [
        |    {"name": "enabled", "type": "boolean", "default": true}
        |  ]
        |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map()

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("enabled") shouldBe true
    avroRecord.value(PactFieldPath("$.enabled")).rules shouldBe empty
  }

  it should "support 'enum' field" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "EnumField",
    |  "fields": [
    |    {"name": "color", "type": {"type": "enum", "name": "Color", "symbols": [ "UNKNOWN", "GREEN", "RED"]}}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map("color" -> Value(StringValue("matching(equalTo, 'GREEN')")))

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    val value1 = genericRecord.get("color")
    value1 shouldBe new GenericData.EnumSymbol(schema, "GREEN")
    val rules = avroRecord.value(PactFieldPath("$.color")).rules
    rules should have size 1
    rules shouldBe Seq(EqualsMatcher.INSTANCE)
  }

  it should "support 'enum' field with default value" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "EnumField",
    |  "fields": [
    |    {"name": "color", "type": {"type": "enum", "name": "Color", "symbols": [ "UNKNOWN", "GREEN", "RED"]}, "default": "UNKNOWN"}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map()

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    val value1 = genericRecord.get("color")
    value1 shouldBe new GenericData.EnumSymbol(schema, "UNKNOWN")
    avroRecord.value(PactFieldPath("$.color")).rules shouldBe empty
  }

  it should "support 'fixed' field" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "FixedField",
    |  "fields": [
    |    {"name": "md5", "type": {"name": "md5", "type": "fixed", "size": 4}}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map("md5" -> Value(StringValue("matching(equalTo, '\\u0000\\u0001\\u0002\\u0003')")))

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("md5") shouldBe "\\u0000\\u0001\\u0002\\u0003"
    val rules = avroRecord.value(PactFieldPath("$.md5")).rules
    rules should have size 1
    rules shouldBe Seq(EqualsMatcher.INSTANCE)
  }

  it should "support 'fixed' field  with default value" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "FixedField",
    |  "fields": [
    |    {"name": "md5", "type": {"name": "md5", "type": "fixed", "size": 4}, "default": "\\u0000"}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map()

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("md5") shouldBe "\\u0000"
    avroRecord.value(PactFieldPath("$.md5")).rules shouldBe empty
  }

  it should "support 'bytes' field" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "BytesField",
    |  "fields": [
    |    {"name": "MAC", "type": "bytes"}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map(
      "MAC" -> Value(StringValue("matching(equalTo, '\\u0000\\u0001\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007')"))
    )

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("MAC") shouldBe "\\u0000\\u0001\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007"
    val rules = avroRecord.value(PactFieldPath("$.MAC")).rules
    rules should have size 1
    rules shouldBe Seq(EqualsMatcher.INSTANCE)
  }

  it should "support 'bytes' field with default value" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "BytesField",
    |  "fields": [
    |    {"name": "MAC", "type": "bytes", "default": "\\u0000"}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map()

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("MAC") shouldBe "\\u0000"
    avroRecord.value(PactFieldPath("$.MAC")).rules shouldBe empty
  }

  it should "support 'array' field" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "ArrayField",
    |  "fields": [
    |    {"name": "names", "type": {"type": "array", "items": "string"}}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map(
      "names" -> Value(
        ListValue(
          StructListValue(
            Seq(
              Value(StringValue("notEmpty('name-1')")),
              Value(StringValue("notEmpty('name-2')"))
            )
          )
        )
      )
    )

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("names").asInstanceOf[util.List[String]] should contain theSameElementsAs List("name-1", "name-2")
    avroRecord.value should have size 1
    val avroArray = avroRecord.value.head._2.asInstanceOf[AvroArray]
    avroArray.value should have size 2
    val rules = avroArray.value.flatMap(_.rules)
    rules should have size 2
    rules shouldBe Seq(NotEmptyMatcher.INSTANCE, NotEmptyMatcher.INSTANCE)
  }

  it should "support 'map' field" in {
    val schemaStr = """{
    |  "namespace": "com.example",
    |  "type": "record",
    |  "name": "MapField",
    |  "fields": [
    |    {"name": "ages","type": { "type": "map", "values": "int"}}
    |  ]
    |}""".stripMargin

    val pactConfiguration: Map[String, Value] = Map(
      "ages" -> Value(
        StructValue(
          Struct(
            Map(
              "first" -> Value(StringValue("matching(integer, 2)")),
              "second" -> Value(StringValue("matching(integer, 3)"))
            )
          )
        )
      )
    )

    val schema = AvroUtils.parseSchema(schemaStr).value
    val avroRecord = AvroRecord(schema, pactConfiguration).value
    val genericRecord = GenericRecord(schema, avroRecord)
    genericRecord.get("ages").asInstanceOf[util.Map[String, Int]].asScala should contain theSameElementsAs Map("first" -> 2, "second" -> 3)
    avroRecord.value should have size 1
    val avroMap = avroRecord.value.head._2.asInstanceOf[AvroMap]
    avroMap.value should have size 2
    val rules = avroMap.value.flatMap(_._2.rules)
    rules should have size 2
    rules shouldBe Seq(new NumberTypeMatcher(NumberType.INTEGER), new NumberTypeMatcher(NumberType.INTEGER))
  }
}
