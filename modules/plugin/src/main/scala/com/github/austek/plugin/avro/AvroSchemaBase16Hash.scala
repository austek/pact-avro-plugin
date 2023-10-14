package com.github.austek.plugin.avro

import com.google.common.io.BaseEncoding
import org.apache.avro.Schema

import java.security.MessageDigest

case class AvroSchemaBase16Hash(value: String)

object AvroSchemaBase16Hash {
  private val digest: MessageDigest = MessageDigest.getInstance("MD5")

  def apply(avroSchema: Schema): AvroSchemaBase16Hash =
    AvroSchemaBase16Hash(BaseEncoding.base16().lowerCase().encode(digest.digest(avroSchema.toString.getBytes)))
}
