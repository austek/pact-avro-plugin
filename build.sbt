import BuildSettings._
import Dependencies._

ThisBuild / version := {
  val orig = (ThisBuild / version).value
  if (orig.endsWith("-SNAPSHOT")) orig.split("""\+""").head + "-SNAPSHOT"
  else orig
}
ThisBuild / scalaVersion := scala213 // scala-steward:off

// sbt-github-actions
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Run(
    name = Some("Start containers"),
    commands = List("docker-compose -f docker-compose.yml up -d")
  ),
  WorkflowStep.Sbt(
    name = Some("Build project"),
    commands = List("compile", "scalafmtCheckAll", "core/test")
  ),
  WorkflowStep.Sbt(
    name = Some("Test Consumer"),
    commands = List("consumer/test")
  ),
  WorkflowStep.Run(
    name = Some("Upload Consumer Pact"),
    commands = List("./pact-publish.sh")
  ),
  // TODO: Enable when https://github.com/pact-foundation/pact-jvm/issues/1678 is fixed
//  WorkflowStep.Sbt(
//    name = Some("Test Provider"),
//    commands = List("provider/test")
//  ),
  WorkflowStep.Run(
    name = Some("Stop containers"),
    commands = List("docker-compose -f docker-compose.yml down")
  )
)
// Add windows-latest when https://github.com/sbt/sbt/issues/7082 is resolved
// Add macos-latest when step to install docker on it is done
ThisBuild / githubWorkflowOSes := Seq("ubuntu-latest")
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
  .enablePlugins(
    AkkaGrpcPlugin,
    DockerPlugin,
    JavaAppPackaging
  )
  .settings(
    name := "core",
    maintainer := "ali.ustek@collibra.com",
    basicSettings,
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
      Dependencies.compile(apacheAvro, auPacMatchers, logback, pactCore, scalaLogging).map(withExclusions) ++
        Dependencies.test(scalaTest).map(withExclusions),
    dependencyOverrides += Dependencies.grpcStub
  )

lazy val provider = project
  .in(file("modules/examples/provider"))
  .settings(
    basicSettings,
    Test / sbt.Keys.test := (Test / sbt.Keys.test).dependsOn(core / Universal / stage).value,
    Test / envVars := Map("PACT_PLUGIN_DIR" -> (core / Universal / stagingDirectory).value.absolutePath),
    libraryDependencies ++=
      Dependencies.compile(avroCompiler, logback, pulsar4sCore, pulsar4sAvro, pureConfig, scalacheck).map(withExclusions) ++
        Dependencies.test(assertJCore, jUnitInterface, pactProviderJunit).map(withExclusions),
    publish / skip := false
  )

lazy val consumer = project
  .in(file("modules/examples/consumer"))
  .settings(
    basicSettings,
    Compile / avroSource := (Compile / resourceDirectory).value / "avro",
    Test / sbt.Keys.test := (Test / sbt.Keys.test).dependsOn(core / Universal / stage).value,
    Test / envVars := Map("PACT_PLUGIN_DIR" -> (core / Universal / stagingDirectory).value.absolutePath),
    libraryDependencies ++=
      Dependencies.compile(avroCompiler, logback, pulsar4sCore, pulsar4sAvro, pureConfig, scalaLogging).map(withExclusions) ++
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
    publish / skip := false
  )
