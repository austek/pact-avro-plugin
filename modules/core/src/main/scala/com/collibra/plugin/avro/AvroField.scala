package com.collibra.plugin.avro

import au.com.dius.pact.core.model.matchingrules.MatchingRule

case class AvroField[+T](path: String, value: AvroFieldValue[T], rules: Seq[MatchingRule])
