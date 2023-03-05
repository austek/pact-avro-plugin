package com.collibra.plugin.avro

object ContentTypeConstants {
  val ContentTypeApplicationAvro: String = "application/avro"
  val ContentTypeAvroBytes: String = "avro/bytes"
  val ContentTypeAvroBinary: String = "avro/binary"
  val ContentTypeAvroWildcard: String = "application/*+avro"
  val ContentTypes: Seq[String] = Seq(
    ContentTypeApplicationAvro,
    ContentTypeAvroBytes,
    ContentTypeAvroBinary,
    ContentTypeAvroWildcard
  )
  val ContentTypesStr: String = ContentTypes.mkString(";")
}
