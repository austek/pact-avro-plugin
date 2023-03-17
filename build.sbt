import BuildSettings._
import Dependencies._

ThisBuild / scalaVersion := scala213

val withExclusions: ModuleID => ModuleID = moduleId => moduleId.excludeAll(Dependencies.exclusions: _*)

lazy val plugin = project
  .in(file("modules/plugin"))
  .enablePlugins(
    AkkaGrpcPlugin,
    DockerPlugin,
    GhpagesPlugin,
    GitVersioning,
    JavaAppPackaging
  )
  .settings(
    name := "plugin",
    maintainer := "aliustek@gmail.com",
    basicSettings,
    executableScriptName := "pact-avro-plugin",
    git.remoteRepo := "git@github.com:austek/pact-avro-plugin.git",
    ghpagesNoJekyll := true,
    siteSourceDirectory := (`pact-avro-plugin` / baseDirectory).value / "build" / "site",
    makeSite / includeFilter := (makeSite / includeFilter).value || ("*.svg" | "*.txt" | "*.woff" | "*.woff2" | "*.xml"),
    ghpagesCleanSite / excludeFilter := ".gitignore" || "CNAME",
    Compile / packageDoc / mappings := Seq(),
    inConfig(Universal) {
      Seq(
        packageName := s"pact-avro-plugin-${version.value}",
        topLevelDirectory := Some(s"avro-${version.value}"),
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
lazy val pluginRef = LocalProject("plugin")

lazy val provider = project
  .in(file("modules/examples/provider"))
  .settings(
    basicSettings,
    Test / sbt.Keys.test := (Test / sbt.Keys.test).dependsOn(pluginRef / Universal / stage).value,
    Test / envVars := Map("PACT_PLUGIN_DIR" -> (pluginRef / Universal / stagingDirectory).value.absolutePath),
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
    Test / sbt.Keys.test := (Test / sbt.Keys.test).dependsOn(pluginRef / Universal / stage).value,
    Test / envVars := Map("PACT_PLUGIN_DIR" -> (pluginRef / Universal / stagingDirectory).value.absolutePath),
    libraryDependencies ++=
      Dependencies.compile(avroCompiler, logback, pulsar4sCore, pulsar4sAvro, pureConfig, scalaLogging).map(withExclusions) ++
        Dependencies.test(assertJCore, jUnitInterface, pactConsumerJunit).map(withExclusions),
    dependencyOverrides += Dependencies.pactCore,
    publish / skip := false
  )

lazy val `pact-avro-plugin` = (project in file("."))
  .aggregate(
    pluginRef,
    consumer,
    provider
  )
  .settings(
    basicSettings,
    publish / skip := false
  )
