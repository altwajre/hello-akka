package hello.akka.router.fromconfig.group

import akka.actor.{ActorSystem, Props}
import akka.routing.FromConfig
import hello.akka.router.Worker
import hello.akka.router.Worker.Work

object Main extends App {

  val system = ActorSystem("routing")

  val router = system.actorOf(FromConfig.props(Props[Worker]), "random-router-pool")

  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()

  Thread.sleep(1000)
  system.terminate()

}

