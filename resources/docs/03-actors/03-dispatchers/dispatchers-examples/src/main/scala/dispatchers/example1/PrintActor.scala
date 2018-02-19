package dispatchers.example1

import akka.actor.Actor

class PrintActor extends Actor {
  def receive = {
    case i: Int ⇒
      println(s"PrintActor: ${i}")
  }
}

