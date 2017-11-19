organization in ThisBuild := "com.packt.chapter11"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.11.8"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % Test

lazy val `akkacookbook` = (project in file("."))
  .aggregate(`akkacookbook-api`, `akkacookbook-impl`, `akkacookbook-stream-api`, `akkacookbook-stream-impl`)

lazy val `akkacookbook-api` = (project in file("akkacookbook-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `akkacookbook-impl` = (project in file("akkacookbook-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`akkacookbook-api`)

lazy val `akkacookbook-stream-api` = (project in file("akkacookbook-stream-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `akkacookbook-stream-impl` = (project in file("akkacookbook-stream-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .dependsOn(`akkacookbook-stream-api`, `akkacookbook-api`)
