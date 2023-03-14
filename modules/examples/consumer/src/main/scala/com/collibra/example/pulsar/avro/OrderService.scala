package com.collibra.example.pulsar.avro

import com.collibra.example.Order
import com.typesafe.scalalogging.StrictLogging

class OrderService extends StrictLogging {

  def process(order: Order): Unit =
    logger.info(s"Processing order ${order.id}")

}
