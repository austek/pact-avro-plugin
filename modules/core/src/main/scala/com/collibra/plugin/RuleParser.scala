package com.collibra.plugin

import au.com.dius.pact.core.model.matchingrules
import au.com.dius.pact.core.model.matchingrules.expressions.MatchingRuleDefinition
import com.collibra.plugin.avro.utils.AvroSupportImplicits.{fromPactEither, fromPactResult}
import com.collibra.plugin.avro.utils.{PluginError, PluginErrorMessage, PluginErrorMessages}
import com.google.protobuf.struct.Value

import scala.jdk.CollectionConverters._

object RuleParser {
  def parseRules(inValue: Value): Either[PluginError[_], (String, Seq[matchingrules.MatchingRule])] = {
    fromPactResult(MatchingRuleDefinition.parseMatchingRuleDefinition(inValue.getStringValue)) match {
      case Right(ok) =>
        ok.getRules.asScala.toSeq
          .map(fromPactEither)
          .map {
            case Left(rule)  => Right(rule)
            case Right(rule) => Left(s"Rule '$rule' not supported for now")
          }
          .partitionMap(identity) match {
          case (errors, _) if errors.nonEmpty => Left(PluginErrorMessages(errors))
          case (_, rules)                     => Right((ok.getValue, rules))
        }
      case Left(err) =>
        Left(PluginErrorMessage(s"'${inValue.getStringValue}' is not a valid matching rule definition - $err"))
    }
  }
}
