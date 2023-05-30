import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.SettingsHelper.makeDeploymentSettings
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.packageZipTarball
import sbt.Keys._
import sbt._

object PublishSettings {

  lazy val publishSettings: Seq[Def.Setting[_]] =
    Seq(
      executableScriptName := "pact-avro-plugin",
      Compile / doc / sources := Seq.empty,
      Compile / packageDoc / mappings := Seq.empty,
      Compile / resourceGenerators += Def.task {
        val file = (Compile / resourceManaged).value / "pact-plugin.json"
        IO.write(file, PactPluginJson.json(version.value))
        Seq(file)
      },
      Universal / mappings ++= Seq(
        sourceDirectory.value / "main" / "resources" / "logback.xml" -> "conf/logback.xml",
        (Compile / resourceManaged).value / "pact-plugin.json" -> "pact-plugin.json"
      ),
      Universal / javaOptions ++= Seq(
        "-Dfile.encoding=UTF-8",
        "-Dlogback.configurationFile=conf/logback.xml"
      ),
      Universal / packageName := s"pact-avro-plugin-${version.value}",
      Universal / topLevelDirectory := Some(s"avro-${version.value}")
    )

}
