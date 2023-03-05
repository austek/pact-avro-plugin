package com.collibra.plugin.avro

import org.apache.avro.Schema
import org.apache.avro.Schema.Type.{ARRAY, ENUM, FIXED, MAP, RECORD}
import org.apache.avro.generic.{GenericData, GenericRecord => AvroGenericRecord}

import java.util
import scala.jdk.CollectionConverters._

object GenericRecord {

  def apply(schema: Schema, avroRecord: AvroRecord): AvroGenericRecord = {
    val record: GenericData.Record = new GenericData.Record(schema)
    avroRecord.toRecordValue.asScala.foreach { case (key, value) =>
      if (null != value) {
        val field = schema.getField(key)
        val fieldSchema = field.schema()
        fieldSchema.getType match {
          case ENUM =>
            record.put(key, new GenericData.EnumSymbol(fieldSchema, value))
          case ARRAY if fieldSchema.getElementType.getType == RECORD =>
            val subType = fieldSchema.getElementType
            val subRecords = value
              .asInstanceOf[java.util.List[AvroRecord]]
              .asScala
              .map { item =>
                this(subType, item)
              }
              .asJava
            record.put(key, subRecords)
          case MAP if fieldSchema.getValueType.getType == RECORD =>
            val subType = fieldSchema.getValueType
            val subRecords = value.asInstanceOf[util.Map[String, AvroRecord]].asScala.map { case (key, item) =>
              key -> this(subType, item)
            }
            record.put(key, subRecords)
          case RECORD =>
            val subRecord = this(fieldSchema, value.asInstanceOf[AvroRecord])
            record.put(key, subRecord)
          case FIXED =>
            record.put(key, new GenericData.Fixed(fieldSchema, value.asInstanceOf[String].getBytes))
          case _ =>
            record.put(key, value)
        }
      } else {
        record.put(key, value)
      }
    }
    record
  }

}
