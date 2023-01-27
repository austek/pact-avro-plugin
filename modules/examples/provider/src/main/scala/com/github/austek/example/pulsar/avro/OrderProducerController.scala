package com.github.austek.example.pulsar.avro

import OrderRandomization._
import com.github.austek.example.Order
import com.github.austek.example.config.ProviderAppConfig
import com.sksamuel.pulsar4s._
import org.apache.pulsar.client.api.Schema
import util.randomization.Randomization._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OrderProducerController(config: ProviderAppConfig) {

  private val pulsarClient = PulsarClient(config.pulsarUrl)
  implicit val schema: Schema[Order] = Schema.AVRO(classOf[Order])

  private val topic = Topic(config.orderProducer.topic)
  private val eventProducer: Producer[Order] = pulsarClient.producer[Order](
    ProducerConfig(
      topic,
      producerName = config.orderProducer.name,
      enableBatching = config.orderProducer.enableBatching,
      blockIfQueueFull = config.orderProducer.blockIfQueueFull
    )
  )

  def producerOrders(numberOfOrders: Int): Future[IndexedSeq[MessageId]] = {
    Future.sequence((0 until numberOfOrders) map { _ =>
      val sensorEvent = random[Order]
      val message = DefaultProducerMessage(Some(randomUUID.toString), sensorEvent, eventTime = Some(EventTime(System.currentTimeMillis())))
      eventProducer.sendAsync(message)
    })
  }

  def close(): Unit = {
    eventProducer.close()
    pulsarClient.close()
  }

}
