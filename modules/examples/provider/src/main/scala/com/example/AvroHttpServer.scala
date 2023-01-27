package com.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import fr.davit.akka.http.scaladsl.marshallers.avro.AvroSupport._

import scala.concurrent.ExecutionContext
import scala.io.StdIn
import scala.jdk.CollectionConverters._

object AvroHttpServer {
  private val name = "AvroService"
  val item: Item = Item.newBuilder().setName("thing").setId(42).build()
  val items: Map[Int, Item] = Map(
    41 -> Item.newBuilder().setName("thing 1").setId(41).build(),
    42 -> Item.newBuilder().setName("thing 2").setId(42).build(),
    43 -> Item.newBuilder().setName("thing 3").setId(43).build(),
    44 -> Item.newBuilder().setName("thing 4").setId(44).build()
  )

  val routes: Route =
    get {
      pathSingleSlash {
        complete(items.head._2)
      } ~
        path("items" / IntNumber) { id: Int =>
          items.get(id) match {
            case Some(item) => complete(item)
            case None       => complete(StatusCodes.NotFound)
          }
        }
    } ~ post {
      path("orders") {
        entity(as[Order]) { order =>
          val itemsCount = order.getItems.size
          val itemNames = order.getItems.asScala.map(_.getName).mkString(", ")
          complete(
            StatusCodes.Created,
            s"Ordered $itemsCount items: $itemNames"
          )
        }
      }
    }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem(name)
    implicit val ec: ExecutionContext = system.dispatcher

    val binding = Http().newServerAt("localhost", 8080).bind(routes)

    binding.foreach(binding => println(s"$name bound to: ${binding.localAddress}"))

    StdIn.readLine()
    binding
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
