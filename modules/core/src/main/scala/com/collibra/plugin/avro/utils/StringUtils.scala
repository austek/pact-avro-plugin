package com.collibra.plugin.avro.utils

import com.collibra.plugin.avro.{AvroFieldName, PactFieldPath}

object StringUtils {

  implicit class StringImprovements(val s: String) {
    def toPactPath: PactFieldPath = PactFieldPath(s)

    def toFieldName: AvroFieldName = AvroFieldName(s)
  }

}