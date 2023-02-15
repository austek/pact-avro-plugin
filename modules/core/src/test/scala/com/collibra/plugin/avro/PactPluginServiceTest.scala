package com.collibra.plugin.avro

import com.collibra.plugin.avro.utils.AvroUtils
import com.google.protobuf.ByteString
import com.google.protobuf.struct.{Struct, Value}
import io.pact.plugin._
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues}

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
                "pact:content-type" -> Value(Value.Kind.StringValue("application/avro"))
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
                "pact:avro" -> Value(Value.Kind.StringValue("non-existing.avsc"))
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
                "pact:avro" -> Value(Value.Kind.StringValue(schemasPath))
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
                "pact:avro" -> Value(Value.Kind.StringValue(schemasPath))
              )
            )
          )
        )
      )
      .map { response =>
        response.error shouldBe "Config item with key 'pact:record-name' and record-name of the payload is required"
      }
  }

  it should "return Interaction Response" in {
    val url = getClass.getResource("/item.avsc")
    val eventualResponse = new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "avro/binary",
          Some(
            Struct(
              Map(
                "pact:avro" -> Value(Value.Kind.StringValue(url.getPath)),
                "pact:record-name" -> Value(Value.Kind.StringValue("Item")),
                "pact:content-type" -> Value(Value.Kind.StringValue("avro/binary")),
                "name" -> Value(Value.Kind.StringValue("notEmpty('Item-41')")),
                "id" -> Value(Value.Kind.StringValue("notEmpty('41')"))
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

    val schema = AvroUtils.parseSchema(url.getPath).value
    val bytes = toByteString(schema, Map("name" -> "Item-41", "id" -> 41)).value
    content.getContent shouldBe bytes

    interaction.rules should have size 2
  }

  private def toByteString(schema: Schema, fields: Map[String, Any]): Option[ByteString] = {
    val record = new GenericData.Record(schema)
    fields.foreach(v => record.put(v._1, v._2))
    AvroUtils.schemaToByteString(schema, record)
  }
}
