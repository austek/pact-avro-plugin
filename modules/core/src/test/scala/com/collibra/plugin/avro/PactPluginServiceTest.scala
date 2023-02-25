package com.collibra.plugin.avro

import com.collibra.plugin.avro.utils.AvroUtils
import com.collibra.plugin.avro.utils.StringUtils._
import com.google.protobuf.struct.Value.Kind._
import com.google.protobuf.struct.{Struct, Value, ListValue => StructListValue}
import io.pact.plugin._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues}

import java.nio.file.Path
import scala.jdk.CollectionConverters._

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
        response.error shouldBe "non-existing.avsc (No such file or directory)"
      }
  }

  it should "require a valid avro schema file" in {
    val schemasPath = getClass.getResource("/invalid.avsc").getPath
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
        response.error shouldBe "Type not supported: invalid"
      }
  }

  it should "require record-name to be defined" in {
    val schemasPath = getClass.getResource("/schemas.avsc").getPath
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
    val url = getClass.getResource("/item.avsc")
    val eventualResponse = new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "avro/binary",
          Some(
            Struct(
              Map(
                "pact:avro" -> Value(StringValue(url.getPath)),
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

    val schema = AvroUtils.parseSchema(Path.of(url.getPath).toFile).value
    val bytes = AvroRecord
      .toByteString(
        schema,
        AvroRecord(
          "$".toPactPath,
          ".".toFieldName,
          Map(
            "$.name".toPactPath -> AvroString("$.name".toPactPath, "name".toFieldName, "Item-41"),
            "$.id".toPactPath -> AvroInt("$.id".toPactPath, "id".toFieldName, 41)
          )
        )
      )
      .value
    content.getContent shouldBe bytes

    interaction.rules should have size 2
  }

  it should "return Interaction Response for a record with other record in field" in {
    val url = getClass.getResource("/schemas.avsc")
    val eventualResponse = new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "avro/binary",
          Some(
            Struct(
              Map(
                "pact:avro" -> Value(StringValue(url.getPath)),
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
                "color" -> Value(StringValue("matching(equalTo, 'GREEN')"))
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
    val schema = AvroUtils.parseSchema(Path.of(url.getPath).toFile).value.getTypes.asScala.find(_.getName == "Complex").get
    val avroRecord = AvroRecord(
      "$".toPactPath,
      ".".toFieldName,
      Map(
        "$.id".toPactPath -> AvroLong("$.id".toPactPath, "id".toFieldName, 100),
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
        "$.no".toPactPath -> AvroInt("$.no".toPactPath, "no".toFieldName, 121)
      )
    )
    val bytes = AvroRecord.toByteString(schema, avroRecord).value
    content.getContent shouldBe bytes

    interaction.rules should have size 9
  }
}
