package hello.akka.router.programmatic2.group

import akka.actor.{ActorSystem, Props}
import akka.routing.RandomGroup
import hello.akka.router.Worker
import hello.akka.router.Worker.Work

object Main1 extends App {

  val system = ActorSystem("routing")

  system.actorOf(Props[Worker], "w1")
  system.actorOf(Props[Worker], "w2")
  system.actorOf(Props[Worker], "w3")
  system.actorOf(Props[Worker], "w4")
  system.actorOf(Props[Worker], "w5")

  val workers = List(
    "/user/w1",
    "/user/w2",
    "/user/w3",
    "/user/w4",
    "/user/w5"
  )

  val router = system.actorOf(RandomGroup(workers).props(), "random-router-group")

  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()
  router ! Work()

  Thread.sleep(1000)
  system.terminate()

}
