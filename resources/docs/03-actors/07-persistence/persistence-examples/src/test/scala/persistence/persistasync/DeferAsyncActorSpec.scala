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

      // We'll receive exactly 8 messages
      (1 to 8).foreach { _ =>
        expectMsgPF() {
          case msg ⇒ println(s"Received $msg")
        }
      }
      expectNoMessage(1.second)

    }
  }
}