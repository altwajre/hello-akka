package dispatchers.example1

import akka.actor.Actor

import scala.concurrent.{ExecutionContext, Future}

class SeparateDispatcherFutureActor extends Actor {
  implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup("my-blocking-dispatcher")

  def receive = {
    case i: Int ⇒
      println(s"Calling blocking Future: ${i}")
      Future {
        Thread.sleep(5000) //block for 5 seconds
        println(s"Blocking future finished ${i}")
      }
  }
}
