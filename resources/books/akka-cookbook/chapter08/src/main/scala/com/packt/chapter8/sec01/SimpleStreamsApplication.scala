package com.packt.chapter8.sec01

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{RunnableGraph, Sink, Source}

object SimpleStreamsApplication extends App {

  implicit val actorSystem = ActorSystem("SimpleStream")
  implicit val actorMaterializer = ActorMaterializer()

  val fileList = List(
    "src/main/resources/testfile1.text",
    "src/main/resources/testfile2.txt",
    "src/main/resources/testfile3.txt"
  )

  val stream: RunnableGraph[NotUsed] = Source(fileList)
    .map(new File(_))
    .filter(_.exists())
    .filter(_.length() != 0)
    .to(Sink.foreach(file => println(s"Found file containing text: ${file.getAbsolutePath}")))

  stream.run()

  Thread.sleep(2000)
  System.exit(0)

}
