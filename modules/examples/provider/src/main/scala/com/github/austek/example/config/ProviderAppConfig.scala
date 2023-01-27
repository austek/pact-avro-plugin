package com.github.austek.example.config

final case class OrderProducerConfig(
  topic: String,
  name: Option[String],
  enableBatching: Option[Boolean],
  blockIfQueueFull: Option[Boolean],
  numberOfOrders: Int = 5
)
final case class ProviderAppConfig(pulsarUrl: String, orderProducer: OrderProducerConfig)
