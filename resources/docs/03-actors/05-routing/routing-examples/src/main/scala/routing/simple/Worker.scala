package routing.simple

import akka.actor.Actor
import routing.simple.Master.Work

class Worker extends Actor {
  override def receive = {
    case Work(task) => {
      println(s"${self.toString()} starting $task")
      Thread.sleep(1000)
      println(s"${self.toString()} done $task")
    }
  }
}


