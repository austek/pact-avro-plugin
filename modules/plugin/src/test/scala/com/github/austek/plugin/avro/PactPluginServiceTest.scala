package com.github.austek.plugin.avro

import com.github.austek.plugin.avro.Avro.*
import com.github.austek.plugin.avro.utils.AvroUtils
import com.github.austek.plugin.avro.utils.StringUtils.*
import com.google.protobuf.struct.Value.Kind.*
import com.google.protobuf.struct.{ListValue as StructListValue, Struct, Value}
import io.pact.plugin.pact_plugin.*
import io.pact.plugin.pact_plugin.Body.ContentTypeHint.BINARY
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues}

import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

class PactPluginServiceTest extends AsyncFlatSpecLike with Matchers with OptionValues with ScalaFutures with EitherValues {

  "Avro Plugin" should "initialise" in {
    new PactAvroPluginService()
      .initPlugin(InitPluginRequest("test", "0"))
      .map { response =>
        response.catalogue should have size 1

        val first = response.catalogue.head
        first.`type` shouldBe CatalogueEntry.EntryType.CONTENT_MATCHER
        first.key shouldBe "avro"
        first.values("content-types") shouldBe "application/avro;avro/bytes;avro/binary;application/*+avro"
      }
  }

  it should "require configuration" in {
    new PactAvroPluginService()
      .configureInteraction(ConfigureInteractionRequest("text/test"))
      .map { response =>
        response.error shouldBe "Configuration not found"
      }
  }

  it should "require path to the avro schema file" in {
    new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "text/test",
          Some(
            Struct(
              Map(
                "pact:content-type" -> Value(StringValue("application/avro"))
              )
            )
          )
        )
      )
      .map { response =>
        response.error shouldBe "Config item with key 'pact:avro' and path to the avro schema file is required"
      }
  }

  it should "require path to the avro schema file that exists" in {
    new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "text/test",
          Some(
            Struct(
              Map(
                "pact:avro" -> Value(StringValue("non-existing.avsc"))
              )
            )
          )
        )
      )
      .map { response =>
        response.error should startWith("Failed to parse avro schema from file:")
        response.error should endWith("non-existing.avsc")
      }
  }

  it should "require a valid avro schema file" in {
    val schemasPath = Paths.get(getClass.getResource("/invalid.avsc").toURI).toFile.getAbsolutePath
    new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "text/test",
          Some(
            Struct(
              Map(
                "pact:avro" -> Value(StringValue(schemasPath))
              )
            )
          )
        )
      )
      .map { response =>
        response.error should startWith("Failed to parse avro schema from file:")
        response.error should endWith("invalid.avsc")
      }
  }

  it should "require record-name to be defined" in {
    val schemasPath = Paths.get(getClass.getResource("/schemas.avsc").toURI).toFile.getAbsolutePath
    new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "text/test",
          Some(
            Struct(
              Map(
                "pact:avro" -> Value(StringValue(schemasPath))
              )
            )
          )
        )
      )
      .map { response =>
        response.error shouldBe "Config item with key 'pact:record-name' and record-name of the payload is required"
      }
  }

  it should "return Interaction Response for a single record" in {
    val schemaFile = Paths.get(getClass.getResource("/item.avsc").toURI).toFile
    val schemaPath = schemaFile.getAbsolutePath
    val eventualResponse = new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "avro/binary",
          Some(
            Struct(
              Map(
                "pact:avro" -> Value(StringValue(schemaPath)),
                "pact:record-name" -> Value(StringValue("Item")),
                "pact:content-type" -> Value(StringValue("avro/binary")),
                "name" -> Value(StringValue("notEmpty('Item-41')")),
                "id" -> Value(StringValue("notEmpty('41')"))
              )
            )
          )
        )
      )
    val response = eventualResponse.futureValue
    response.interaction should have size 1
    val interaction: InteractionResponse = response.interaction.head
    val content = interaction.contents.value
    content.contentTypeHint shouldBe Body.ContentTypeHint.BINARY
    content.contentType shouldBe "avro/binary;record=Item"

    val schema = AvroUtils.parseSchema(schemaFile).value
    val bytes =
      AvroRecord(
        "$".toPactPath,
        ".".toFieldName,
        Map(
          "$.name".toPactPath -> AvroString("$.name".toPactPath, "name".toFieldName, "Item-41"),
          "$.id".toPactPath -> AvroInt("$.id".toPactPath, "id".toFieldName, 41)
        )
      ).toByteString(schema).value
    content.getContent shouldBe bytes

    interaction.rules should have size 2
  }

  it should "return Interaction Response for a record with other record in field" in {
    val schemaFile = Paths.get(getClass.getResource("/schemas.avsc").toURI).toFile
    val schemaPath = schemaFile.getAbsolutePath
    val eventualResponse = new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "avro/binary",
          Some(
            Struct(
              Map(
                "pact:avro" -> Value(StringValue(schemaPath)),
                "pact:record-name" -> Value(StringValue("Complex")),
                "pact:content-type" -> Value(StringValue("avro/binary")),
                "id" -> Value(StringValue("notEmpty('100')")),
                "names" -> Value(
                  ListValue(
                    StructListValue(
                      Seq(
                        Value(StringValue("notEmpty('name-1')")),
                        Value(StringValue("notEmpty('name-2')"))
                      )
                    )
                  )
                ),
                "enabled" -> Value(StringValue("matching(boolean, true)")),
                "no" -> Value(StringValue("matching(integer, 121)")),
                "height" -> Value(StringValue("matching(decimal, 15.8)")),
                "width" -> Value(StringValue("matching(decimal, 1.8)")),
                "ages" -> Value(
                  StructValue(
                    Struct(
                      Map(
                        "first" -> Value(StringValue("matching(integer, 2)")),
                        "second" -> Value(StringValue("matching(integer, 3)"))
                      )
                    )
                  )
                ),
                "color" -> Value(StringValue("matching(equalTo, 'GREEN')")),
                "md5" -> Value(StringValue("matching(equalTo, '\\\u0000\\\u0001\\\u0002\\\u0003')")),
                "address" -> Value(StructValue(Struct(Map("street" -> Value(StringValue("notEmpty('street name')")))))),
                "items" -> Value(
                  ListValue(
                    StructListValue(
                      Vector(
                        Value(StructValue(Struct(Map("name" -> Value(StringValue("notEmpty('Item-1')")), "id" -> Value(StringValue("matching(integer, 1)")))))),
                        Value(StructValue(Struct(Map("name" -> Value(StringValue("notEmpty('Item-2')")), "id" -> Value(StringValue("matching(integer, 2)"))))))
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )
    val response = eventualResponse.futureValue
    response.interaction should have size 1
    val interaction: InteractionResponse = response.interaction.head
    val content = interaction.contents.value
    content.contentTypeHint shouldBe Body.ContentTypeHint.BINARY
    content.contentType shouldBe "avro/binary;record=Complex"
    val schema = AvroUtils.parseSchema(schemaFile).value.getTypes.asScala.find(_.getName == "Complex").get
    val avroRecord = AvroRecord(
      "$".toPactPath,
      ".".toFieldName,
      Map(
        "$.id".toPactPath -> AvroLong("$.id".toPactPath, "id".toFieldName, 100),
        "$.street".toPactPath -> AvroString("$.street".toPactPath, "street".toFieldName, "NONE"),
        "$.names".toPactPath -> AvroArray(
          "$.names".toPactPath,
          "names".toFieldName,
          List(
            AvroString("$.names".toPactPath, "names".toFieldName, "name-1"),
            AvroString("$.names".toPactPath, "names".toFieldName, "name-2")
          )
        ),
        "$.width".toPactPath -> AvroDouble("$.width".toPactPath, "width".toFieldName, 1.8),
        "$.enabled".toPactPath -> AvroBoolean("$.enabled".toPactPath, "enabled".toFieldName, value = true),
        "$.color".toPactPath -> AvroEnum("$.color".toPactPath, "color".toFieldName, "GREEN"),
        "$.height".toPactPath -> AvroFloat("$.height".toPactPath, "height".toFieldName, 15.8f),
        "$.ages".toPactPath -> AvroMap(
          "$.ages".toPactPath,
          "ages".toFieldName,
          Map(
            "first".toPactPath -> AvroInt("$.ages.first".toPactPath, "first".toFieldName, 2),
            "second".toPactPath -> AvroInt("$.ages.second".toPactPath, "second".toFieldName, 3)
          )
        ),
        "$.no".toPactPath -> AvroInt("$.no".toPactPath, "no".toFieldName, 121),
        "$.md5".toPactPath -> AvroString("$.md5".toPactPath, "md5".toFieldName, "\\\u0000\\\u0001\\\u0002\\\u0003"),
        "$.address".toPactPath -> AvroRecord(
          "$.address".toPactPath,
          "address".toFieldName,
          Map(
            "street".toPactPath -> AvroString("$.address.street".toPactPath, "street".toFieldName, "street name")
          )
        ),
        "$.items".toPactPath -> AvroArray(
          "$.items".toPactPath,
          "items".toFieldName,
          List(
            AvroRecord(
              "$.items".toPactPath,
              "items".toFieldName,
              Map(
                "$.items.name".toPactPath -> AvroString("$.items.name".toPactPath, "name".toFieldName, "Item-1"),
                "$.items.id".toPactPath -> AvroLong("$.items.id".toPactPath, "id".toFieldName, 1)
              )
            ),
            AvroRecord(
              "$.items".toPactPath,
              "items".toFieldName,
              Map(
                "$.items.name".toPactPath -> AvroString("$.items.name".toPactPath, "name".toFieldName, "Item-2"),
                "$.items.id".toPactPath -> AvroLong("$.items.id".toPactPath, "id".toFieldName, 2)
              )
            )
          )
        )
      )
    )
    val bytes = avroRecord.toByteString(schema).value
    content.getContent shouldBe bytes

    interaction.rules should have size 18
  }

  it should "return successful result when comparing to matching content" in {
    val schemaFile = Paths.get(getClass.getResource("/item.avsc").toURI).toFile
    val schema = AvroUtils.parseSchema(schemaFile).value
    val expectedBytes =
      AvroRecord(
        "$".toPactPath,
        ".".toFieldName,
        Map(
          "$.name".toPactPath -> AvroString("$.name".toPactPath, "name".toFieldName, "Item-100"),
          "$.id".toPactPath -> AvroInt("$.id".toPactPath, "id".toFieldName, 100)
        )
      ).toByteString(schema).value
    val actualBytes =
      AvroRecord(
        "$".toPactPath,
        ".".toFieldName,
        Map(
          "$.name".toPactPath -> AvroString("$.name".toPactPath, "name".toFieldName, "Item-42"),
          "$.id".toPactPath -> AvroInt("$.id".toPactPath, "id".toFieldName, 42)
        )
      ).toByteString(schema).value

    val eventualResponse = new PactAvroPluginService()
      .compareContents(
        CompareContentsRequest(
          expected = Some(
            Body(
              contentType = "avro/binary; record=Item",
              content = Some(expectedBytes),
              contentTypeHint = BINARY
            )
          ),
          actual = Some(
            Body(
              contentType = "avro/binary; record=Item",
              content = Some(actualBytes),
              contentTypeHint = BINARY
            )
          ),
          allowUnexpectedKeys = true,
          rules = Map(
            "$.name" -> MatchingRules(
              rule = Seq(
                MatchingRule(
                  `type` = "regex",
                  values = Some(
                    Struct(
                      Map(
                        "regex" -> Value(StringValue("^Item-.*$"))
                      )
                    )
                  )
                )
              )
            ),
            "$.id" -> MatchingRules(
              rule = Seq(
                MatchingRule(
                  `type` = "number",
                  values = Some(Struct())
                )
              )
            )
          ),
          pluginConfiguration = Some(
            PluginConfiguration(
              interactionConfiguration = Some(
                Struct(
                  Map(
                    "record" -> Value(StringValue("Item")),
                    "schemaKey" -> Value(StringValue("someschemakey"))
                  )
                )
              ),
              pactConfiguration = Some(
                Struct(
                  Map(
                    "someschemakey" -> Value(
                      StructValue(
                        Struct(
                          Map(
                            "avroSchema" -> Value(StringValue(schema.toString))
                          )
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )

    val response = eventualResponse.futureValue

    response.results should have size 2
    response.results.get("$.id").value.mismatches should have size 0
    response.results.get("$.name").value.mismatches should have size 0
    response.error should equal("")
  }
}
