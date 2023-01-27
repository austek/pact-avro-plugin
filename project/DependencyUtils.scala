import sbt.ModuleID

trait DependencyUtils {
  def cluster(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "multi-jvm")
  def compile(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def container(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")
  def example(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "example")
  def integration(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "it")
  def feature(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "ft")
  def protobuf(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "protobuf")
  def provided(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def runtime(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def test(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
}
