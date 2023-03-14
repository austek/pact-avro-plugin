package com.collibra.example.config

case class SubscriptionConfig(name: String, topic: String, consumerName: Option[String])
case class ConsumerAppConfig(pulsarUrl: String, subscription: SubscriptionConfig)
