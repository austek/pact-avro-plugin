package com.github.austek.plugin.avro.error

import com.github.austek.plugin.avro.AvroFieldName

case class FieldInvalidSchemaException(fieldName: AvroFieldName, fieldValue: Object)
    extends UnsupportedOperationException(s"A valid schema wasn't find for field: '${fieldName.value}' with value: '$fieldValue'")
