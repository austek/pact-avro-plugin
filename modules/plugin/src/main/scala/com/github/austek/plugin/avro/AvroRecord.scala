package com.github.austek.plugin.avro

import au.com.dius.pact.core.model.matchingrules.{MatchingRule, MatchingRuleCategory}
import com.github.austek.plugin.RuleParser.parseRules
import com.github.austek.plugin.avro.AvroPluginConstants.MatchingRuleCategoryName
import com.github.austek.plugin.avro.utils.StringUtils._
import com.github.austek.plugin.avro.utils.{PluginError, PluginErrorException, PluginErrorMessage}
import com.google.protobuf.ByteString
import com.google.protobuf.struct.Value
import com.google.protobuf.struct.Value.Kind._
import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.Schema
import org.apache.avro.Schema.Type._
import org.apache.avro.generic._
import org.apache.avro.io.EncoderFactory

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try, Using}

case class AvroFieldName(value: String)
case class PactFieldPath(segments: List[String]) {

  @`inline` def :+(segment: String): PactFieldPath = append(segment)

  @`inline` def :+(segment: Int): PactFieldPath = append(segment.toString)

  @`inline` def :+(segment: AvroFieldName): PactFieldPath = append(segment.value)
  def append(segment: String): PactFieldPath = PactFieldPath(segments :+ segment)

  def startsWith(other: PactFieldPath): Boolean = segments.startsWith(other.segments)

  def toJsonPath: String = segments.mkString(".")

  def size: Int = segments.size
}

object Avro {
  sealed trait AvroValue {

    type AvroValueType

    def path: PactFieldPath

    def name: AvroFieldName

    def value: AvroValueType

    def rules: Seq[MatchingRule]

    protected[Avro] def addRules(matchingRules: MatchingRuleCategory): Unit = matchingRules.addRules(path.toJsonPath, rules.asJava)
  }

  object AvroValue extends StrictLogging {

    def apply(
      rootPath: PactFieldPath,
      fieldName: AvroFieldName,
      schema: Schema,
      inValue: Value,
      appendPath: Boolean = true
    ): Either[Seq[PluginError[_]], AvroValue] = {
      val path = if (appendPath) rootPath :+ fieldName else rootPath
      logger.debug(s">>> buildFieldValue($path, $fieldName, $inValue)")
      val valueSchema = schema.getType match {
        case ARRAY => schema.getElementType
        case MAP   => schema.getValueType
        case _     => schema
      }
      inValue.kind match {
        case Empty        => Right(AvroNull(path, fieldName))
        case NullValue(_) => Right(AvroNull(path, fieldName))
        case StringValue(_) =>
          parseRules(inValue)
            .flatMap { fieldRule =>
              AvroValue(path, fieldName, valueSchema.getType, fieldRule.value, fieldRule.rules)
            }
            .left
            .map(e => Seq(e))
        case ListValue(_)   => Left(Seq(PluginErrorMessage(s"List kind value for field is not supported")))
        case NumberValue(_) => Left(Seq(PluginErrorMessage(s"Number kind value for field is not supported")))
        case BoolValue(_)   => Left(Seq(PluginErrorMessage(s"Bool kind value for field is not supported")))
        case StructValue(_) =>
          if (valueSchema.getType == RECORD) {
            AvroRecord(path, fieldName, valueSchema, inValue.getStructValue.fields)
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
            case ARRAY  => Left(new UnsupportedOperationException(s"'ARRAY' type is not supported for field: '${fieldName.value}' with value: '$fieldValue'"))
            case MAP    => Left(new UnsupportedOperationException(s"'MAP' type is not supported for field: '${fieldName.value}' with value: '$fieldValue'"))
            case RECORD => Left(new UnsupportedOperationException(s"'RECORD' type is not supported for field: '${fieldName.value}' with value: '$fieldValue'"))
            case UNION  => Left(new UnsupportedOperationException(s"'UNION' type is not supported for field: '${fieldName.value}' with value: '$fieldValue'"))
            case t =>
              Left(new UnsupportedOperationException(s"Unknown type '$t' type is not supported for field: '${fieldName.value}' with value: '$fieldValue'"))
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
        case DOUBLE  => Try(fieldValue.toDouble).map(v => AvroDouble(path, fieldName, v, rules)).toEither
        case ENUM    => Right(AvroEnum(path, fieldName, fieldValue, rules))
        case FIXED   => Right(AvroString(path, fieldName, fieldValue, rules)) // TODO: Use bytes, Using String values for now
        case FLOAT   => Try(fieldValue.toFloat).map(v => AvroFloat(path, fieldName, v, rules)).toEither
        case INT     => Try(fieldValue.toInt).map(v => AvroInt(path, fieldName, v, rules)).toEither
        case LONG    => Try(fieldValue.toLong).map(v => AvroLong(path, fieldName, v, rules)).toEither
        case NULL    => Right(AvroNull(path, fieldName))
        case STRING  => Right(AvroString(path, fieldName, fieldValue, rules))
        case ARRAY   => Left(new UnsupportedOperationException(s"'ARRAY' type is not supported for field: '${fieldName.value}' with value: '$fieldValue'"))
        case MAP     => Left(new UnsupportedOperationException(s"'MAP' type is not supported for field: '${fieldName.value}' with value: '$fieldValue'"))
        case RECORD  => Left(new UnsupportedOperationException(s"'RECORD' type is not supported for field: '${fieldName.value}' with value: '$fieldValue'"))
        case UNION   => Left(new UnsupportedOperationException(s"'UNION' type is not supported for field: '${fieldName.value}' with value: '$fieldValue'"))
        case t => Left(new UnsupportedOperationException(s"Unknown type '$t' type is not supported for field: '${fieldName.value}' with value: '$fieldValue'"))
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

  case class AvroDouble(override val path: PactFieldPath, override val name: AvroFieldName, value: Double, rules: Seq[MatchingRule] = Seq.empty)
      extends AvroValue {
    override type AvroValueType = Double
  }

  case class AvroFloat(override val path: PactFieldPath, override val name: AvroFieldName, value: Float, rules: Seq[MatchingRule] = Seq.empty)
      extends AvroValue {
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

    protected[Avro] def toRecordValue: util.List[Any] = value.map {
      case a: AvroArray  => a.toRecordValue
      case m: AvroMap    => m.toRecordValue
      case r: AvroRecord => r
      case v             => v.value
    }.asJava

    protected[Avro] override def addRules(matchingRules: MatchingRuleCategory): Unit =
      value.foreach(_.addRules(matchingRules))
  }

  object AvroArray {
    def apply(rootPath: PactFieldPath, fieldName: AvroFieldName, schema: Schema, inValue: Value): Either[Seq[PluginError[_]], AvroArray] = {
      inValue.kind match {
        case Empty        => Right(AvroArray(rootPath, fieldName))
        case NullValue(_) => Right(AvroArray(rootPath, fieldName))
        case ListValue(listValue) =>
          val arrayBasePath = rootPath :+ fieldName
          listValue.values.zipWithIndex
            .map { case (singleValue, index) =>
              schema.getElementType.getType match {
                case RECORD => AvroValue(arrayBasePath :+ index, fieldName, schema, singleValue, appendPath = false)
                case _ =>
                  AvroValue(rootPath, fieldName, schema, singleValue).map {
                    case v: AvroNull    => v.copy(path = v.path :+ index)
                    case v: AvroString  => v.copy(path = v.path :+ index)
                    case v: AvroEnum    => v.copy(path = v.path :+ index)
                    case v: AvroDouble  => v.copy(path = v.path :+ index)
                    case v: AvroFloat   => v.copy(path = v.path :+ index)
                    case v: AvroInt     => v.copy(path = v.path :+ index)
                    case v: AvroLong    => v.copy(path = v.path :+ index)
                    case v: AvroBoolean => v.copy(path = v.path :+ index)
                    case v: AvroArray   => v.copy(path = v.path :+ index)
                    case v: AvroMap     => v.copy(path = v.path :+ index)
                    case v: AvroRecord  => v
                  }
              }
            }
            .partitionMap(identity) match {
            case (errors, _) if errors.nonEmpty => Left(errors.flatten)
            case (_, fields)                    => Right(AvroArray(arrayBasePath, fieldName, fields.toList))
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

    protected[Avro] def toRecordValue: util.Map[String, Any] = value.map { case (_, v) =>
      v match {
        case a: AvroArray  => a.name.value -> a.toRecordValue
        case m: AvroMap    => m.name.value -> m.toRecordValue
        case r: AvroRecord => r.name.value -> r
        case av            => av.name.value -> av.value
      }
    }.asJava

    protected[Avro] override def addRules(matchingRules: MatchingRuleCategory): Unit =
      value.foreach { case (_, field) =>
        field.addRules(matchingRules)
      }
  }

  object AvroMap {
    def apply(rootPath: PactFieldPath, fieldName: AvroFieldName, schema: Schema, inValue: Value): Either[Seq[PluginError[_]], AvroMap] = {
      val path = rootPath :+ fieldName
      inValue.kind match {
        case Empty        => Right(AvroMap(path, fieldName))
        case NullValue(_) => Right(AvroMap(path, fieldName))
        case StructValue(structValue) =>
          structValue.fields
            .map { case (key, singleValue) =>
              AvroValue(path, key.toFieldName, schema, singleValue).map { v =>
                key.toPactPath -> v
              }
            }
            .partitionMap(identity) match {
            case (errors, _) if errors.nonEmpty => Left(errors.toSeq.flatten)
            case (_, fields)                    => Right(AvroMap(path, fieldName, fields.toMap))
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

    protected[Avro] override def addRules(matchingRules: MatchingRuleCategory): Unit =
      value.foreach { case (_, avroValue) =>
        avroValue.addRules(matchingRules)
      }

    def matchingRules: MatchingRuleCategory = {
      val matchingRules: MatchingRuleCategory = new MatchingRuleCategory(MatchingRuleCategoryName)
      addRules(matchingRules)
      matchingRules
    }

    private def toRecordValue: util.Map[String, Any] = value.map { case (_, v) =>
      v match {
        case a: AvroArray  => a.name.value -> a.toRecordValue
        case m: AvroMap    => m.name.value -> m.toRecordValue
        case r: AvroRecord => r.name.value -> r
        case n: AvroNull   => n.name.value -> null
        case av            => av.name.value -> av.value
      }
    }.asJava

    def toGenericRecord(schema: Schema): GenericRecord = {
      val record: GenericRecord = new GenericData.Record(schema)
      toRecordValue.asScala.foreach { case (key, value) =>
        if (null != value) {
          val field = schema.getField(key)
          val fieldSchema = field.schema()
          fieldToRecord(record, key, value, fieldSchema)
        } else {
          record.put(key, value)
        }
      }
      record
    }

    @tailrec
    private def fieldToRecord(record: GenericRecord, key: String, value: Any, fieldSchema: Schema): Unit = {
      fieldSchema.getType match {
        case ENUM =>
          record.put(key, new GenericData.EnumSymbol(fieldSchema, value))
        case ARRAY if fieldSchema.getElementType.getType == RECORD =>
          record.put(
            key,
            value
              .asInstanceOf[util.List[AvroRecord]]
              .asScala
              .map { item =>
                item.toGenericRecord(fieldSchema.getElementType)
              }
              .asJava
          )
        case MAP if fieldSchema.getValueType.getType == RECORD =>
          record.put(
            key,
            value
              .asInstanceOf[util.Map[String, AvroRecord]]
              .asScala
              .map { case (key, item) =>
                key -> item.toGenericRecord(fieldSchema.getValueType)
              }
              .asJava
          )
        case RECORD =>
          record.put(key, value.asInstanceOf[AvroRecord].toGenericRecord(fieldSchema))
        case FIXED =>
          record.put(key, new GenericData.Fixed(fieldSchema, value.asInstanceOf[String].getBytes))
        case UNION =>
          fieldToRecord(record, key, value, fieldSchema.getTypes.asScala.filterNot(_.getType == NULL).head)
        case _ =>
          record.put(key, value)
      }
    }

    def toByteString(schema: Schema): Either[PluginErrorException, ByteString] = {
      val datumWriter = new GenericDatumWriter[GenericRecord](schema)
      Using(new ByteArrayOutputStream()) { os =>
        val encoder = EncoderFactory.get.binaryEncoder(os, null)
        val record = this.toGenericRecord(schema)
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
              selectField(rootPath, schemaField, fieldName, configValue)
            case None =>
              if (schemaField.hasDefaultValue) {
                AvroValue(rootPath :+ fieldName, fieldName, schemaField.schema().getType, schemaField.defaultVal(), Seq.empty).left.map(e => Seq(e))
              } else if (schemaField.schema().getType == UNION && schemaField.schema().getTypes.asScala.exists(_.getType == NULL)) {
                Right(AvroNull(rootPath :+ fieldName, fieldName))
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

    private def selectField(
      rootPath: PactFieldPath,
      schemaField: Schema.Field,
      fieldName: AvroFieldName,
      configValue: Value
    ): Either[Seq[PluginError[_]], AvroValue] = {
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
        case RECORD  => AvroRecord(rootPath :+ fieldName, fieldName, schemaField.schema(), configValue.getStructValue.fields)
        case ARRAY   => AvroArray(rootPath, fieldName, schemaField.schema(), configValue)
        case MAP     => AvroMap(rootPath, fieldName, schemaField.schema(), configValue)
        case UNION =>
          val subTypes = schemaField.schema().getTypes.asScala
          if (subTypes.size == 2 && subTypes.exists(_.getType == NULL)) {
            subTypes.filterNot(_.getType == NULL).headOption match {
              case Some(schema) => selectNullableField(rootPath, fieldName, configValue, schema)
              case None =>
                Left(
                  Seq(
                    PluginErrorException(
                      new UnsupportedOperationException(s"A valid schema wasn't find for field: '${fieldName.value}' with value: '$configValue'")
                    )
                  )
                )
            }
          } else {
            Left(
              Seq(
                PluginErrorException(
                  new UnsupportedOperationException(
                    s"'UNION' type is only supported to make field nullable, field: '${fieldName.value}' with value: '$configValue'"
                  )
                )
              )
            )
          }
        case t =>
          Left(
            Seq(
              PluginErrorException(
                new UnsupportedOperationException(s"Unknown type '$t' type is not supported for field: '${fieldName.value}' with value: '$configValue'")
              )
            )
          )
      }
    }

    private def selectNullableField(rootPath: PactFieldPath, fieldName: AvroFieldName, configValue: Value, schema: Schema) = {
      schema.getType match {
        case STRING  => AvroValue(rootPath, fieldName, schema, configValue)
        case INT     => AvroValue(rootPath, fieldName, schema, configValue)
        case LONG    => AvroValue(rootPath, fieldName, schema, configValue)
        case FLOAT   => AvroValue(rootPath, fieldName, schema, configValue)
        case DOUBLE  => AvroValue(rootPath, fieldName, schema, configValue)
        case BOOLEAN => AvroValue(rootPath, fieldName, schema, configValue)
        case ENUM    => AvroValue(rootPath, fieldName, schema, configValue)
        case FIXED   => AvroValue(rootPath, fieldName, schema, configValue)
        case BYTES   => AvroValue(rootPath, fieldName, schema, configValue)
        case NULL    => AvroValue(rootPath, fieldName, schema, configValue)
        case RECORD  => AvroRecord(rootPath :+ fieldName, fieldName, schema, configValue.getStructValue.fields)
        case ARRAY =>
          AvroArray(rootPath, fieldName, schema, configValue)
        case MAP => AvroMap(rootPath, fieldName, schema, configValue)
        case t =>
          Left(
            Seq(
              PluginErrorException(
                new UnsupportedOperationException(s"$t is not a supported type for field: '${fieldName.value}' with value: '$configValue'")
              )
            )
          )
      }
    }
  }
}
