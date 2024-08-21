import sbt.*
import sbt.librarymanagement.syntax.ExclusionRule

object Dependencies extends DependencyUtils {

  object Versions {
    val assertjCore = "3.26.3"
    val avro = "1.12.0"
    val jupiterInterface = "0.13.0"
    val logback = "1.5.7"
    val pact = "4.6.13"
    val pactDriverCore = "0.4.2"
    val pulsar4sVersion = "2.9.1"
    val scalacheck = "1.18.0"
    val scalaLogging = "3.9.5"
    val scalaTest = "3.2.19"
    val slf4jApi = "2.0.6"
  }

  // protobuf Dependencies
  val scalaPB: ModuleID = "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion

  // Compile Dependencies
  val apacheAvro: ModuleID = "org.apache.avro"           % "avro"     % Versions.avro excludeAll ExclusionRule("org.slf4j")
  val auPactMatchers: ModuleID = "au.com.dius.pact.core" % "matchers" % Versions.pact excludeAll (
    ExclusionRule("com.google.guava"),
    ExclusionRule("io.pact.plugin.driver"),
    ExclusionRule("org.slf4j")
  )
  val logback: ModuleID = "ch.qos.logback"         % "logback-classic" % Versions.logback
  val pactCore: ModuleID = "io.pact.plugin.driver" % "core"            % Versions.pactDriverCore excludeAll (
    ExclusionRule("au.com.dius.pact.core"),
    ExclusionRule("org.slf4j")
  )
  val scalaLogging: ModuleID = "com.typesafe.scala-logging" %% "scala-logging"        % Versions.scalaLogging excludeAll ExclusionRule("org.slf4j")
  val scalaPBRuntime = "com.thesamet.scalapb"               %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
  val slf4jApi: ModuleID = "org.slf4j"                      %% "slf4j-api"            % Versions.slf4jApi

  // Test dependencies
  val assertJCore: ModuleID = "org.assertj"                     % "assertj-core"      % Versions.assertjCore
  val avroCompiler: ModuleID = "org.apache.avro"                % "avro-compiler"     % Versions.avro excludeAll ExclusionRule("org.slf4j")
  val jUnitInterface: ModuleID = "com.github.sbt.junit"         % "jupiter-interface" % Versions.jupiterInterface
  val pactConsumerJunit: ModuleID = "au.com.dius.pact.consumer" % "junit5"            % Versions.pact
  val pactProviderJunit: ModuleID = "au.com.dius.pact.provider" % "junit5"            % Versions.pact
  val pulsar4sAvro: ModuleID = "com.clever-cloud.pulsar4s"     %% "pulsar4s-avro"     % Versions.pulsar4sVersion excludeAll ExclusionRule("org.slf4j")
  val pulsar4sCore: ModuleID = "com.clever-cloud.pulsar4s"     %% "pulsar4s-core"     % Versions.pulsar4sVersion excludeAll ExclusionRule("org.slf4j")
  val scalacheck: ModuleID = "org.scalacheck"                  %% "scalacheck"        % Versions.scalacheck
  val scalaTest: ModuleID = "org.scalatest"                    %% "scalatest"         % Versions.scalaTest

  // Overrides
  val grpcApi: ModuleID = "io.grpc"   % "grpc-api"   % scalapb.compiler.Version.grpcJavaVersion
  val grpcCore: ModuleID = "io.grpc"  % "grpc-core"  % scalapb.compiler.Version.grpcJavaVersion
  val grpcNetty: ModuleID = "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion
}
