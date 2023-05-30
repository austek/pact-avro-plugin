import sbt._
import sbt.librarymanagement.syntax.ExclusionRule

object Dependencies extends DependencyUtils {

  object Versions {
    val avro = "1.11.1"
    val logback = "1.2.11"
    val pact = "4.6.0"
    val pulsar4sVersion = "2.9.0"
    val scalaTest = "3.2.16"
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
  val logback: ModuleID = "ch.qos.logback"         % "logback-classic" % "1.4.7"
  val pactCore: ModuleID = "io.pact.plugin.driver" % "core"            % "0.4.0" excludeAll (
    ExclusionRule("au.com.dius.pact.core"),
    ExclusionRule("org.slf4j")
  )
  val scalaLogging: ModuleID = "com.typesafe.scala-logging" %% "scala-logging"        % "3.9.5" excludeAll ExclusionRule("org.slf4j")
  val scalaPBRuntime = "com.thesamet.scalapb"               %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
  val slf4jApi: ModuleID = "org.slf4j"                      %% "slf4j-api"            % "2.0.6"
  val pureConfig: ModuleID = "com.github.pureconfig"        %% "pureconfig"           % "0.17.4"

  // Test dependencies
  val assertJCore: ModuleID = "org.assertj"                     % "assertj-core"      % "3.24.2"
  val avroCompiler: ModuleID = "org.apache.avro"                % "avro-compiler"     % Versions.avro excludeAll ExclusionRule("org.slf4j")
  val jUnitInterface: ModuleID = "net.aichler"                  % "jupiter-interface" % "0.11.1"
  val pactConsumerJunit: ModuleID = "au.com.dius.pact.consumer" % "junit5"            % Versions.pact excludeAll ExclusionRule("io.pact.plugin.driver")
  val pactProviderJunit: ModuleID = "au.com.dius.pact.provider" % "junit5"            % Versions.pact excludeAll ExclusionRule("io.pact.plugin.driver")
  val pulsar4sAvro: ModuleID = "com.clever-cloud.pulsar4s"     %% "pulsar4s-avro"     % Versions.pulsar4sVersion excludeAll ExclusionRule("org.slf4j")
  val pulsar4sCore: ModuleID = "com.clever-cloud.pulsar4s"     %% "pulsar4s-core"     % Versions.pulsar4sVersion excludeAll ExclusionRule("org.slf4j")
  val scalacheck: ModuleID = "org.scalacheck"                  %% "scalacheck"        % "1.17.0"
  val scalaTest: ModuleID = "org.scalatest"                    %% "scalatest"         % Versions.scalaTest

  // Overrides
  val grpcApi: ModuleID = "io.grpc"   % "grpc-api"   % scalapb.compiler.Version.grpcJavaVersion
  val grpcCore: ModuleID = "io.grpc"  % "grpc-core"  % scalapb.compiler.Version.grpcJavaVersion
  val grpcNetty: ModuleID = "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion
}
