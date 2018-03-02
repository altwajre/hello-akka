package aia.testdriven

import aia.testdriven.Greeter01Test._
import akka.actor.{ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, EventFilter, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpecLike

class Greeter01Test extends TestKit(testSystem)
  with WordSpecLike
  with StopSystemAfterAll {

  "The Greeter" must {
    "say Hello World! when a Greeting(\"World\") is sent to it" in {
      val dispatcherId = CallingThreadDispatcher.Id
      val props = Props[Greeter].withDispatcher(dispatcherId)
      val greeter = system.actorOf(props)
      EventFilter
        .info(message = "Hello World!", occurrences = 1)
        .intercept {
          greeter ! Greeting("World")
        }
    }
  }
}

object Greeter01Test {
  val testSystem = {
    val config = ConfigFactory.parseString {
      """
         akka.loggers = [akka.testkit.TestEventListener]
      """
    }
    ActorSystem("testsystem", config)
  }
}


