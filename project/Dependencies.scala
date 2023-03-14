import sbt._

object Dependencies extends DependencyUtils {

  object Versions {
    val avro = "1.11.1"
    val logback = "1.2.11"
    val pact = "4.5.2"
    val pulsar4sVersion = "2.9.0"
    val scalaTest = "3.2.15"
  }

  val auPacMatchers: ModuleID = "au.com.dius.pact.core"      % "matchers"        % "4.5.2"
  val grpcStub: ModuleID = "io.grpc"                         % "grpc-stub"       % "1.53.0"
  val logback: ModuleID = "ch.qos.logback"                   % "logback-classic" % "1.4.5"
  val pactCore: ModuleID = "io.pact.plugin.driver"           % "core"            % "0.3.1"
  val scalaLogging: ModuleID = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5"
  val pureConfig: ModuleID = "com.github.pureconfig"        %% "pureconfig"      % "0.17.2"

  // Test dependencies
  val apacheAvro: ModuleID = "org.apache.avro"                  % "avro"                           % Versions.avro
  val assertJCore: ModuleID = "org.assertj"                     % "assertj-core"                   % "3.24.2"
  val avroCompiler: ModuleID = "org.apache.avro"                % "avro-compiler"                  % Versions.avro
  val hamcrest: ModuleID = "org.hamcrest"                       % "hamcrest"                       % "2.2"
  val jUnitInterface: ModuleID = "net.aichler"                  % "jupiter-interface"              % "0.11.1"
  val pact4sScalaTest: ModuleID = "io.github.jbwheatley"       %% "pact4s-scalatest"               % "0.9.0"
  val pactConsumerJunit: ModuleID = "au.com.dius.pact.consumer" % "junit5"                         % Versions.pact
  val pactProviderJunit: ModuleID = "au.com.dius.pact.provider" % "junit5"                         % Versions.pact
  val pulsar4sAvro: ModuleID = "com.clever-cloud.pulsar4s"     %% "pulsar4s-avro"                  % Versions.pulsar4sVersion
  val pulsar4sCore: ModuleID = "com.clever-cloud.pulsar4s"     %% "pulsar4s-core"                  % Versions.pulsar4sVersion
  val scalacheck: ModuleID = "org.scalacheck"                  %% "scalacheck"                     % "1.17.0"
  val scalaTest: ModuleID = "org.scalatest"                    %% "scalatest"                      % Versions.scalaTest
  val testContainer: ModuleID = "com.dimafeng"                 %% "testcontainers-scala-scalatest" % "0.40.12"

  // Dependency Conflict Resolution
  val exclusions = Seq()
}
