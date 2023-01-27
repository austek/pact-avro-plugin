package com.collibra.plugin.avro

import com.google.protobuf.struct.{Struct, Value}
import io.pact.plugin.{CatalogueEntry, ConfigureInteractionRequest, InitPluginRequest}
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers

class PactPluginServiceTest extends AsyncFlatSpecLike with Matchers {

  "Avro Plugin" should "initialise" in {
    new PactAvroPluginService()
      .initPlugin(InitPluginRequest("test", "0"))
      .map { response =>
        response.catalogue should have size 3

        val first = response.catalogue.head
        first.`type` shouldBe CatalogueEntry.EntryType.CONTENT_MATCHER
        first.key shouldBe "avro"
        first.values("content-types") shouldBe "application/avro;avro/bytes;avro/binary;application/*+avro"

        val second = response.catalogue(1)
        second.`type` shouldBe CatalogueEntry.EntryType.CONTENT_GENERATOR
        second.key shouldBe "avro"
        second.values("content-types") shouldBe "application/avro;avro/bytes;avro/binary;application/*+avro"

        val last = response.catalogue.last
        last.`type` shouldBe CatalogueEntry.EntryType.TRANSPORT
        last.key shouldBe "avro"
      }
  }

  it should "require configuration" in {
    new PactAvroPluginService()
      .configureInteraction(ConfigureInteractionRequest("text/test"))
      .map { response =>
        response.error shouldBe "Configuration not found"
      }
  }

  it should "require content-type to be defined" in {
    new PactAvroPluginService()
      .configureInteraction(ConfigureInteractionRequest("text/test", Some(Struct(Map("pact:avro" -> Value(Value.Kind.StringValue("schemas.avsc")))))))
      .map { response =>
        response.error shouldBe "Config item with key 'pact:content-type' and content-type of the payload is required"
      }
  }

  it should "require avro content-type" in {
    new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "text/test",
          Some(
            Struct(
              Map(
                "pact:content-type" -> Value(Value.Kind.StringValue("application/json"))
              )
            )
          )
        )
      )
      .map { response =>
        response.error shouldBe "Provided content-type 'application/json' is not supported by this plugin"
      }
  }

  it should "require path to the avro file" in {
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
        response.error shouldBe "Config item with key 'pact:avro' and path to the avro file is required"
      }
  }

  it should "require path to the avro file that exists" in {
    new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "text/test",
          Some(
            Struct(
              Map(
                "pact:avro" -> Value(Value.Kind.StringValue("non-existing.avsc")),
                "pact:content-type" -> Value(Value.Kind.StringValue("application/avro"))
              )
            )
          )
        )
      )
      .map { response =>
        response.error shouldBe "non-existing.avsc (No such file or directory)"
      }
  }

  it should "require a valid avro file" in {
    val url = getClass.getResource("/invalid.avsc")
    new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "text/test",
          Some(
            Struct(
              Map(
                "pact:avro" -> Value(Value.Kind.StringValue(url.getPath)),
                "pact:content-type" -> Value(Value.Kind.StringValue("application/avro"))
              )
            )
          )
        )
      )
      .map { response =>
        response.error shouldBe "Type not supported: invalid"
      }
  }

  it should "return Interaction Response" in {
    val url = getClass.getResource("/schemas.avsc")
    new PactAvroPluginService()
      .configureInteraction(
        ConfigureInteractionRequest(
          "avro/binary",
          Some(
            Struct(
              Map(
                "pact:avro" -> Value(Value.Kind.StringValue(url.getPath)),
                "pact:content-type" -> Value(Value.Kind.StringValue("application/avro"))
              )
            )
          )
        )
      )
      .map { response =>
        response shouldNot be(null)
      }
  }
}
