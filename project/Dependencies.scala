import sbt._
import sbt.librarymanagement.syntax.ExclusionRule

object Dependencies extends DependencyUtils {

  object Versions {
    val avro = "1.11.1"
    val logback = "1.2.11"
    val pact = "4.5.4"
    val pulsar4sVersion = "2.9.0"
    val scalaTest = "3.2.15"
  }

  val auPactMatchers: ModuleID = "au.com.dius.pact.core"     % "matchers"        % Versions.pact excludeAll ExclusionRule("io.pact.plugin.driver")
  val grpcStub: ModuleID = "io.grpc"                         % "grpc-stub"       % "1.53.0"
  val logback: ModuleID = "ch.qos.logback"                   % "logback-classic" % "1.4.6"
  val pactCore: ModuleID = "io.pact.plugin.driver"           % "core"            % "0.3.2" excludeAll ExclusionRule("au.com.dius.pact.core")
  val scalaLogging: ModuleID = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5"
  val pureConfig: ModuleID = "com.github.pureconfig"        %% "pureconfig"      % "0.17.2"

  // Test dependencies
  val apacheAvro: ModuleID = "org.apache.avro"                  % "avro"              % Versions.avro
  val assertJCore: ModuleID = "org.assertj"                     % "assertj-core"      % "3.24.2"
  val avroCompiler: ModuleID = "org.apache.avro"                % "avro-compiler"     % Versions.avro
  val jUnitInterface: ModuleID = "net.aichler"                  % "jupiter-interface" % "0.11.1"
  val pactConsumerJunit: ModuleID = "au.com.dius.pact.consumer" % "junit5"            % Versions.pact excludeAll ExclusionRule("io.pact.plugin.driver")
  val pactProviderJunit: ModuleID = "au.com.dius.pact.provider" % "junit5"            % Versions.pact excludeAll ExclusionRule("io.pact.plugin.driver")
  val pulsar4sAvro: ModuleID = "com.clever-cloud.pulsar4s"     %% "pulsar4s-avro"     % Versions.pulsar4sVersion
  val pulsar4sCore: ModuleID = "com.clever-cloud.pulsar4s"     %% "pulsar4s-core"     % Versions.pulsar4sVersion
  val scalacheck: ModuleID = "org.scalacheck"                  %% "scalacheck"        % "1.17.0"
  val scalaTest: ModuleID = "org.scalatest"                    %% "scalatest"         % Versions.scalaTest

  // Dependency Conflict Resolution
  val exclusions: Seq[ExclusionRule] = Seq[ExclusionRule]()
}
