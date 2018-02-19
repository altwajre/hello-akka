package dispatchers.example1

import akka.actor.{ActorSystem, Props}

object PrintActorApp extends App {

  val system = ActorSystem("DispatchersExample1")

  val actor1 = system.actorOf(Props(new BlockingFutureActor))
  val actor2 = system.actorOf(Props(new PrintActor))

  for (i ← 1 to 100) {
    actor1 ! i
    actor2 ! i
  }

}
