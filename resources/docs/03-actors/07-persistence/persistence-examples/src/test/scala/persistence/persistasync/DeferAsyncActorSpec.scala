package persistence.persistasync

import java.util.Date

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class DeferAsyncActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("DeferAsyncActorSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "deferAsync" must {
    "work" in {
      val persistentActor = system.actorOf(Props[DeferAsyncActor], "DeferAsyncActor")

      val date = new Date().toString

      persistentActor ! s"A - $date"
      persistentActor ! s"B - $date"

      // Expect guaranteed order of received messages:
      expectMsg(s"A - $date")
      expectMsg(s"B - $date")
      expectMsg(s"evt-A - $date-1")
      expectMsg(s"evt-A - $date-2")
      expectMsg(s"evt-A - $date-3")
      expectMsg(s"evt-B - $date-1")
      expectMsg(s"evt-B - $date-2")
      expectMsg(s"evt-B - $date-3")

      expectNoMessage(1.second)

    }
  }
}