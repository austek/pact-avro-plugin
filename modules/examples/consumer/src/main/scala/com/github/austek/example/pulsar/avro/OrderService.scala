package com.github.austek.example.pulsar.avro

import com.github.austek.example.Order
import com.typesafe.scalalogging.StrictLogging

class OrderService extends StrictLogging {

  def process(order: Order): Unit =
    logger.info(s"Processing order ${order.id}")

}
