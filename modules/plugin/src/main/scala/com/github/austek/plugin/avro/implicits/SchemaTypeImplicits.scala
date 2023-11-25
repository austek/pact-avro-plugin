package com.github.austek.plugin.avro.implicits

import org.apache.avro.Schema.Type
import org.apache.avro.Schema.Type.*
import org.apache.avro.generic.GenericRecord

object SchemaTypeImplicits {

  implicit class RichType(schemaType: Type) {
    def asJava: Class[_] = {
      schemaType match {
        case STRING | BYTES => classOf[String]
        case INT            => classOf[Int]
        case LONG           => classOf[Long]
        case FLOAT          => classOf[Float]
        case DOUBLE         => classOf[Double]
        case BOOLEAN        => classOf[Boolean]
        case ENUM           => classOf[String]
        case FIXED          => classOf[Array[Byte]]
        case ARRAY          => classOf[List[_]]
        case MAP            => classOf[Map[_, _]]
        case RECORD         => classOf[GenericRecord]
        case _              => classOf[Object]
      }
    }
  }
}
