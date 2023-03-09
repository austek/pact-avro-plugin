package com.collibra.plugin.avro

import au.com.dius.pact.core.model.matchingrules.NumberTypeMatcher.NumberType
import au.com.dius.pact.core.model.matchingrules._
import com.collibra.plugin.avro.Avro.AvroRecord
import com.collibra.plugin.avro.TestSchemas._
import com.collibra.plugin.avro.utils.MatchingRuleCategoryImplicits._
import com.google.protobuf.struct.Value.Kind._
import com.google.protobuf.struct.{ListValue => StructListValue, Struct, Value}
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util
import scala.jdk.CollectionConverters._

class AvroRecordTest extends AnyWordSpecLike with Matchers with EitherValues {
  def provide: AfterWord = afterWord("provide")

  "String field" when {
    "value provided" should provide {
      val schema = schemaWithField("""{"name": "street", "type": "string"}""")
      val pactConfiguration: Map[String, Value] = Map("street" -> Value(StringValue("matching(equalTo, 'hello')")))
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("street") shouldBe "hello"
        }
        "returns matching rules using JsonPath" in {
          avroRecord.matchingRules should have size 1
          val rules = avroRecord.matchingRules.getRules("$.street")
          rules shouldBe Seq(EqualsMatcher.INSTANCE)
        }
      }
    }

    "value not provided but has default" should provide {
      val schema = schemaWithField("""{"name": "street", "type": "string", "default": "NONE"}""")
      val pactConfiguration: Map[String, Value] = Map()
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field containing default value" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("street") shouldBe "NONE"
        }
        "returns empty matching rules using JsonPath" in {
          avroRecord.matchingRules.getRules("$.street") shouldBe empty
        }
      }
    }
  }

  "Integer field" when {
    "value provided" should provide {
      val schema = schemaWithField("""{"name": "no", "type": "int"}""")
      val pactConfiguration: Map[String, Value] = Map("no" -> Value(StringValue("matching(integer, 121)")))
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("no") shouldBe 121
        }
        "returns matching rules using JsonPath" in {
          avroRecord.matchingRules should have size 1
          val rules = avroRecord.matchingRules.getRules("$.no")
          rules shouldBe Seq(new NumberTypeMatcher(NumberType.INTEGER))
        }
      }
    }

    "value not provided but has default" should provide {
      val schema = schemaWithField("""{"name": "no", "type": "int", "default": 5}""")
      val pactConfiguration: Map[String, Value] = Map()
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field containing default value" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("no") shouldBe 5
        }
        "returns empty matching rules using JsonPath" in {
          avroRecord.matchingRules.getRules("$.no") shouldBe empty
        }
      }
    }
  }

  "Long field" when {
    "value provided" should provide {
      val schema = schemaWithField("""{"name": "id", "type": "long"}""")
      val pactConfiguration: Map[String, Value] = Map("id" -> Value(StringValue("notEmpty('100')")))
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("id") shouldBe 100
        }
        "returns matching rules using JsonPath" in {
          avroRecord.matchingRules should have size 1
          val rules = avroRecord.matchingRules.getRules("$.id")
          rules shouldBe Seq(NotEmptyMatcher.INSTANCE)
        }
      }
    }

    "value not provided but has default" should provide {
      val schema = schemaWithField("""{"name": "id", "type": "long", "default": 100}""")
      val pactConfiguration: Map[String, Value] = Map()
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field containing default value" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("id") shouldBe 100
        }
        "returns empty matching rules using JsonPath" in {
          avroRecord.matchingRules.getRules("$.id") shouldBe empty
        }
      }
    }
  }

  "Double field" when {
    "value provided" should provide {
      val schema = schemaWithField("""{"name": "width", "type": "double"}""")
      val pactConfiguration: Map[String, Value] = Map("width" -> Value(StringValue("matching(decimal, 1.8)")))
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("width") shouldBe 1.8d
        }
        "returns matching rules using JsonPath" in {
          avroRecord.matchingRules should have size 1
          val rules = avroRecord.matchingRules.getRules("$.width")
          rules shouldBe Seq(new NumberTypeMatcher(NumberType.DECIMAL))
        }
      }
    }

    "value not provided but has default" should provide {
      val schema = schemaWithField("""{"name": "width", "type": "double", "default": 1.8}""")
      val pactConfiguration: Map[String, Value] = Map()
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field containing default value" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("width") shouldBe 1.8d
        }
        "returns empty matching rules using JsonPath" in {
          avroRecord.matchingRules.getRules("$.width") shouldBe empty
        }
      }
    }
  }

  "Float field" when {
    "value provided" should provide {
      val schema = schemaWithField("""{"name": "height", "type": "float"}""")
      val pactConfiguration: Map[String, Value] = Map("height" -> Value(StringValue("matching(decimal, 15.8)")))
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("height") shouldBe 15.8f
        }
        "returns matching rules using JsonPath" in {
          avroRecord.matchingRules should have size 1
          val rules = avroRecord.matchingRules.getRules("$.height")
          rules shouldBe Seq(new NumberTypeMatcher(NumberType.DECIMAL))
        }
      }
    }

    "value not provided but has default" should provide {
      val schema = schemaWithField("""{"name": "height", "type": "float", "default": 15.8}""")
      val pactConfiguration: Map[String, Value] = Map()
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field containing default value" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("height") shouldBe 15.8f
        }
        "returns empty matching rules using JsonPath" in {
          avroRecord.matchingRules.getRules("$.height") shouldBe empty
        }
      }
    }
  }

  "Boolean field" when {
    "value provided" should provide {
      val schema = schemaWithField("""{"name": "enabled", "type": "boolean"}""")
      val pactConfiguration: Map[String, Value] = Map("enabled" -> Value(StringValue("matching(boolean, true)")))
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("enabled") shouldBe true
        }
        "returns matching rules using JsonPath" in {
          avroRecord.matchingRules should have size 1
          val rules = avroRecord.matchingRules.getRules("$.enabled")
          rules shouldBe Seq(BooleanMatcher.INSTANCE)
        }
      }
    }

    "value not provided but has default" should provide {
      val schema = schemaWithField("""{"name": "enabled", "type": "boolean", "default": true}""")
      val pactConfiguration: Map[String, Value] = Map()
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field containing default value" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("enabled") shouldBe true
        }
        "returns empty matching rules using JsonPath" in {
          avroRecord.matchingRules.getRules("$.enabled") shouldBe empty
        }
      }
    }
  }

  "Enum field" when {
    "value provided" should provide {
      val schema = schemaWithField("""{"name": "color", "type": {"type": "enum", "name": "Color", "symbols": [ "UNKNOWN", "GREEN", "RED"]}}""")
      val pactConfiguration: Map[String, Value] = Map("color" -> Value(StringValue("matching(equalTo, 'GREEN')")))
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("color") shouldBe new GenericData.EnumSymbol(schema, "GREEN")
        }
        "returns matching rules using JsonPath" in {
          avroRecord.matchingRules should have size 1
          val rules = avroRecord.matchingRules.getRules("$.color")
          rules shouldBe Seq(EqualsMatcher.INSTANCE)
        }
      }
    }

    "value not provided but has default" should provide {
      val schema =
        schemaWithField("""{"name": "color", "type": {"type": "enum", "name": "Color", "symbols": [ "UNKNOWN", "GREEN", "RED"]}, "default": "UNKNOWN"}""")
      val pactConfiguration: Map[String, Value] = Map()
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field containing default value" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("color") shouldBe new GenericData.EnumSymbol(schema, "UNKNOWN")
        }
        "returns empty matching rules using JsonPath" in {
          avroRecord.matchingRules.getRules("$.color") shouldBe empty
        }
      }
    }
  }

  "Fixed field" when {
    "value provided" should provide {
      val schema = schemaWithField("""{"name": "md5", "type": {"name": "md5", "type": "fixed", "size": 4}}""")
      val pactConfiguration: Map[String, Value] = Map("md5" -> Value(StringValue("matching(equalTo, '\\u0000\\u0001\\u0002\\u0003')")))
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("md5") shouldBe new GenericData.Fixed(schema.getField("md5").schema(), "\\u0000\\u0001\\u0002\\u0003".getBytes)
        }
        "returns matching rules using JsonPath" in {
          avroRecord.matchingRules should have size 1
          val rules = avroRecord.matchingRules.getRules("$.md5")
          rules shouldBe Seq(EqualsMatcher.INSTANCE)
        }
      }
    }

    "value not provided but has default" should provide {
      val schema = schemaWithField("""{"name": "md5", "type": {"name": "md5", "type": "fixed", "size": 4}, "default": "\\u0000"}""")
      val pactConfiguration: Map[String, Value] = Map()
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field containing default value" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("md5") shouldBe new GenericData.Fixed(schema.getField("md5").schema(), "\\u0000".getBytes)
        }
        "returns empty matching rules using JsonPath" in {
          avroRecord.matchingRules.getRules("$.md5") shouldBe empty
        }
      }
    }
  }

  "Bytes field" when {
    "value provided" should provide {
      val schema = schemaWithField("""{"name": "MAC", "type": "bytes"}""")
      val pactConfiguration: Map[String, Value] = Map(
        "MAC" -> Value(StringValue("matching(equalTo, '\\u0000\\u0001\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007')"))
      )
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("MAC") shouldBe "\\u0000\\u0001\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007"
        }
        "returns matching rules using JsonPath" in {
          avroRecord.matchingRules should have size 1
          val rules = avroRecord.matchingRules.getRules("$.MAC")
          rules shouldBe Seq(EqualsMatcher.INSTANCE)
        }
      }
    }

    "value not provided but has default" should provide {
      val schema = schemaWithField("""{"name": "MAC", "type": "bytes", "default": "\\u0000"}""")
      val pactConfiguration: Map[String, Value] = Map()
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field containing default value" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("MAC") shouldBe "\\u0000"
        }
        "returns empty matching rules using JsonPath" in {
          avroRecord.matchingRules.getRules("$.MAC") shouldBe empty
        }
      }
    }
  }

  "Array simple type field" when {
    "value provided" should provide {
      val schema = schemaWithField("""{"name": "names", "type": {"type": "array", "items": "string"}}""")
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
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("names").asInstanceOf[util.List[String]] should contain theSameElementsAs List("name-1", "name-2")
        }
        "returns matching rules using JsonPath" in {
          avroRecord.matchingRules should have size 2
          avroRecord.matchingRules.getRules("$.names.0") shouldBe List(NotEmptyMatcher.INSTANCE)
          avroRecord.matchingRules.getRules("$.names.1") shouldBe List(NotEmptyMatcher.INSTANCE)
        }
      }
    }
  }

  "Array complex type field" when {
    "value provided" should provide {
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
      val addressSchema = schema.getField("addresses").schema().getElementType
      val addressRecord = new GenericData.Record(addressSchema)
      addressRecord.put("street", "street name")

      "a method," which {
        "returns GenericRecord with field" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("addresses").asInstanceOf[util.List[GenericRecord]] should contain theSameElementsAs List(addressRecord)
        }
        "returns matching rules using JsonPath" in {
          avroRecord.matchingRules should have size 1
          avroRecord.matchingRules.getRules("$.addresses.0.street") shouldBe List(EqualsMatcher.INSTANCE)
        }
      }
    }
  }

  "Map field" when {
    "value provided" should provide {
      val schema = schemaWithField("""{"name": "ages","type": { "type": "map", "values": "int"}}""")
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
      val avroRecord = AvroRecord(schema, pactConfiguration).value

      "a method," which {
        "returns GenericRecord with field" in {
          val genericRecord = avroRecord.toGenericRecord(schema)
          genericRecord.get("ages").asInstanceOf[util.Map[String, Int]].asScala should contain theSameElementsAs Map("first" -> 2, "second" -> 3)
          avroRecord.value should have size 1
        }
        "returns matching rules using JsonPath" in {
          avroRecord.matchingRules should have size 2
          avroRecord.matchingRules.getRules("$.ages.first") shouldBe List(new NumberTypeMatcher(NumberType.INTEGER))
          avroRecord.matchingRules.getRules("$.ages.second") shouldBe List(new NumberTypeMatcher(NumberType.INTEGER))
        }
      }
    }
  }
}
