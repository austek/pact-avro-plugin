package com.github.austek.plugin.avro

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import io.pact.plugin.PactPluginHandler

import java.util.UUID
import java.util.UUID.randomUUID
import scala.concurrent.{ExecutionContext, Future}

object PactAvroPluginServer {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("PactAvroPlugin")
    new PactAvroPluginServer(system).run()
    ()
    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }
}

class PactAvroPluginServer(
  system: ActorSystem,
  private val interface: String = "127.0.0.1",
  private val port: Int = PortFinder.findFreePort.getOrElse(9090),
  private val serverKey: UUID = randomUUID()
) {
  private def run(): Future[Http.ServerBinding] = {
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    val service: HttpRequest => Future[HttpResponse] = PactPluginHandler(new PactAvroPluginService())

    val binding: Future[Http.ServerBinding] = Http().newServerAt(interface, port).bind(service)

    binding.foreach(binding => println(s"""{\"port":${binding.localAddress.getPort}, "serverKey":"$serverKey"}"""))

    binding
  }
}
