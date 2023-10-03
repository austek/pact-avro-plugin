package com.github.austek.plugin.avro.utils

import au.com.dius.pact.core.model.matchingrules.{MatchingRule, MatchingRuleCategory, MatchingRuleGroup}
import org.scalatest.enablers.Size

import scala.jdk.CollectionConverters.*
object MatchingRuleCategoryImplicits {

  given timeout: Int = 10

  given sizeOfMatchingRuleCategory: Size[MatchingRuleCategory] = new Size[MatchingRuleCategory] {
    def sizeOf(rules: MatchingRuleCategory): Long = rules.getMatchingRules.size.toLong
  }

  extension (rules: MatchingRuleCategory) {
    def get(path: String): MatchingRuleGroup = rules.getMatchingRules.get(path)
    def getRules(path: String): List[MatchingRule] = rules.getMatchingRules.get(path).getRules.asScala.toList
  }

}
