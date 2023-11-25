package com.github.austek.example.pulsar.avro

import com.github.austek.example.Order
import com.github.austek.example.config.ConsumerAppConfig
import com.sksamuel.pulsar4s.*
import com.typesafe.scalalogging.StrictLogging
import org.apache.pulsar.client.api.{Schema, SubscriptionInitialPosition, SubscriptionType}

import scala.annotation.tailrec
import scala.util.{Failure, Success}

class OrderConsumerController(config: ConsumerAppConfig) extends StrictLogging {

  private val orderService = new OrderService()
  private val pulsarClient = PulsarClient(config.pulsarUrl)
  private implicit val schema: Schema[Order] = Schema.AVRO(classOf[Order])
  private val topic = Topic(config.subscription.topic)

  private val consumerConfig = ConsumerConfig(
    Subscription(config.subscription.name),
    Seq(topic),
    consumerName = config.subscription.consumerName,
    subscriptionInitialPosition = Some(SubscriptionInitialPosition.Earliest),
    subscriptionType = Some(SubscriptionType.Exclusive)
  )

  private val consumerFn = pulsarClient.consumer[Order](consumerConfig)

  @tailrec
  final def processOrderMessages(totalMessageCount: Int, f: () => Boolean): Int = {
    if (!f()) {
      totalMessageCount
    } else {
      consumerFn.receive match {
        case Success(message) =>
          orderService.process(message.value)
          logger.info(s"Total Messages '$totalMessageCount' - Acked Message: ${message.messageId}")
          consumerFn.acknowledge(message.messageId)
        case Failure(exception) =>
          logger.info(s"Failed to receive message: ${exception.getMessage}")
      }
      processOrderMessages(totalMessageCount + 1, f)
    }
  }

}
