package com.github.austek.plugin.avro

import io.grpc.{Server, ServerBuilder}
import io.pact.plugin.pact_plugin.PactPluginGrpc

import java.util.UUID
import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext
import scala.sys.ShutdownHookThread

object PactAvroPluginServer {
  def main(args: Array[String]): Unit = {
    val server = new PactAvroPluginServer(ExecutionContext.global)
    server.start()
    server.blockUntilShutdown()
  }
}

class PactAvroPluginServer(
  executionContext: ExecutionContext,
  private val port: Int = PortFinder.findFreePort.getOrElse(9090),
  private val serverKey: UUID = randomUUID()
) { self =>

  private[this] var server: Option[Server] = None

  private def start(): ShutdownHookThread = {
    server = Option(
      ServerBuilder
        .forPort(port)
        .addService(PactPluginGrpc.bindService(new PactAvroPluginService(), executionContext))
        .build
        .start
    )
    println(s"""{\"port":$port, "serverKey":"$serverKey"}""")
    sys.addShutdownHook {
      System.err.println("*** shutting down gRPC server since JVM is shutting down")
      self.stop()
      System.err.println("*** server shut down")
    }
  }

  private def stop(): Unit = server.foreach(_.shutdown())

  private def blockUntilShutdown(): Unit = server.foreach(_.awaitTermination())
}
