package com.collibra.plugin.avro.implicits

import au.com.dius.pact.core.support.{Either => PactEither, Result}

import scala.util.{Failure, Success, Try}

object AvroSupportImplicits {
  implicit def fromPactEither[A, B](pactEither: PactEither[A, B]): Either[A, B] = {
    Try(pactEither.unwrapB("")) match {
      case Success(value) => Right(value)
      case Failure(_) =>
        Try(pactEither.unwrapA("")) match {
          case Failure(exception) => throw exception
          case Success(value)     => Left(value)
        }
    }
  }
  implicit def fromPactResult[A, B](result: Result[A, B]): Either[B, A] =
    Option(result.get()) match {
      case Some(value) => Right(value)
      case None        => Left(result.errorValue())
    }
}
