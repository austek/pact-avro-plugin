import sbt.Keys._
import sbt._

import java.util

object BuildSettings {
  private val javaVersion = 11
  private val env: util.Map[String, String] = System.getenv()
  val scala213 = "2.13.10"


  lazy val basicSettings: Seq[Def.Setting[_]] = Seq(
    homepage := Some(new URL("https://github.com/austek/pact-avro-plugin")),
    organization := "io.pact",
    description := "Pact Avro Plugin",
    scalaVersion := scala213,
    resolvers += Resolver.mavenLocal,
    resolvers ++= Resolver.sonatypeOssRepos("releases"),
    javacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-source", s"$javaVersion",
      "-target", s"$javaVersion"
    ),
    Test / fork := true,
    scalacOptions --= {
      if (sys.env.contains("CI"))
        Seq.empty
      else
        Seq("-Xfatal-warnings")
    },
    scalacOptions ++= Seq(
      "-Wconf:src=src_managed/.*:silent"
    ),
    Test / scalacOptions := Seq(
      "-Ywarn-value-discard"
    ),
    initialize := {
      val _ = initialize.value
      val javaVersionFound = sys.props("java.specification.version").toDouble
      if (javaVersionFound < javaVersion)
        sys.error(s"Minimum Java $javaVersion is required for this project. Found $javaVersionFound instead")
    },
    // Configures eviction reports
    evicted / evictionWarningOptions := EvictionWarningOptions.default
      .withWarnDirectEvictions(true)
      .withWarnEvictionSummary(true)
      .withWarnScalaVersionEviction(true)
      .withWarnTransitiveEvictions(true)
      .withShowCallers(true),
    // Checks evictions on resolving dependencies
    update := update.dependsOn(evicted).value,
  )
}
