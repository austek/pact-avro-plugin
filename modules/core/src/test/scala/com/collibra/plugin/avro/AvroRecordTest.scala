package com.collibra.plugin.avro

import com.collibra.plugin.avro.utils.AvroUtils
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class AvroRecordTest extends AnyFlatSpecLike with Matchers with EitherValues {

  "AvroRecord" should "return Interaction Response for a record with other record in field" in {
    val schemasPath = getClass.getResource("/schemas.avsc").getPath
//    AvroRecord(AvroUtils.parseSchema(schemasPath).value, )
    AvroRecord
  }
}
