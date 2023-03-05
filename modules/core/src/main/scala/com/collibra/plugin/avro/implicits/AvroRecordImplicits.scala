package com.collibra.plugin.avro.implicits

import au.com.dius.pact.core.matchers._
import com.collibra.plugin.avro.implicits.PathExpressionImplicits._
import com.collibra.plugin.avro.implicits.SchemaFieldImplicits._
import com.collibra.plugin.avro.matchers.BodyItemMatchResult
import com.collibra.plugin.avro.utils._
import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.generic.{GenericDatumWriter, GenericRecord}
import org.apache.avro.io.EncoderFactory

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import scala.util._

object AvroRecordImplicits extends StrictLogging {

  implicit class RichAvroRecord(genericRecord: GenericRecord) {

    def toJson: Either[PluginErrorException, String] = {
      Using(new ByteArrayOutputStream()) { outputStream =>
        val writer = new GenericDatumWriter[GenericRecord](genericRecord.getSchema)
        val encoder = EncoderFactory.get().jsonEncoder(genericRecord.getSchema, outputStream, true)
        writer.write(genericRecord, encoder)
        encoder.flush()

        outputStream.toString(StandardCharsets.UTF_8)
      } match {
        case Success(value)     => Right(value)
        case Failure(exception) => Left(PluginErrorException(exception))
      }
    }

    def diff(other: GenericRecord): Either[PluginErrorException, String] = {
      for {
        genericRecordJson <- genericRecord.toJson
        otherJson <- other.toJson
        result <- Right(DiffUtilsKt.generateDiff(genericRecordJson, otherJson).asScala.mkString("\n"))
      } yield result
    }

    def toByteArray: Array[Byte] = {
      val outputStream = new ByteArrayOutputStream()
      val writer = new GenericDatumWriter[GenericRecord](genericRecord.getSchema)
      val encoder = EncoderFactory.get().binaryEncoder(outputStream, null)
      writer.write(genericRecord, encoder)
      encoder.flush()
      outputStream.toByteArray
    }

    def compare(
      path: List[String],
      other: GenericRecord
    )(implicit context: MatchingContext): Either[Seq[PluginError[_]], List[BodyItemMatchResult]] = {
      logger.debug(s">>> compareMessage($path, $genericRecord, $other)")
      if (genericRecord.getSchema.getName == other.getSchema.getName) {
        genericRecord.getSchema.getFields.asScala
          .map { field =>
            val fieldPath = path :+ field.name
            Try(other.getSchema.getField(field.name())) match {
              case Success(otherField) => field.compare(fieldPath, otherField)
              case Failure(_) =>
                BodyItemMatchResult
                  .mismatch(
                    genericRecord,
                    other,
                    diff => {
                      List(
                        new BodyItemMatchResult(
                          path.head,
                          List(
                            new BodyMismatch(
                              field.name,
                              null,
                              s"genericRecord field '${field.name}' but was missing",
                              fieldPath.constructPath,
                              diff
                            )
                          ).asJava
                        )
                      )
                    }
                  )
                  .left
                  .map(e => Seq(e))
            }
          }
          .partitionMap(identity) match {
          case (errors, _) if errors.nonEmpty => Left(errors.toSeq.flatten)
          case (_, result)                    => Right(result.flatten.toList)
        }
      } else {
        BodyItemMatchResult
          .mismatch(
            genericRecord,
            other,
            diff => {
              List(
                new BodyItemMatchResult(
                  path.head,
                  List(
                    new BodyMismatch(
                      genericRecord,
                      other,
                      s"Expected record '${genericRecord.getSchema.getName}' but got '${other.getSchema.getName}'",
                      "/",
                      diff
                    )
                  ).asJava
                )
              )
            }
          )
          .left
          .map(e => Seq(e))
      }
    }
  }

}
