package com.github.austek.example

import com.typesafe.config.{Config, ConfigFactory}

package object config {
  private val config: Config = ConfigFactory.load()
  private val orderProducerConfig: Config = config.getConfig("subscription")
  val consumerAppConfig: ConsumerAppConfig = ConsumerAppConfig(
    config.getString("pulsar-url"),
    SubscriptionConfig(
      orderProducerConfig.getString("name"),
      orderProducerConfig.getString("topic"),
      Option(orderProducerConfig.getString("consumer-name"))
    )
  )
}
