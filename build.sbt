import BuildSettings.*
import Dependencies.*
import PublishSettings.*
import TestEnvironment.*

ThisBuild / scalaVersion := scalaV

lazy val plugin = project
  .in(file("modules/plugin"))
  .enablePlugins(
    GitHubPagesPlugin,
    GitVersioning,
    JavaAppPackaging,
    // https://sbt-native-packager.readthedocs.io/en/stable/recipes/longclasspath.html#long-classpaths
    LauncherJarPlugin
  )
  .settings(
    name := "plugin",
    maintainer := "aliustek@gmail.com",
    basicSettings,
    publishSettings,
    testEnvSettings,
    gitHubPagesOrgName := "austek",
    gitHubPagesRepoName := "pact-avro-plugin",
    gitHubPagesSiteDir := (`pact-avro-plugin` / baseDirectory).value / "build" / "site",
    gitHubPagesAcceptedTextExtensions := Set(".css", ".html", ".js", ".svg", ".txt", ".woff", ".woff2", ".xml"),
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    ),
    libraryDependencies ++=
      Dependencies.compile(apacheAvro, auPactMatchers, logback, pactCore, scalaLogging, scalaPBRuntime) ++
        Dependencies.protobuf(scalaPB) ++
        Dependencies.test(scalaTest),
    dependencyOverrides ++= Seq(grpcApi, grpcCore, grpcNetty)
  )
lazy val pluginRef = LocalProject("plugin")

lazy val provider = project
  .in(file("modules/examples/provider"))
  .settings(
    basicSettings,
    Test / sbt.Keys.test := (Test / sbt.Keys.test).dependsOn(pluginRef / buildTestPluginDir).value,
    Test / envVars := Map("PACT_PLUGIN_DIR" -> ((pluginRef / target).value / "plugin").absolutePath),
    libraryDependencies ++=
      Dependencies.compile(avroCompiler, logback, pulsar4sCore, pulsar4sAvro, scalacheck) ++
        Dependencies.test(assertJCore, jUnitInterface, pactProviderJunit),
    publish / skip := false
  )

lazy val consumer = project
  .in(file("modules/examples/consumer"))
  .settings(
    basicSettings,
    Compile / avroSource := (Compile / resourceDirectory).value / "avro",
    Test / sbt.Keys.test := (Test / sbt.Keys.test).dependsOn(pluginRef / buildTestPluginDir).value,
    Test / envVars := Map("PACT_PLUGIN_DIR" -> ((pluginRef / target).value / "plugin").absolutePath),
    libraryDependencies ++=
      Dependencies.compile(avroCompiler, logback, pulsar4sCore, pulsar4sAvro, scalaLogging) ++
        Dependencies.test(assertJCore, jUnitInterface, pactConsumerJunit),
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
