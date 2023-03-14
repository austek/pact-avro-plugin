package com.collibra.example

import com.collibra.example.config.ConsumerAppConfig
import com.collibra.example.pulsar.avro.OrderConsumerController
import com.typesafe.scalalogging.StrictLogging
import pureconfig.ConfigSource
import pureconfig.generic.auto._
object OrderConsumerApplication extends App with StrictLogging {

  private val config: ConsumerAppConfig = ConfigSource.default.load[ConsumerAppConfig].getOrElse(throw new Exception("Configuration couldn't be loaded"))
  private val controller = new OrderConsumerController(config)

  private val count: Int = controller.processOrderMessages(0, () => true)

  logger.info(s"Processed $count orders")

}
