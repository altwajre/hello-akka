name := "persistence-query-examples"

version := "1.0"

scalaVersion := "2.12.2"

lazy val akkaVersion = "2.5.9"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)
