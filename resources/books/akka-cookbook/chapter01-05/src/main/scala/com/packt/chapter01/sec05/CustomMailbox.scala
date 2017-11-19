package com.packt.chapter01.sec05

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.dispatch.{Envelope, MailboxType, MessageQueue, ProducesMessageQueue}
import com.packt.utils.terminate
import com.typesafe.config.Config

// ---------------------------------------------------------------------------------------------------------------------

class MyMessageQueue extends MessageQueue {

  private final val queue = new ConcurrentLinkedQueue[Envelope]()

  override def enqueue(receiver: ActorRef, handle: Envelope): Unit = {
    if (handle.sender.path.name == "Friend") {
      handle.sender ! "Hey friend, processing your request... (from 'MyMessageQueue')"
      queue.offer(handle)
    } else {
      handle.sender ! "I don't talk to strangers, I can't process your request (from 'MyMessageQueue')"
    }
  }

  override def dequeue(): Envelope = queue.poll()

  override def numberOfMessages: Int = queue.size()

  override def hasMessages: Boolean = !queue.isEmpty

  override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    println("cleaning up")
    while (hasMessages)
      deadLetters.enqueue(owner, dequeue())
  }

}

// ---------------------------------------------------------------------------------------------------------------------

class MyUnboundedMailbox extends MailboxType with ProducesMessageQueue[MyMessageQueue] {

  def this(settings: ActorSystem.Settings, config: Config) = {
    this() // RESEARCH: why?
  }

  // the create method is called to create the MessageQueue
  override def create(owner: Option[ActorRef], system: Option[ActorSystem]) = new MyMessageQueue

}

// ---------------------------------------------------------------------------------------------------------------------

class MySpecialActor extends Actor {
  override def receive = {
    case msg: String =>
      println(s"Got msg on '${self.path.name}': $msg")
  }
}

// ---------------------------------------------------------------------------------------------------------------------

class MyActor extends Actor {

  override def receive = {

    case (msg: String, actorRef: ActorRef) =>
      println(s"Got msg on '${self.path.name}': $msg")
      println(s"Passing msg to '${actorRef.path.name}'")
      actorRef ! msg

    case response: String =>
      println(s"Got response: $response")

  }

}

// ---------------------------------------------------------------------------------------------------------------------

object CustomMailbox extends App {

  val actorSystem = ActorSystem("HelloAkka")

  val specialActor = actorSystem.actorOf(Props[MySpecialActor].withDispatcher("custom-dispatcher"), "SpecialActor")

  val strangerActor = actorSystem.actorOf(Props[MyActor], "Stranger")

  val friendActor = actorSystem.actorOf(Props[MyActor], "Friend")

  println("------------------------------------------------------------")

  strangerActor ! ("Hello stranger", specialActor)

  Thread.sleep(1000)
  println("------------------------------------------------------------")

  friendActor ! ("Hello friend", specialActor)

  Thread.sleep(1000)
  println("------------------------------------------------------------")

  terminate(actorSystem, delay = 0)

}
