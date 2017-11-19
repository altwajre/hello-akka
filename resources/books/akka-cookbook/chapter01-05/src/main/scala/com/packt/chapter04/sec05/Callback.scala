package com.packt.chapter04.sec05

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

// ---------------------------------------------------------------------------------------------------------------------

object Callback extends App {
  val future = Future(1 + 2).mapTo[Int]
  future onComplete {
    case Success(result) => println(s"result is $result")
    case Failure(cause) => cause.printStackTrace()
  }
  println("Executed before callback")

  Thread.sleep(1000)

}