package com.example

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{MessageEntity, StatusCodes}
import akka.http.scaladsl.server.Route.seal
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.example.AvroHttpServer._
import fr.davit.akka.http.scaladsl.marshallers.avro.AvroBinarySupport._
import fr.davit.akka.http.scaladsl.marshallers.avro.AvroProtocol
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

class AvroHttpRouteTest extends AnyFlatSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  "The AvroHttpServer" should "get a single item" in {
    Get().withHeaders(Accept(AvroProtocol.`avro/binary`.mediaType)) ~> routes ~> check {
      contentType shouldBe AvroProtocol.`avro/binary`
      responseAs[Array[Byte]] shouldBe items.head._2.toByteBuffer.array()
    }
  }

  it should "get an item with id" in {
    Get("/items/42").withHeaders(Accept(AvroProtocol.`avro/binary`.mediaType)) ~> routes ~> check {
      contentType shouldBe AvroProtocol.`avro/binary`
      responseAs[Item] shouldBe items(42)
    }
  }

  it should "post an order with avro schema" in {
    val orders = Order
      .newBuilder()
      .setItems(
        List(
          item
        ).asJava
      )
      .build()
    val ordersEntity = Marshal(orders).to[MessageEntity].futureValue

    Post("/orders", ordersEntity) ~> seal(routes) ~> check {
      status shouldBe StatusCodes.Created
    }
  }
}
