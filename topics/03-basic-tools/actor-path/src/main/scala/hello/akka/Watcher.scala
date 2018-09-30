package hello.akka

import akka.actor.{Actor, ActorIdentity, ActorRef, Identify}

class Watcher extends Actor {

  var counterRef: ActorRef = _

  val selection = context.actorSelection("/user/counter")

  selection ! Identify(None)

  override def receive: Receive = {
    case ActorIdentity(_, Some(ref)) =>
      println(s"Watcher: reference for counter is $ref")
    case ActorIdentity(_, None) =>
      println(s"Watcher: reference for counter could not be found")
  }

}
