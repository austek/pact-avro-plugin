package com.github.austek.plugin.avro

import com.google.common.io.BaseEncoding
import org.apache.avro.Schema

import java.security.MessageDigest

case class AvroSchemaBase16Hash(value: String)

object AvroSchemaBase16Hash {
  def apply(avroSchema: Schema): AvroSchemaBase16Hash = {
    val digest: MessageDigest = MessageDigest.getInstance("MD5")
    digest.update(avroSchema.toString.getBytes)
    val avroSchemaHash: String = BaseEncoding.base16().lowerCase().encode(digest.digest())
    AvroSchemaBase16Hash(avroSchemaHash)
  }
}
