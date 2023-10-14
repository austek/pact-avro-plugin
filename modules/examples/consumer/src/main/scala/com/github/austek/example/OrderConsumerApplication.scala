package com.github.austek.example

import com.github.austek.example.config.consumerAppConfig
import com.github.austek.example.pulsar.avro.OrderConsumerController
import com.typesafe.scalalogging.StrictLogging

object OrderConsumerApplication extends App with StrictLogging {

  private val controller = new OrderConsumerController(consumerAppConfig)

  private val count: Int = controller.processOrderMessages(0, () => true)

  logger.info(s"Processed $count orders")

}
