package com.github.austek.plugin.avro.error

import com.github.austek.plugin.avro.AvroFieldName
import org.apache.avro.Schema

case class FieldUnsupportedTypeException(t: Schema.Type, fieldName: AvroFieldName, fieldValue: Object)
    extends UnsupportedOperationException(s"Type '$t' is not supported for field: '${fieldName.value}' with value: '$fieldValue'")
