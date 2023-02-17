import BuildSettings._
import Dependencies._

val setPluginStage = """; set core / Universal / stagingDirectory := file(s"${System.getProperty("user.home")}/.pact/plugins/avro-${version.value}")"""

ThisBuild / version := {
  val orig = (ThisBuild / version).value
  if (orig.endsWith("-SNAPSHOT")) orig.split("""\+""").head + "-SNAPSHOT"
  else orig
}
ThisBuild / scalaVersion := scala213 // scala-steward:off

// sbt-github-actions
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(
    name = Some("Build project"),
    commands = List(
      setPluginStage,
      "compile",
      "scalafmtCheckAll",
      "test"
    )
  )
)
// Add windows-latest when https://github.com/sbt/sbt/issues/7082 is resolved
ThisBuild / githubWorkflowOSes := Seq("ubuntu-latest", "macos-latest")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))
ThisBuild / githubWorkflowTargetBranches := Seq("main")
ThisBuild / githubWorkflowTargetTags := Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v")))
ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    commands = List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )
)

val withExclusions: ModuleID => ModuleID = moduleId => moduleId.excludeAll(Dependencies.exclusions: _*)

lazy val core = project
  .in(file("modules/core"))
  .configs(IntegrationTest)
  .enablePlugins(
    AkkaGrpcPlugin,
    DockerPlugin,
    JavaAppPackaging
  )
  .settings(
    name := "core",
    maintainer := "ali.ustek@collibra.com",
    basicSettings,
    Defaults.itSettings,
    executableScriptName := "pact-avro-plugin",
    Compile / packageDoc / mappings := Seq(),
    inConfig(Universal) {
      Seq(
        packageName := s"avro-${version.value}",
        mappings += {
          baseDirectory.value / "pact-plugin.json" -> "pact-plugin.json"
        }
      )
    },
    libraryDependencies ++=
      Dependencies.compile(apacheAvro, logback, pactCore, scalaLogging).map(withExclusions) ++
        Dependencies.test(scalaTest).map(withExclusions),
    dependencyOverrides += Dependencies.grpcStub
  )

lazy val provider = project
  .in(file("modules/examples/provider"))
  .settings(
    basicSettings,
    libraryDependencies ++=
      Dependencies.compile(akka, akkaHttpAvro, akkaStream, apacheAvro, avroCompiler, logback).map(withExclusions) ++
        Dependencies.test(akkaHttpTest, akkaStreamTest, scalaTest).map(withExclusions),
    publish / skip := false
  )

lazy val consumer = project
  .in(file("modules/examples/consumer"))
  .dependsOn(provider)
  .settings(
    basicSettings,
    Test / sbt.Keys.test := (Test / sbt.Keys.test).dependsOn(core / Universal / stage).value,
    libraryDependencies ++=
      Dependencies.test(assertJCore, jUnitInterface, pactConsumerJunit).map(withExclusions),
    dependencyOverrides += Dependencies.pactCore,
    publish / skip := false
  )

lazy val `pact-avro-plugin` = (project in file("."))
  .aggregate(
    core,
    consumer,
    provider
  )
  .settings(
    basicSettings,
    commands += Command.command("pactTest") { state =>
      setPluginStage ::
        "consumer/test" :: state
    },
    publish / skip := false
  )
