package com.example

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Post
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Content-Type`, Accept}
import akka.http.scaladsl.unmarshalling.Unmarshal
import fr.davit.akka.http.scaladsl.marshallers.avro.AvroBinarySupport._
import fr.davit.akka.http.scaladsl.marshallers.avro.AvroProtocol

import scala.concurrent.{ExecutionContextExecutor, Future}

trait AvroHttpClient {
  def getItem: Future[Item]

  def createOrder(order: Order): Future[Done]
}

class AvroHttpClientImpl(baseUri: Uri) extends AvroHttpClient {
  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "SingleRequest")
  implicit val executionContext: ExecutionContextExecutor = system.executionContext
  override def getItem: Future[Item] = {
    Http()
      .singleRequest(
        HttpRequest(uri = baseUri, headers = Seq(Accept(AvroProtocol.`avro/binary`.mediaType)))
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, headers @ _, entity, _) =>
          Unmarshal(entity).to[Item]
        case resp @ HttpResponse(code, _, _, _) =>
          resp.discardEntityBytes()
          throw new Exception(s"Unexpected status: $code")
        case resp =>
          resp.discardEntityBytes()
          throw new Exception("Unknown Response")
      }
  }

  override def createOrder(order: Order): Future[Done] = {
    Post(baseUri).withHeaders(headers = Seq(Accept(AvroProtocol.`avro/binary`.mediaType)))
    Http()
      .singleRequest(
        HttpRequest(
          uri = baseUri,
          method = HttpMethods.POST,
          headers = Seq(Accept(AvroProtocol.`avro/binary`.mediaType), `Content-Type`(AvroProtocol.`avro/binary`))
        )
      )
      .flatMap {
        case HttpResponse(StatusCodes.Created, _, _, _) =>
          Future.successful(Done)
        case resp @ HttpResponse(code, _, _, _) =>
          resp.discardEntityBytes()
          throw new Exception(s"Unexpected status: $code")
        case resp =>
          resp.discardEntityBytes()
          throw new Exception("Unknown Response")
      }
  }
}
