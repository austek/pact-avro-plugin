addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.3.0-M2")

dependencyOverrides += "io.grpc" % "grpc-stub" % "1.49.0"
