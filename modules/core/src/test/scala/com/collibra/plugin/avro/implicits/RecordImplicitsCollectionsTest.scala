package com.collibra.plugin.avro.implicits

import au.com.dius.pact.core.matchers.MatchingContext
import au.com.dius.pact.core.model.matchingrules.MatchingRuleCategory
import com.collibra.plugin.avro.AvroPluginConstants.MatchingRuleCategoryName
import com.collibra.plugin.avro.AvroRecord
import com.collibra.plugin.avro.TestSchemas._
import com.collibra.plugin.avro.implicits.RecordImplicits._
import com.collibra.plugin.avro.matchers.{BodyItemMatchResult, BodyMismatch}
import com.google.protobuf.struct.Value.Kind._
import com.google.protobuf.struct.{ListValue => StructListValue, Struct, Value}
import org.apache.avro.generic.GenericData
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._
class RecordImplicitsCollectionsTest extends AnyWordSpec with Matchers with EitherValues {
  "GenericRecord" when {
    "comparing Array fields with String values" should {
      val schema = schemaWithField("""{"name": "names", "type": {"type": "array", "items": "string"}}""")
      val pactConfiguration: Map[String, Value] = Map(
        "names" -> Value(
          ListValue(
            StructListValue(
              Seq(
                Value(StringValue("matching(equalTo, 'name-1')")),
                Value(StringValue("matching(equalTo, 'name-2')"))
              )
            )
          )
        )
      )
      val avroRecord = AvroRecord(schema, pactConfiguration).value
      val record = avroRecord.toGenericRecord(schema)

      val matchingRules: MatchingRuleCategory = new MatchingRuleCategory(MatchingRuleCategoryName)
      avroRecord.addRules(matchingRules)
      implicit val context: MatchingContext = new MatchingContext(matchingRules, false)

      val expected = List("name-1", "name-2").asJava

      "return empty BodyMatch list for equal fields" in {
        val otherRecord = new GenericData.Record(schema)
        otherRecord.put("names", expected)

        val result = record.compare(List("$"), otherRecord).value
        result should have size 2
        result shouldBe List(
          BodyItemMatchResult("$.names.0", List()),
          BodyItemMatchResult("$.names.1", List())
        )
      }

      "return a BodyMatch for unequal fields" in {
        val otherRecord = new GenericData.Record(schema)
        val other = List("name-3", "name-4").asJava
        otherRecord.put("names", other)

        val result = record.compare(List("$"), otherRecord).value
        result should have size 2
        result shouldBe
          List(
            BodyItemMatchResult(
              "$.names.0",
              List(
                BodyMismatch("name-1", "name-3", "Expected 'name-3' (String) to equal 'name-1' (String)", "$.names.0", "")
              )
            ),
            BodyItemMatchResult(
              "$.names.1",
              List(
                BodyMismatch("name-2", "name-4", "Expected 'name-4' (String) to equal 'name-2' (String)", "$.names.1", "")
              )
            )
          )
      }

      "return a BodyMatch for missing field value" in {
        val otherRecord = new GenericData.Record(schema)
        val result = record.compare(List("$"), otherRecord).value
        result should have size 1
        result shouldBe List(
          BodyItemMatchResult(
            "$.names",
            List(
              BodyMismatch(expected, null, s"Expected null (Null) to equal '$expected' (Array)", "$.names", null)
            )
          )
        )
      }
    }

    "comparing Array fields with Integer values" should {
      val schema = schemaWithField("""{"name": "ids", "type": {"type": "array", "items": "int"}}""")
      val pactConfiguration: Map[String, Value] = Map(
        "ids" -> Value(
          ListValue(
            StructListValue(
              Seq(
                Value(StringValue("matching(equalTo, 1)")),
                Value(StringValue("matching(equalTo, 2)"))
              )
            )
          )
        )
      )
      val avroRecord = AvroRecord(schema, pactConfiguration).value
      val record = avroRecord.toGenericRecord(schema)

      val matchingRules: MatchingRuleCategory = avroRecord.matchingRules
      implicit val context: MatchingContext = new MatchingContext(matchingRules, false)

      val expected = List(1, 2).asJava

      "return empty BodyMatch list for equal fields" in {
        val otherRecord = new GenericData.Record(schema)
        otherRecord.put("ids", expected)

        val result = record.compare(List("$"), otherRecord).value
        result should have size 2
        result shouldBe List(
          BodyItemMatchResult("$.ids.0", List()),
          BodyItemMatchResult("$.ids.1", List())
        )
      }

      "return a BodyMatch for unequal fields" in {
        val otherRecord = new GenericData.Record(schema)
        val other = List(3, 4).asJava
        otherRecord.put("ids", other)

        val result = record.compare(List("$"), otherRecord).value
        result should have size 2
        result shouldBe
          List(
            BodyItemMatchResult(
              "$.ids.0",
              List(
                BodyMismatch(1, 3, "Expected 3 (Integer) to equal 1 (Integer)", "$.ids.0", "")
              )
            ),
            BodyItemMatchResult(
              "$.ids.1",
              List(
                BodyMismatch(2, 4, "Expected 4 (Integer) to equal 2 (Integer)", "$.ids.1", "")
              )
            )
          )
      }

      "return a BodyMatch for missing field value" in {
        val otherRecord = new GenericData.Record(schema)
        val result = record.compare(List("$"), otherRecord).value
        result should have size 1
        result shouldBe List(
          BodyItemMatchResult(
            "$.ids",
            List(
              BodyMismatch(expected, null, s"Expected null (Null) to equal '$expected' (Array)", "$.ids", null)
            )
          )
        )
      }
    }

    "comparing Array fields with Record values" should {
      val schema = schemaWithField("""{
                                     |  "name": "addresses",
                                     |  "type": {
                                     |    "type": "array",
                                     |    "items": {
                                     |      "name": "address",
                                     |      "type": "record",
                                     |      "fields": [
                                     |        { "name": "street", "type": "string" }
                                     |      ]
                                     |    }
                                     |  }
                                     |}""".stripMargin)
      val pactConfiguration: Map[String, Value] = Map(
        "addresses" -> Value(
          ListValue(
            StructListValue(
              Seq(
                Value(StructValue(Struct(Map("street" -> Value(StringValue("matching(equalTo, 'street name')"))))))
              )
            )
          )
        )
      )
      val avroRecord = AvroRecord(schema, pactConfiguration).value
      val record = avroRecord.toGenericRecord(schema)

      val matchingRules: MatchingRuleCategory = avroRecord.matchingRules
      implicit val context: MatchingContext = new MatchingContext(matchingRules, false)

      val addressRecord = new GenericData.Record(schema.getField("addresses").schema().getElementType)
      addressRecord.put("street", "street name")
      val expected = List(addressRecord).asJava

      "return empty BodyMatch list for equal fields" in {
        val otherRecord = new GenericData.Record(schema)
        otherRecord.put("addresses", expected)

        val result = record.compare(List("$"), otherRecord).value
        result should have size 1
        result shouldBe List(BodyItemMatchResult("$.addresses.0.street", List()))
      }

      "return a BodyMatch for unequal fields" in {
        val otherAddressRecord = new GenericData.Record(schema.getField("addresses").schema().getElementType)
        otherAddressRecord.put("street", "other")
        val other = List(otherAddressRecord).asJava
        val otherRecord = new GenericData.Record(schema)
        otherRecord.put("addresses", other)

        val result = record.compare(List("$"), otherRecord).value
        result should have size 1
        result shouldBe
          List(
            BodyItemMatchResult(
              "$.addresses.0.street",
              List(
                BodyMismatch("street name", "other", "Expected 'other' (String) to equal 'street name' (String)", "$.addresses.0.street", "")
              )
            )
          )
      }

      "return a BodyMatch for missing field value" in {
        val otherRecord = new GenericData.Record(schema)
        val result = record.compare(List("$"), otherRecord).value
        result should have size 1
        result shouldBe List(
          BodyItemMatchResult(
            "$.addresses",
            List(
              BodyMismatch(expected, null, s"Expected null (Null) to equal '$expected' (Array)", "$.addresses", null)
            )
          )
        )
      }
    }
  }
}
