import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.11.7",
      version := "1.0"
    )),
    name := "Hello-Akka",
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "com.typesafe.akka" % "akka-actor_2.11" % "2.4.4",
    libraryDependencies += "com.typesafe.akka" % "akka-agent_2.11" % "2.4.4",
    libraryDependencies += "com.typesafe.akka" % "akka-testkit_2.11" % "2.4.4",
    libraryDependencies += "org.scalatest" % "scalatest_2.11" % "3.0.1"
  )
