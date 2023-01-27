package com.github.austek.example

import com.github.austek.example.config.ProviderAppConfig
import com.github.austek.example.pulsar.avro.OrderProducerController
import pureconfig.ConfigSource
import pureconfig.generic.auto._

object OrderProviderApplication {

  private val config: ProviderAppConfig = ConfigSource.default.loadOrThrow[ProviderAppConfig]

  private val controller = new OrderProducerController(config)

  sys.addShutdownHook {
    println("Closing producer and pulsar client..")
    controller.close()
  }

  def main(args: Array[String]): Unit = {
    controller.producerOrders(config.orderProducer.numberOfOrders)
    ()
  }
}
