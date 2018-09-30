package hello.akka.router.programmatic2.group

import akka.actor.{ActorSystem, Props}
import akka.routing.RoundRobinGroup
import hello.akka.router.Worker
import hello.akka.router.Worker.Work

object Main2 extends App {

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

  val router = system.actorOf(RoundRobinGroup(workers).props(), "round-robin-router-group")

  router ! Work()
  Thread.sleep(100)

  router ! Work()
  Thread.sleep(100)

  router ! Work()
  Thread.sleep(100)

  router ! Work()
  Thread.sleep(100)

  router ! Work()
  Thread.sleep(100)

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
