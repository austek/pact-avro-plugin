package com.collibra.plugin.avro.implicits

import au.com.dius.pact.core.matchers._
import com.collibra.plugin.avro.implicits.PathExpressionImplicits._
import com.collibra.plugin.avro.matchers.BodyItemMatchResult
import com.collibra.plugin.avro.utils.PluginError
import org.apache.avro.Schema
import org.apache.avro.Schema.Type._

import scala.jdk.CollectionConverters._

object SchemaFieldImplicits {

  implicit class SchemaField(field: Schema.Field) {

    def compare(path: List[String], other: Schema.Field)(implicit context: MatchingContext): Either[Seq[PluginError[_]], List[BodyItemMatchResult]] = {
      (field.schema().getType, other.schema().getType) match {
        case (expectedType, actualType) if expectedType == actualType =>
          expectedType match {
            case STRING  => ??? // compareValue(path, expected)
            case INT     => ???
            case LONG    => ???
            case FLOAT   => ???
            case DOUBLE  => ???
            case BOOLEAN => ???
            case ENUM    => ???
            case FIXED   => ???
            case BYTES   => ???
            case NULL    => ???
            case RECORD  => ???
            case ARRAY   => ???
            case MAP     => ???
            case UNION   => ???
            case _ =>
              context.hashCode()
              ???
          }
        case _ =>
          BodyItemMatchResult
            .mismatch(
              field,
              other,
              diff => {
                List(
                  new BodyItemMatchResult(
                    path.constructPath,
                    List(
                      new BodyMismatch(
                        field,
                        other,
                        s"Expected field '${field.name()}' to be type '${field.schema.getType}' but got '${other.schema().getType}'",
                        path.constructPath,
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
