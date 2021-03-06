package persistence.persistasync

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

      persistentActor ! "a"
      persistentActor ! "b"

      // Expect guaranteed order of received messages:
      expectMsg("a")
      expectMsg("b")
      expectMsg("evt-a-1")
      expectMsg("evt-a-2")
      expectMsg("evt-a-3")
      expectMsg("evt-b-1")
      expectMsg("evt-b-2")
      expectMsg("evt-b-3")

      expectNoMessage(1.second)

    }
  }
}