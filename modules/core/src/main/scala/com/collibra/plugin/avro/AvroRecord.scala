package com.collibra.plugin.avro

import au.com.dius.pact.core.model.PathExpressionsKt._
import au.com.dius.pact.core.model.matchingrules.{MatchingRule, MatchingRuleCategory}
import com.collibra.plugin.RuleParser.parseRules
import com.collibra.plugin.avro.utils.StringUtils._
import com.collibra.plugin.avro.utils._
import com.google.protobuf.ByteString
import com.google.protobuf.struct.Value
import com.google.protobuf.struct.Value.Kind
import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.Schema
import org.apache.avro.Schema.Type._
import org.apache.avro.generic.{GenericData, GenericDatumWriter, GenericRecord}
import org.apache.avro.io.EncoderFactory

import java.io.ByteArrayOutputStream
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
    schemaType: Schema.Type,
    inValue: Value
  ): Either[Seq[PluginError[_]], AvroValue] = {
    val path = rootPath.subPathOf(fieldName.value)
    logger.debug(s">>> buildFieldValue($path, $fieldName, $inValue)")
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
      case Kind.StructValue(_) => Left(Seq(PluginErrorMessage(s"Struct kind value for field is not supported")))
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
          case STRING  => Try(AvroString(path, fieldName, fieldValue.asInstanceOf[String], rules)).toEither
          case INT     => Try(AvroInt(path, fieldName, fieldValue.asInstanceOf[BigInt], rules)).toEither
          case LONG    => Try(AvroLong(path, fieldName, fieldValue.asInstanceOf[Long], rules)).toEither
          case FLOAT   => Try(AvroFloat(path, fieldName, fieldValue.asInstanceOf[Float], rules)).toEither
          case DOUBLE  => Try(AvroDouble(path, fieldName, fieldValue.asInstanceOf[Double], rules)).toEither
          case BOOLEAN => Try(AvroBoolean(path, fieldName, fieldValue.asInstanceOf[Boolean], rules)).toEither
          case ENUM    => Try(AvroEnum(path, fieldName, fieldValue.asInstanceOf[String])).toEither
          case RECORD  => Left(new UnsupportedOperationException("'RECORD' not support as AvroValue"))
          case ARRAY   => Left(new UnsupportedOperationException("'ARRAY' not support as AvroValue"))
          case MAP     => Left(new UnsupportedOperationException("'MAP' not support as AvroValue"))
          case UNION   => Left(new UnsupportedOperationException("'UNION' not support as AvroValue"))
          case FIXED   => Left(new UnsupportedOperationException("'FIXED' not support as AvroValue"))
          case BYTES   => Left(new UnsupportedOperationException("'BYTES' not support as AvroValue"))
          case NULL    => Right(AvroNull(path, fieldName))
          case t       => Left(new UnsupportedOperationException(s"Unknown type '$t' not support as AvroValue"))
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
      case STRING  => Right(AvroString(path, fieldName, fieldValue, rules))
      case INT     => Try(BigInt(fieldValue)).map(v => AvroInt(path, fieldName, v, rules)).toEither
      case LONG    => Try(fieldValue.toLong).map(v => AvroLong(path, fieldName, v, rules)).toEither
      case FLOAT   => Try(fieldValue.toFloat).map(v => AvroFloat(path, fieldName, v, rules)).toEither
      case DOUBLE  => Try(BigDecimal(fieldValue.toDouble)).map(v => AvroDouble(path, fieldName, v, rules)).toEither
      case BOOLEAN => Right(AvroBoolean(path, fieldName, fieldValue.toLowerCase == "true", rules))
      case ENUM    => Right(AvroEnum(path, fieldName, fieldValue, rules))
      case RECORD  => Left(new UnsupportedOperationException("'RECORD' not support as AvroValue"))
      case ARRAY   => Left(new UnsupportedOperationException("'ARRAY' not support as AvroValue"))
      case MAP     => Left(new UnsupportedOperationException("'MAP' not support as AvroValue"))
      case UNION   => Left(new UnsupportedOperationException("'UNION' not support as AvroValue"))
      case FIXED   => Left(new UnsupportedOperationException("'FIXED' not support as AvroValue"))
      case BYTES   => Left(new UnsupportedOperationException("'BYTES' not support as AvroValue"))
      case NULL    => Right(AvroNull(path, fieldName))
      case t       => Left(new UnsupportedOperationException(s"Unknown type '$t' not support as AvroValue"))
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
case class AvroInt(override val path: PactFieldPath, override val name: AvroFieldName, value: BigInt, rules: Seq[MatchingRule] = Seq.empty) extends AvroValue {
  override type AvroValueType = BigInt
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
    case r: AvroRecord => r.toRecordValue
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
            AvroValue(rootPath, fieldName, schemaField.schema().getElementType.getType, singleValue)
          }
          .partitionMap(identity) match {
          case (errors, _) if errors.nonEmpty => Left(errors.flatten)
          case (_, fields)                    => Right(AvroArray(rootPath, fieldName, fields.toList))
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
      case r: AvroRecord => r.name.value -> r.toRecordValue
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
            val either = AvroValue(rootPath, key.toFieldName, schemaField.schema().getValueType.getType, singleValue)
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
  override val value: Map[PactFieldPath, AvroValue] = Map.empty,
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
      case r: AvroRecord => r.name.value -> r.toRecordValue
      case av            => av.name.value -> av.value
    }
  }.asJava
}

object AvroRecord {

  def apply(rootPath: PactFieldPath, fieldName: AvroFieldName, fields: Seq[AvroValue]): AvroRecord =
    AvroRecord(rootPath, fieldName, fields.map(field => field.path -> field).toMap)

  def apply(rootPath: PactFieldPath, fieldName: AvroFieldName, schema: Schema, configFields: Map[String, Value]): Either[Seq[PluginError[_]], AvroRecord] = {
    schema.getFields.asScala.toSeq
      .map { schemaField =>
        val fieldName = AvroFieldName(schemaField.name())
        configFields.get(schemaField.name()) match {
          case Some(configValue) =>
            schemaField.schema().getType match {
              case STRING  => AvroValue(rootPath, fieldName, schemaField.schema().getType, configValue)
              case INT     => AvroValue(rootPath, fieldName, schemaField.schema().getType, configValue)
              case LONG    => AvroValue(rootPath, fieldName, schemaField.schema().getType, configValue)
              case FLOAT   => AvroValue(rootPath, fieldName, schemaField.schema().getType, configValue)
              case DOUBLE  => AvroValue(rootPath, fieldName, schemaField.schema().getType, configValue)
              case BOOLEAN => AvroValue(rootPath, fieldName, schemaField.schema().getType, configValue)
              case ENUM    => AvroValue(rootPath, fieldName, schemaField.schema().getType, configValue)
              case FIXED   => AvroValue(rootPath, fieldName, schemaField.schema().getType, configValue)
              case BYTES   => AvroValue(rootPath, fieldName, schemaField.schema().getType, configValue)
              case NULL    => AvroValue(rootPath, fieldName, schemaField.schema().getType, configValue)
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
              AvroValue(rootPath, fieldName, schemaField.schema().getType, schemaField.defaultVal(), Seq.empty).left.map(e => Seq(e))
            } else {
              Left(Seq(PluginErrorException(new Exception(s"Couldn't find configuration for field: ${schemaField.name()}"))))
            }
        }
      }
      .partitionMap(identity) match {
      case (errors, _) if errors.nonEmpty => Left(errors.flatten)
      case (_, value)                     => Right(AvroRecord(rootPath, fieldName, value))
    }
  }

  private def toRecord(schema: Schema, avroRecord: AvroRecord): GenericData.Record = {
    val record: GenericData.Record = new GenericData.Record(schema)
    avroRecord.toRecordValue.asScala.foreach { case (key, value) =>
      val fieldSchema = schema.getField(key).schema()
      if (fieldSchema.getType == Schema.Type.ENUM) {
        record.put(key, new GenericData.EnumSymbol(fieldSchema, value))
      } else {
        record.put(key, value)
      }
    }
    record
  }

  def toByteString(schema: Schema, avroRecord: AvroRecord): Either[PluginErrorException, ByteString] = {
    val datumWriter = new GenericDatumWriter[GenericRecord](schema)
    Using(new ByteArrayOutputStream()) { os =>
      val encoder = EncoderFactory.get.binaryEncoder(os, null)
      val record = AvroRecord.toRecord(schema, avroRecord)
      datumWriter.write(record, encoder)
      encoder.flush()
      os.toByteArray
    } match {
      case Success(bytes)     => Right(ByteString.copyFrom(bytes))
      case Failure(exception) => Left(PluginErrorException(exception))
    }
  }
}
