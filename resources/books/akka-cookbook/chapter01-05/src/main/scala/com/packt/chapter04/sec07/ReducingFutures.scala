package com.packt.chapter04.sec07

import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

// ---------------------------------------------------------------------------------------------------------------------

object ReducingFutures extends App {
  val timeout = Timeout(10 seconds)
  val listOfFutures = (1 to 3).map(Future(_))
  val finalFuture = Future.reduce(listOfFutures)(_ + _)
  println(s"sum of numbers from 1 to 3 is ${Await.result(finalFuture, 10 seconds)}")
}