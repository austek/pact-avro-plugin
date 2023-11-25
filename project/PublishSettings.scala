import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.packager.Keys.*
import sbt.Keys.*
import sbt.{Def, *}

object PublishSettings {

  val universalStageDir = settingKey[File]("Universal staging directory")
  val root = project in file("..")

  lazy val publishSettings: Seq[Def.Setting[?]] =
    Seq(
      executableScriptName := "pact-avro-plugin",
      Compile / doc / sources := Seq.empty,
      Compile / packageDoc / mappings := Seq.empty,
      Compile / resourceGenerators += Def.task {
        val artifactDir: File = target.value / "artifacts"
        Seq(
          generatePactPluginJson(artifactDir, version.value),
          generateInstallPluginSh(artifactDir, version.value)
        )
      },
      Universal / mappings ++= Seq(
        sourceDirectory.value / "main" / "resources" / "logback.xml" -> "conf/logback.xml"
      ),
      Universal / javaOptions ++= Seq(
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",
        "-Dfile.encoding=UTF-8",
        "-Dlogback.configurationFile=conf/logback.xml"
      ),
      Universal / packageName := s"pact-avro-plugin",
      Universal / topLevelDirectory := None
    )

  private def generatePactPluginJson(artifactDir: sbt.File, version: String): sbt.File = {
    val file = artifactDir / "pact-plugin.json"
    IO.write(file, PactPluginJson.json(version))
    file
  }

  private def generateInstallPluginSh(artifactDir: sbt.File, version: String): sbt.File = {
    val file = artifactDir / "install-plugin.sh"
    val content = """#!/usr/bin/env bash
      |
      |set -e
      |
      |VERSION="VERSION_HERE"
      |
      |case "$(uname -s)" in
      |
      |   Darwin|Linux|CYGWIN*|MINGW32*|MSYS*|MINGW*)
      |     echo '== Installing plugin =='
      |     mkdir -p ~/.pact/plugins/avro-${VERSION}
      |     wget -c https://github.com/austek/pact-avro-plugin/releases/download/v${VERSION}/pact-avro-plugin-${VERSION}.tgz \
      |     -O - | tar -xz -C ~/.pact/plugins/avro-${VERSION} --strip-components 1
      |     ;;
      |
      |   *)
      |     echo "ERROR: $(uname -s) is not a supported operating system"
      |     exit 1
      |     ;;
      |esac""".stripMargin.replaceAll("VERSION_HERE", version)
    IO.write(file, content)
    file
  }

}
