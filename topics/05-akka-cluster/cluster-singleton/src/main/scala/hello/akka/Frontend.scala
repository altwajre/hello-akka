package hello.akka

import akka.actor
import akka.actor.{Actor, ActorLogging}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import hello.akka.Frontend.Tick

import scala.concurrent.duration._

class Frontend extends Actor with ActorLogging {

  import context.dispatcher

  val masterProxy = context.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "/user/master",
    settings = ClusterSingletonProxySettings(context.system).withRole(None)
  ), name = "masterProxy")

  context.system.scheduler.schedule(0.second, 3.second, self, Tick)

  def receive = {
    case Tick =>
      masterProxy ! Master.Work(self, "add")
  }

}

object Frontend {

  case object Tick

  def props = actor.Props(new Frontend())

}
