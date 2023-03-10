package com.collibra.plugin.avro.implicits

import au.com.dius.pact.core.matchers.MatchingContext
import au.com.dius.pact.core.model.matchingrules.MatchingRuleCategory
import com.collibra.plugin.avro.Avro.AvroRecord
import com.collibra.plugin.avro.TestSchemas._
import com.collibra.plugin.avro.implicits.RecordImplicits._
import com.collibra.plugin.avro.matchers.{BodyItemMatchResult, BodyMismatch}
import com.google.protobuf.struct.Value.Kind._
import com.google.protobuf.struct.{Struct, Value}
import org.apache.avro.generic.GenericData
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util
import scala.jdk.CollectionConverters._

class RecordImplicitsMapsTest extends AnyWordSpec with Matchers with EitherValues {

  "GenericRecord" when {
    "comparing Map fields with String values" should {
      val schema = schemaWithField("""{"name": "ages","type": { "type": "map", "values": "string"}}""")
      val pactConfiguration: Map[String, Value] = Map(
        "ages" -> Value(
          StructValue(
            Struct(
              Map(
                "first" -> Value(StringValue("matching(equalTo, 'name-1')")),
                "second" -> Value(StringValue("matching(equalTo, 'name-2')"))
              )
            )
          )
        )
      )
      val avroRecord = AvroRecord(schema, pactConfiguration).value
      val record = avroRecord.toGenericRecord(schema)

      val matchingRules: MatchingRuleCategory = avroRecord.matchingRules
      implicit val context: MatchingContext = new MatchingContext(matchingRules, false)
      val expected = Map("first" -> "name-1", "second" -> "name-2").asJava

      "return empty BodyMatch list for equal fields" in {
        val otherRecord = new GenericData.Record(schema)
        otherRecord.put("ages", expected)

        val result = record.compare(List("$"), otherRecord).value
        result should have size 2
        result shouldBe List(
          BodyItemMatchResult("$.ages.first", List()),
          BodyItemMatchResult("$.ages.second", List())
        )
      }

      "return a BodyMatch for unequal fields" in {
        val otherRecord = new GenericData.Record(schema)
        val other = Map("first" -> "name-3", "second" -> "name-4").asJava
        otherRecord.put("ages", other)

        val result = record.compare(List("$"), otherRecord).value
        result should have size 2
        result shouldBe
          List(
            BodyItemMatchResult(
              "$.ages.first",
              List(
                BodyMismatch("name-1", "name-3", "Expected 'name-3' (String) to equal 'name-1' (String)", "$.ages.first", "")
              )
            ),
            BodyItemMatchResult(
              "$.ages.second",
              List(
                BodyMismatch("name-2", "name-4", "Expected 'name-4' (String) to equal 'name-2' (String)", "$.ages.second", "")
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
            "$.ages",
            List(
              BodyMismatch(expected, null, s"Expected null (Null) to equal '$expected' (Map)", "$.ages", null)
            )
          )
        )
      }
    }

    "comparing Map fields with Integer values" should {
      val schema = schemaWithField("""{"name": "ids", "type": {"type": "map", "values": "int"}}""")
      val pactConfiguration: Map[String, Value] = Map(
        "ids" -> Value(
          StructValue(
            Struct(
              Map(
                "first" -> Value(StringValue("matching(equalTo, 1)")),
                "second" -> Value(StringValue("matching(equalTo, 2)"))
              )
            )
          )
        )
      )
      val avroRecord = AvroRecord(schema, pactConfiguration).value
      val record = avroRecord.toGenericRecord(schema)

      val matchingRules: MatchingRuleCategory = avroRecord.matchingRules
      implicit val context: MatchingContext = new MatchingContext(matchingRules, false)

      val expected: util.Map[String, Int] = Map("first" -> 1, "second" -> 2).asJava

      "return empty BodyMatch list for equal fields" in {
        val otherRecord = new GenericData.Record(schema)
        otherRecord.put("ids", expected)

        val result = record.compare(List("$"), otherRecord).value
        result should have size 2
        result shouldBe List(
          BodyItemMatchResult("$.ids.first", List()),
          BodyItemMatchResult("$.ids.second", List())
        )
      }

      "return a BodyMatch for unequal fields" in {
        val otherRecord = new GenericData.Record(schema)
        val other = Map("first" -> 3, "second" -> 4).asJava
        otherRecord.put("ids", other)

        val result = record.compare(List("$"), otherRecord).value
        result should have size 2
        result shouldBe
          List(
            BodyItemMatchResult(
              "$.ids.first",
              List(
                BodyMismatch(1, 3, "Expected 3 (Integer) to equal 1 (Integer)", "$.ids.first", "")
              )
            ),
            BodyItemMatchResult(
              "$.ids.second",
              List(
                BodyMismatch(2, 4, "Expected 4 (Integer) to equal 2 (Integer)", "$.ids.second", "")
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
              BodyMismatch(expected, null, s"Expected null (Null) to equal '$expected' (Map)", "$.ids", null)
            )
          )
        )
      }
    }

    "comparing Map fields with Record values" should {
      val schema = schemaWithField("""{
                                     |  "name": "addresses",
                                     |  "type": {
                                     |    "type": "map",
                                     |    "values": {
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
          StructValue(
            Struct(
              Map(
                "first" -> Value(StructValue(Struct(Map("street" -> Value(StringValue("matching(equalTo, 'first street')")))))),
                "second" -> Value(StructValue(Struct(Map("street" -> Value(StringValue("matching(equalTo, 'second street')"))))))
              )
            )
          )
        )
      )
      val avroRecord = AvroRecord(schema, pactConfiguration).value
      val record = avroRecord.toGenericRecord(schema)

      val matchingRules: MatchingRuleCategory = avroRecord.matchingRules
      implicit val context: MatchingContext = new MatchingContext(matchingRules, false)

      val addressSchema = schema.getField("addresses").schema().getValueType
      val firstAddressRecord = new GenericData.Record(addressSchema)
      firstAddressRecord.put("street", "first street")
      val secondAddressRecord = new GenericData.Record(addressSchema)
      secondAddressRecord.put("street", "second street")
      val expected: util.Map[String, GenericData.Record] = Map("first" -> firstAddressRecord, "second" -> secondAddressRecord).asJava

      "return empty BodyMatch list for equal fields" in {
        val otherRecord = new GenericData.Record(schema)
        otherRecord.put("addresses", expected)

        val result = record.compare(List("$"), otherRecord).value
        result should have size 2
        result shouldBe List(BodyItemMatchResult("$.addresses.first.street", List()), BodyItemMatchResult("$.addresses.second.street", List()))
      }

      "return a BodyMatch for unequal fields" in {
        val fourthAddressRecord = new GenericData.Record(addressSchema)
        fourthAddressRecord.put("street", "fourth street")
        val otherAddressRecord: util.Map[String, GenericData.Record] = Map("first" -> firstAddressRecord, "second" -> fourthAddressRecord).asJava

        val otherRecord = new GenericData.Record(schema)
        otherRecord.put("addresses", otherAddressRecord)

        val result = record.compare(List("$"), otherRecord).value
        result should have size 2
        result shouldBe
          List(
            BodyItemMatchResult("$.addresses.first.street", List()),
            BodyItemMatchResult(
              "$.addresses.second.street",
              List(
                BodyMismatch(
                  "second street",
                  "fourth street",
                  "Expected 'fourth street' (String) to equal 'second street' (String)",
                  "$.addresses.second.street",
                  ""
                )
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
              BodyMismatch(expected, null, s"Expected null (Null) to equal '$expected' (Map)", "$.addresses", null)
            )
          )
        )
      }
    }
  }
}
