package hello.akka.router.fromconfig.pool

import akka.actor.{ActorSystem, Props}
import akka.routing.FromConfig
import hello.akka.router.Worker
import hello.akka.router.Worker.Work

object Main extends App {

  val system = ActorSystem("routing")

  val router = system.actorOf(FromConfig.props(Props[Worker]), "round-robin-router-group")

  router ! Work()
  Thread.sleep(100)

  router ! Work()
  Thread.sleep(100)

  router ! Work()
  Thread.sleep(100)

  router ! Work()
  Thread.sleep(100)


  Thread.sleep(1000)
  system.terminate()

}

