addSbtPlugin(
  ("com.github.sbt" % "sbt-ghpages" % "0.7.0")
    .exclude("org.scala-lang.modules", "scala-xml_2.12")
)

