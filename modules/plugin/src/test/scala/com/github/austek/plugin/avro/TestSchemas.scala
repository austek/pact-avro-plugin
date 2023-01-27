package com.github.austek.plugin.avro

import com.github.austek.plugin.avro.utils.AvroUtils
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
