package com.github.austek.plugin.avro.utils

import com.github.austek.plugin.avro.{AvroFieldName, PactFieldPath}

object StringUtils {

  implicit class StringImprovements(val s: String) {
    def toPactPath: PactFieldPath = PactFieldPath(s.split('.').toList)

    def toFieldName: AvroFieldName = AvroFieldName(s)
  }

}
