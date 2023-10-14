package com.github.austek.plugin.avro.error

import com.github.austek.plugin.avro.AvroFieldName

case class FieldNotNullableException(fieldName: AvroFieldName, fieldValue: Object)
    extends UnsupportedOperationException(s"'UNION' type is only supported to make field nullable, field: '${fieldName.value}' with value: '$fieldValue'")
