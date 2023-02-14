import sbt.Keys._
import sbt.{Def, _}

import java.util

object BuildSettings {
  val env: util.Map[String, String] = System.getenv()
  val scala213 = "2.13.10"

  lazy val basicSettings: Seq[Def.Setting[_]] = Seq(
    homepage := Some(new URL("https://github.com/austek/pact-avro-plugin")),
    organization := "io.pact",
    description := "Pact Avro Plugin",
    scalaVersion := scala213,
    resolvers += Resolver.mavenLocal,
    resolvers ++= Resolver.sonatypeOssRepos("releases"),
    javacOptions ++= Seq("-encoding", "UTF-8"),
    Test / fork := true,
    scalacOptions --= {
      if (sys.env.contains("CI"))
        Seq.empty
      else
        Seq("-Xfatal-warnings")
    }
  )
}
