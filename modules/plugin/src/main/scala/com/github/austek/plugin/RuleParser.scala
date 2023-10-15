package com.github.austek.plugin

import au.com.dius.pact.core.model.matchingrules.MatchingRule
import au.com.dius.pact.core.model.matchingrules.expressions.MatchingRuleDefinition
import com.github.austek.plugin.avro.error.{PluginError, PluginErrorMessage, PluginErrorMessages}
import com.github.austek.plugin.avro.implicits.AvroSupportImplicits.{fromPactEither, fromPactResult}
import com.google.protobuf.struct.Value
import com.google.protobuf.struct.Value.Kind.StringValue

import scala.jdk.CollectionConverters._

case class FieldRule(value: String, rules: Seq[MatchingRule])

object RuleParser {
  def parseRules(inValue: Value): Either[PluginError[_], FieldRule] = parseRules(inValue.getStringValue)

  def parseRules(inValue: StringValue): Either[PluginError[_], FieldRule] = parseRules(inValue.value)

  def parseRules(in: String): Either[PluginError[_], FieldRule] = {
    fromPactResult(MatchingRuleDefinition.parseMatchingRuleDefinition(in)) match {
      case Right(ok) =>
        ok.getRules.asScala.toSeq
          .map(fromPactEither)
          .map {
            case Left(rule)  => Right(rule)
            case Right(rule) => Left(s"Rule '$rule' not supported for now")
          }
          .partitionMap(identity) match {
          case (errors, _) if errors.nonEmpty => Left(PluginErrorMessages(errors))
          case (_, rules)                     => Right(FieldRule(ok.getValue, rules))
        }
      case Left(err) =>
        Left(PluginErrorMessage(s"'$in' is not a valid matching rule definition - $err"))
    }
  }
}
