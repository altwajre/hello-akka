package hello.akka

import akka.actor.Actor
import hello.akka.Counter.{Dec, Inc}

class Counter extends Actor {

  var count = 0

  override def receive: Receive = {
    case Inc(x) => count += x
    case Dec(x) => count -= x
  }
}

object Counter {
  case class Inc(num: Int)
  case class Dec(num: Int)

}
