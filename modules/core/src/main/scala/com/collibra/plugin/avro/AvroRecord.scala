package com.collibra.plugin.avro

import au.com.dius.pact.core.model.PathExpressionsKt._
import au.com.dius.pact.core.model.matchingrules.{MatchingRule, MatchingRuleCategory}
import com.collibra.plugin.RuleParser.parseRules
import com.collibra.plugin.avro.GenericRecord
import com.collibra.plugin.avro.utils.StringUtils._
import com.collibra.plugin.avro.utils._
import com.google.protobuf.ByteString
import com.google.protobuf.struct.Value
import com.google.protobuf.struct.Value.Kind
import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.Schema
import org.apache.avro.Schema.Type._
import org.apache.avro.generic._
import org.apache.avro.io.EncoderFactory

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try, Using}

case class AvroFieldName(value: String)
case class PactFieldPath(value: String) {
  def subPathOf(path: String): PactFieldPath = PactFieldPath(constructValidPath(path, value))
}

sealed trait AvroValue {

  type AvroValueType
  def path: PactFieldPath
  def name: AvroFieldName
  def value: AvroValueType
  def rules: Seq[MatchingRule]

  def addRules(matchingRules: MatchingRuleCategory): Unit = matchingRules.addRules(path.value, rules.asJava)
}

object AvroValue extends StrictLogging {

  def apply(
    rootPath: PactFieldPath,
    fieldName: AvroFieldName,
    schema: Schema,
    inValue: Value
  ): Either[Seq[PluginError[_]], AvroValue] = {
    val path = rootPath.subPathOf(fieldName.value)
    logger.debug(s">>> buildFieldValue($path, $fieldName, $inValue)")
    val schemaType = schema.getType match {
      case ARRAY => schema.getElementType.getType
      case MAP   => schema.getValueType.getType
      case _     => schema.getType
    }
    inValue.kind match {
      case Kind.Empty        => Right(AvroNull(path, fieldName))
      case Kind.NullValue(_) => Right(AvroNull(path, fieldName))
      case Kind.StringValue(_) =>
        parseRules(inValue)
          .flatMap { case (fieldValue, rules) =>
            AvroValue(path, fieldName, schemaType, fieldValue, rules)
          }
          .left
          .map(e => Seq(e))
      case Kind.ListValue(listValue) =>
        (listValue.values.map(parseRules).partitionMap(identity) match {
          case (errors, _) if errors.nonEmpty => Left(errors)
          case (_, result)                    => Right(result)
        }).map { result =>
          result.foldLeft((Seq.empty[String], Seq.empty[MatchingRule])) { (acc, values) =>
            (acc._1 :+ values._1) -> (acc._2 ++ values._2)
          }
        }.map { case (fieldValues, rules) =>
          AvroValue(path, fieldName, schemaType, fieldValues, rules)
        } match {
          case Right(Right(value)) => Right(value)
          case Right(Left(error))  => Left(Seq(error))
          case Left(errors)        => Left(errors)
        }
      case Kind.NumberValue(_) => Left(Seq(PluginErrorMessage(s"Number kind value for field is not supported")))
      case Kind.BoolValue(_)   => Left(Seq(PluginErrorMessage(s"Bool kind value for field is not supported")))
      case Kind.StructValue(_) =>
        if (schemaType == RECORD) {
          AvroRecord(path, fieldName, schema.getElementType, inValue.getStructValue.fields)
        } else {
          Left(Seq(PluginErrorMessage(s"Struct kind value for field is not supported")))
        }
    }
  }
  def apply(
    path: PactFieldPath,
    fieldName: AvroFieldName,
    schemaType: Schema.Type,
    fieldValue: Object,
    rules: Seq[MatchingRule]
  ): Either[PluginError[_], AvroValue] = {
    fieldValue match {
      case value: String => fromString(path, fieldName, schemaType, value, rules)
      case _ =>
        (schemaType match {
          case BOOLEAN => Try(AvroBoolean(path, fieldName, fieldValue.asInstanceOf[Boolean], rules)).toEither
          case BYTES =>
            Right(
              AvroString(path, fieldName, new String(fieldValue.asInstanceOf[Array[Byte]], StandardCharsets.UTF_8), rules)
            ) // TODO: Use bytes, Using String values for now
          case DOUBLE => Try(AvroDouble(path, fieldName, fieldValue.asInstanceOf[Double], rules)).toEither
          case ENUM   => Try(AvroEnum(path, fieldName, fieldValue.asInstanceOf[String])).toEither
          case FIXED =>
            Right(
              AvroString(path, fieldName, new String(fieldValue.asInstanceOf[Array[Byte]], StandardCharsets.UTF_8), rules)
            ) // TODO: Use bytes, Using String values for now
          case FLOAT  => Try(AvroFloat(path, fieldName, fieldValue.asInstanceOf[Float], rules)).toEither
          case INT    => Try(AvroInt(path, fieldName, fieldValue.asInstanceOf[Int], rules)).toEither
          case LONG   => Try(AvroLong(path, fieldName, fieldValue.asInstanceOf[Long], rules)).toEither
          case NULL   => Right(AvroNull(path, fieldName))
          case STRING => Try(AvroString(path, fieldName, fieldValue.asInstanceOf[String], rules)).toEither
          case ARRAY  => Left(new UnsupportedOperationException(s"'ARRAY' not support as AvroValue: '$fieldValue'"))
          case MAP    => Left(new UnsupportedOperationException(s"'MAP' not support as AvroValue: '$fieldValue'"))
          case RECORD => Left(new UnsupportedOperationException(s"'RECORD' not support as AvroValue: '$fieldValue'"))
          case UNION  => Left(new UnsupportedOperationException(s"'UNION' not support as AvroValue: '$fieldValue'"))
          case t      => Left(new UnsupportedOperationException(s"Unknown type '$t' not support as AvroValue: '$fieldValue'"))
        }).left.map(e => PluginErrorException(e))
    }
  }

  private def fromString(
    path: PactFieldPath,
    fieldName: AvroFieldName,
    schemaType: Schema.Type,
    fieldValue: String,
    rules: Seq[MatchingRule]
  ): Either[PluginError[_], AvroValue] = {
    (schemaType match {
      case BOOLEAN => Right(AvroBoolean(path, fieldName, fieldValue.toLowerCase == "true", rules))
      case BYTES   => Right(AvroString(path, fieldName, fieldValue, rules)) // TODO: Use bytes, Using String values for now
      case DOUBLE  => Try(BigDecimal(fieldValue.toDouble)).map(v => AvroDouble(path, fieldName, v, rules)).toEither
      case ENUM    => Right(AvroEnum(path, fieldName, fieldValue, rules))
      case FIXED   => Right(AvroString(path, fieldName, fieldValue, rules)) // TODO: Use bytes, Using String values for now
      case FLOAT   => Try(fieldValue.toFloat).map(v => AvroFloat(path, fieldName, v, rules)).toEither
      case INT     => Try(fieldValue.toInt).map(v => AvroInt(path, fieldName, v, rules)).toEither
      case LONG    => Try(fieldValue.toLong).map(v => AvroLong(path, fieldName, v, rules)).toEither
      case NULL    => Right(AvroNull(path, fieldName))
      case STRING  => Right(AvroString(path, fieldName, fieldValue, rules))
      case ARRAY   => Left(new UnsupportedOperationException(s"'ARRAY' not support as AvroValue: '$fieldValue'"))
      case MAP     => Left(new UnsupportedOperationException(s"'MAP' not support as AvroValue: '$fieldValue'"))
      case RECORD  => Left(new UnsupportedOperationException(s"'RECORD' not support as AvroValue: '$fieldValue'"))
      case UNION   => Left(new UnsupportedOperationException(s"'UNION' not support as AvroValue: '$fieldValue'"))
      case t       => Left(new UnsupportedOperationException(s"Unknown type '$t' not support as AvroValue: '$fieldValue'"))
    }).left.map(e => PluginErrorException(e))
  }
}

case class AvroNull(override val path: PactFieldPath, override val name: AvroFieldName, override val value: Unit = (), rules: Seq[MatchingRule] = Seq.empty)
    extends AvroValue {
  override type AvroValueType = Unit
}
case class AvroString(override val path: PactFieldPath, override val name: AvroFieldName, override val value: String, rules: Seq[MatchingRule] = Seq.empty)
    extends AvroValue {
  override type AvroValueType = String
}

case class AvroEnum(override val path: PactFieldPath, override val name: AvroFieldName, override val value: String, rules: Seq[MatchingRule] = Seq.empty)
    extends AvroValue {
  override type AvroValueType = String
}
case class AvroDouble(override val path: PactFieldPath, override val name: AvroFieldName, value: BigDecimal, rules: Seq[MatchingRule] = Seq.empty)
    extends AvroValue {
  override type AvroValueType = BigDecimal
}
case class AvroFloat(override val path: PactFieldPath, override val name: AvroFieldName, value: Float, rules: Seq[MatchingRule] = Seq.empty) extends AvroValue {
  override type AvroValueType = Float
}
case class AvroInt(override val path: PactFieldPath, override val name: AvroFieldName, value: Int, rules: Seq[MatchingRule] = Seq.empty) extends AvroValue {
  override type AvroValueType = Int
}
case class AvroLong(override val path: PactFieldPath, override val name: AvroFieldName, value: Long, rules: Seq[MatchingRule] = Seq.empty) extends AvroValue {
  override type AvroValueType = Long
}
case class AvroBoolean(override val path: PactFieldPath, override val name: AvroFieldName, value: Boolean, rules: Seq[MatchingRule] = Seq.empty)
    extends AvroValue {
  override type AvroValueType = Boolean
}
case class AvroArray(
  override val path: PactFieldPath,
  override val name: AvroFieldName,
  value: List[AvroValue] = List.empty,
  rules: Seq[MatchingRule] = Seq.empty
) extends AvroValue {
  override type AvroValueType = List[AvroValue]
  def toRecordValue: util.List[Any] = value.map {
    case a: AvroArray  => a.toRecordValue
    case m: AvroMap    => m.toRecordValue
    case r: AvroRecord => r
    case v             => v.value
  }.asJava

  override def addRules(matchingRules: MatchingRuleCategory): Unit =
    value.foreach(_.addRules(matchingRules))
}

object AvroArray {
  def apply(rootPath: PactFieldPath, schemaField: Schema.Field, inValue: Value): Either[Seq[PluginError[_]], AvroArray] = {
    val fieldName = AvroFieldName(schemaField.name())
    inValue.kind match {
      case Kind.Empty        => Right(AvroArray(rootPath, fieldName))
      case Kind.NullValue(_) => Right(AvroArray(rootPath, fieldName))
      case Kind.ListValue(listValue) =>
        listValue.values
          .map { singleValue =>
            AvroValue(rootPath, fieldName, schemaField.schema(), singleValue)
          }
          .partitionMap(identity) match {
          case (errors, _) if errors.nonEmpty => Left(errors.flatten)
          case (_, fields)                    => Right(AvroArray(rootPath.subPathOf(fieldName.value), fieldName, fields.toList))
        }
      case _ => Left(Seq(PluginErrorMessage(s"Expected list value for field '${fieldName.value}' but got '${inValue.kind}'")))
    }
  }
}

case class AvroMap(
  override val path: PactFieldPath,
  override val name: AvroFieldName,
  value: Map[PactFieldPath, AvroValue] = Map.empty,
  rules: Seq[MatchingRule] = Seq.empty
) extends AvroValue {
  override type AvroValueType = Map[PactFieldPath, AvroValue]

  def toRecordValue: util.Map[String, Any] = value.map { case (_, v) =>
    v match {
      case a: AvroArray  => a.name.value -> a.toRecordValue
      case m: AvroMap    => m.name.value -> m.toRecordValue
      case r: AvroRecord => r.name.value -> r
      case av            => av.name.value -> av.value
    }
  }.asJava

  override def addRules(matchingRules: MatchingRuleCategory): Unit =
    value.foreach { case (_, field) =>
      field.addRules(matchingRules)
    }
}

object AvroMap {
  def apply(rootPath: PactFieldPath, schemaField: Schema.Field, inValue: Value): Either[Seq[PluginError[_]], AvroMap] = {
    val fieldName = AvroFieldName(schemaField.name())
    inValue.kind match {
      case Kind.Empty        => Right(AvroMap(rootPath, fieldName))
      case Kind.NullValue(_) => Right(AvroMap(rootPath, fieldName))
      case Kind.StructValue(structValue) =>
        structValue.fields
          .map { case (key, singleValue) =>
            val either = AvroValue(rootPath, key.toFieldName, schemaField.schema(), singleValue)
            either.map { v =>
              key.toPactPath -> v
            }
          }
          .partitionMap(identity) match {
          case (errors, _) if errors.nonEmpty => Left(errors.toSeq.flatten)
          case (_, fields)                    => Right(AvroMap(rootPath, fieldName, fields.toMap))
        }
      case _ => Left(Seq(PluginErrorMessage(s"Expected map value for field '${fieldName.value}' but got '${inValue.kind}'")))
    }
  }
}

case class AvroRecord(
  override val path: PactFieldPath,
  override val name: AvroFieldName,
  override val value: Map[PactFieldPath, AvroValue],
  rules: Seq[MatchingRule] = Seq.empty
) extends AvroValue {
  override type AvroValueType = Map[PactFieldPath, AvroValue]

  override def addRules(matchingRules: MatchingRuleCategory): Unit =
    value.foreach { case (_, avroValue) =>
      avroValue.addRules(matchingRules)
    }

  def toRecordValue: util.Map[String, Any] = value.map { case (_, v) =>
    v match {
      case a: AvroArray  => a.name.value -> a.toRecordValue
      case m: AvroMap    => m.name.value -> m.toRecordValue
      case r: AvroRecord => r.name.value -> r
      case n: AvroNull   => n.name.value -> null
      case av            => av.name.value -> av.value
    }
  }.asJava

  def toByteString(schema: Schema): Either[PluginErrorException, ByteString] = {
    val datumWriter = new GenericDatumWriter[GenericRecord](schema)
    Using(new ByteArrayOutputStream()) { os =>
      val encoder = EncoderFactory.get.binaryEncoder(os, null)
      val record = GenericRecord(schema, this)
      datumWriter.write(record, encoder)
      encoder.flush()
      os.toByteArray
    } match {
      case Success(bytes)     => Right(ByteString.copyFrom(bytes))
      case Failure(exception) => Left(PluginErrorException(exception))
    }
  }
}

object AvroRecord {

  def apply(rootPath: PactFieldPath, fieldName: AvroFieldName, fields: Seq[AvroValue]): AvroRecord =
    AvroRecord(rootPath, fieldName, fields.map(field => field.path -> field).toMap)

  def apply(schema: Schema, configFields: Map[String, Value]): Either[Seq[PluginError[_]], AvroRecord] =
    this("$".toPactPath, ".".toFieldName, schema, configFields)

  def apply(rootPath: PactFieldPath, fieldName: AvroFieldName, schema: Schema, configFields: Map[String, Value]): Either[Seq[PluginError[_]], AvroRecord] = {
    schema.getFields.asScala.toSeq
      .map { schemaField =>
        val fieldName = AvroFieldName(schemaField.name())
        configFields.get(schemaField.name()) match {
          case Some(configValue) =>
            schemaField.schema().getType match {
              case STRING  => AvroValue(rootPath, fieldName, schemaField.schema(), configValue)
              case INT     => AvroValue(rootPath, fieldName, schemaField.schema(), configValue)
              case LONG    => AvroValue(rootPath, fieldName, schemaField.schema(), configValue)
              case FLOAT   => AvroValue(rootPath, fieldName, schemaField.schema(), configValue)
              case DOUBLE  => AvroValue(rootPath, fieldName, schemaField.schema(), configValue)
              case BOOLEAN => AvroValue(rootPath, fieldName, schemaField.schema(), configValue)
              case ENUM    => AvroValue(rootPath, fieldName, schemaField.schema(), configValue)
              case FIXED   => AvroValue(rootPath, fieldName, schemaField.schema(), configValue)
              case BYTES   => AvroValue(rootPath, fieldName, schemaField.schema(), configValue)
              case NULL    => AvroValue(rootPath, fieldName, schemaField.schema(), configValue)
              case RECORD =>
                val recordPath = rootPath.subPathOf(schemaField.name())
                AvroRecord(recordPath, fieldName, schemaField.schema(), configValue.getStructValue.fields)
              case ARRAY => AvroArray(rootPath, schemaField, configValue)
              case MAP =>
                val mapPath = rootPath.subPathOf(schemaField.name())
                AvroMap(mapPath, schemaField, configValue)
              case UNION => Left(Seq(PluginErrorException(new UnsupportedOperationException("'UNION' not support as AvroValue"))))
              case t     => Left(Seq(PluginErrorException(new UnsupportedOperationException(s"Unknown type '$t' not support as AvroValue"))))
            }
          case None =>
            if (schemaField.hasDefaultValue) {
              AvroValue(rootPath.subPathOf(fieldName.value), fieldName, schemaField.schema().getType, schemaField.defaultVal(), Seq.empty).left.map(e => Seq(e))
            } else if (schemaField.schema().getType == UNION && schemaField.schema().getTypes.asScala.exists(_.getType == NULL)) {
              Right(AvroNull(rootPath.subPathOf(fieldName.value), fieldName))
            } else {
              Left(Seq(PluginErrorException(new Exception(s"Couldn't find configuration for field: ${schemaField.name()}"))))
            }
        }
      }
      .partitionMap(identity) match {
      case (errors, _) if errors.nonEmpty => Left(errors.flatten)
      case (_, value)                     => Right(this(rootPath, fieldName, value))
    }
  }

}
