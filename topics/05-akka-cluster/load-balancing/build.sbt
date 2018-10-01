name := "load-balancing"
version := "1.0"
scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.17",
  "com.typesafe.akka" %% "akka-cluster" % "2.5.17",
  "com.typesafe.akka" %% "akka-cluster-metrics" % "2.5.17"
)

