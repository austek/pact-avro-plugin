package com.collibra.plugin.avro.utils

import au.com.dius.pact.core.model.matchingrules.{MatchingRule, MatchingRuleCategory, MatchingRuleGroup}
import org.scalatest.enablers.Size

import scala.jdk.CollectionConverters._
object MatchingRuleCategoryImplicits {

  implicit val sizeOfMatchingRuleCategory: Size[MatchingRuleCategory] =
    (rules: MatchingRuleCategory) => rules.getMatchingRules.size.toLong

  implicit class RichMatchingRuleCategory(rules: MatchingRuleCategory) {
    def get(path: String): MatchingRuleGroup = rules.getMatchingRules.get(path)
    def getRules(path: String): List[MatchingRule] = rules.getMatchingRules.get(path).getRules.asScala.toList
  }

}
