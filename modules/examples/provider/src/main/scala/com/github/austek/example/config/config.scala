package com.github.austek.example

import com.typesafe.config.{Config, ConfigFactory}

package object config {
  private val config: Config = ConfigFactory.load()
  private val orderProducerConfig: Config = config.getConfig("order-producer")
  val providerAppConfig: ProviderAppConfig = ProviderAppConfig(
    config.getString("pulsar-url"),
    OrderProducerConfig(
      orderProducerConfig.getString("topic"),
      Option(orderProducerConfig.getString("producer-name")),
      Option(orderProducerConfig.getBoolean("enable-batching")),
      Option(orderProducerConfig.getBoolean("block-if-queue-full")),
      Option(orderProducerConfig.getInt("number-of-orders")).getOrElse(5)
    )
  )
}
