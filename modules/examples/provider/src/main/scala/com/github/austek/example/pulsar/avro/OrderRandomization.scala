package com.github.austek.example.pulsar.avro

import com.github.austek.example._
import org.scalacheck.{Arbitrary, Gen}
import util.randomization.Randomization._

import java.nio.ByteBuffer
import java.util.UUID
import scala.jdk.CollectionConverters._

object OrderRandomization {

  implicit def randomBoolean: Arbitrary[Boolean] = Arbitrary(Gen.oneOf(true, false))

  implicit def randomStatus: Arbitrary[Status] = Arbitrary(Gen.oneOf(Status.CREATED, Status.UPDATED, Status.DELETED))

  implicit def randomMailAddress: Arbitrary[MailAddress] = Arbitrary {
    new MailAddress(
      randomInt,
      randomString,
      ByteBuffer.wrap(randomString.getBytes)
    )
  }

  implicit def randomItem: Arbitrary[Item] = Arbitrary {
    new Item(
      randomString,
      randomLong
    )
  }

  implicit def randomOrder: Arbitrary[Order] =
    Arbitrary {
      new Order(
        randomLong,
        randomString,
        random[Boolean],
        randomFloat,
        randomDouble,
        random[Status],
        random[MailAddress],
        random[Item](2).asJava,
        random[UUID]
      )
    }

}
