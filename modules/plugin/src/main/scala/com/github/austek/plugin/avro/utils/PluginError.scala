package com.github.austek.plugin.avro.utils

sealed trait PluginError[T] {
  def value: T
}
case class PluginErrorMessage(override val value: String) extends PluginError[String]
case class PluginErrorMessages(override val value: Seq[String]) extends PluginError[Seq[String]]
case class PluginErrorException(override val value: Throwable) extends PluginError[Throwable]
