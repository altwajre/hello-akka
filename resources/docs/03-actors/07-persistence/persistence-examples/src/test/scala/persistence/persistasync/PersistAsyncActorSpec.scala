package persistence.persistasync

import java.util.Date

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class PersistAsyncActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("PersistentAsyncActorSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "persistAsync" must {
    "work" in {
      val persistentActor = system.actorOf(Props[PersistAsyncActor], "PersistAsyncActor")

      val date = new Date().toString

      persistentActor ! s"A - $date"
      persistentActor ! s"B - $date"

      // We'll receive exactly 6 messages
      (1 to 6).foreach { _ =>
        expectMsgPF() {
          case msg ⇒ println(s"Received $msg")
        }
      }
      expectNoMessage(1.second)

    }
  }
}