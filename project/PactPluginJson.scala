import upickle.default

case class PactPluginDependency(name: String, version: String, `type`: String)

object PactPluginDependency {
  implicit val pactPluginDependencyRW: default.ReadWriter[PactPluginDependency] = upickle.default.macroRW[PactPluginDependency]
}

case class PactPluginJson(
  manifestVersion: Int,
  pluginInterfaceVersion: Int,
  name: String,
  version: String,
  executableType: String,
  entryPoint: String,
  entryPoints: Map[String, String],
  dependencies: List[PactPluginDependency]
)
object PactPluginJson {
  implicit val pactPluginJsonRW: default.ReadWriter[PactPluginJson] = upickle.default.macroRW[PactPluginJson]
  def json(v: String): String = upickle.default.write(
    PactPluginJson(
      manifestVersion = 1,
      pluginInterfaceVersion = 1,
      name = "avro",
      version = v,
      executableType = "exec",
      entryPoint = "bin/pact-avro-plugin",
      entryPoints = Map(
        "windows" -> "bin/pact-avro-plugin.bat"
      ),
      dependencies = List(
        PactPluginDependency(
          name = "jvm",
          version = "17+",
          `type` = "OSPackage"
        )
      )
    )
  )
}
