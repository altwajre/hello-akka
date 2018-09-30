package hello.akka.router.programmatic1.pool

import akka.actor.{ActorSystem, Props}
import hello.akka.router.Worker.Work

object Main extends App {

  val system = ActorSystem("routing")

  val router = system.actorOf(Props[Router], "router")

  Thread.sleep(1000)

  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()

  Thread.sleep(2000)
  system.terminate()

}
