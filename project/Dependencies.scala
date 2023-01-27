import sbt._

object Dependencies extends DependencyUtils {

  object Versions {
    val akka = "2.7.0"
    val akkaHttp = "10.2.9"
    val avro = "1.11.1"
    val logback = "1.2.11"
    val pact = "4.5.0-beta.0"
    val scalaTest = "3.2.15"
  }

  val akka: ModuleID = "com.typesafe.akka"                  %% "akka-actor-typed" % Versions.akka
  val akkaStream: ModuleID = "com.typesafe.akka"            %% "akka-stream"      % Versions.akka
  val grpcStub: ModuleID = "io.grpc"                         % "grpc-stub"        % "1.49.0"
  val logback: ModuleID = "ch.qos.logback"                   % "logback-classic"  % "1.4.5"
  val pactCore: ModuleID = "io.pact.plugin.driver"           % "core"             % "0.2.2"
  val scalaLogging: ModuleID = "com.typesafe.scala-logging" %% "scala-logging"    % "3.9.5"

  // Test dependencies
  val akkaHttpAvro: ModuleID = "fr.davit"                      %% "akka-http-avro"                 % "0.1.3"
  val akkaHttpTest: ModuleID = "com.typesafe.akka"             %% "akka-http-testkit"              % Versions.akkaHttp
  val akkaStreamTest: ModuleID = "com.typesafe.akka"           %% "akka-stream-testkit"            % Versions.akka
  val apacheAvro: ModuleID = "org.apache.avro"                  % "avro"                           % Versions.avro
  val avroCompiler: ModuleID = "org.apache.avro"                % "avro-compiler"                  % Versions.avro
  val hamcrest: ModuleID = "org.hamcrest"                       % "hamcrest"                       % "2.2"
  val jUnitInterface: ModuleID = "net.aichler"                  % "jupiter-interface"              % "0.11.1"
  val pact4sScalaTest: ModuleID = "io.github.jbwheatley"       %% "pact4s-scalatest"               % "0.8.0"
  val pactConsumer: ModuleID = "au.com.dius.pact"               % "consumer"                       % Versions.pact
  val pactConsumerJunit: ModuleID = "au.com.dius.pact.consumer" % "junit5"                         % Versions.pact
  val pactProvider: ModuleID = "au.com.dius.pact"               % "provider"                       % Versions.pact
  val scalaTest: ModuleID = "org.scalatest"                    %% "scalatest"                      % Versions.scalaTest
  val testContainer: ModuleID = "com.dimafeng"                 %% "testcontainers-scala-scalatest" % "0.40.12"

  // Dependency Conflict Resolution
  val exclusions = Seq()
}
