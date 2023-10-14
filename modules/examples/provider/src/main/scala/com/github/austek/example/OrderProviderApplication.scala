package com.github.austek.example

import com.github.austek.example.config.providerAppConfig
import com.github.austek.example.pulsar.avro.OrderProducerController

object OrderProviderApplication {

  private val controller = new OrderProducerController(providerAppConfig)

  def main(args: Array[String]): Unit = {
    val _ = sys.addShutdownHook {
      println("Closing producer and pulsar client..")
      controller.close()
    }
    val _ = controller.producerOrders(providerAppConfig.orderProducer.numberOfOrders)
  }
}
