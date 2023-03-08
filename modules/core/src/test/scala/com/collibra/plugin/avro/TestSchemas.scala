package com.collibra.plugin.avro

import com.collibra.plugin.avro.utils.AvroUtils
import org.apache.avro.Schema
import org.scalatest.EitherValues

object TestSchemas extends EitherValues {

  def schemaWithField(field: String): Schema = AvroUtils
    .parseSchema(s"""{
        |  "namespace": "com.example",
        |  "type": "record",
        |  "name": "Parent",
        |  "fields": [
        |    $field
        |  ]
        |}""".stripMargin)
    .value
}
