package persistence.atleastonce

import akka.actor.{Actor, ActorSelection}
import akka.persistence.{AtLeastOnceDelivery, PersistentActor}

case class Msg(deliveryId: Long, s: String)
case class Confirm(deliveryId: Long)

sealed trait Evt
case class MsgSent(s: String) extends Evt
case class MsgConfirmed(deliveryId: Long) extends Evt

class MyPersistentActor(destinationPath: String)
  extends PersistentActor with AtLeastOnceDelivery {

  val destination: ActorSelection = context.system.actorSelection(destinationPath)

  override def persistenceId: String = "persistence-id"

  override def receiveCommand: Receive = {
    case s: String           ⇒ persist(MsgSent(s))(updateState)
    case Confirm(deliveryId) ⇒ persist(MsgConfirmed(deliveryId))(updateState)
  }

  override def receiveRecover: Receive = {
    case evt: Evt ⇒ updateState(evt)
  }

  def updateState(evt: Evt): Unit = evt match {
    case MsgSent(s) ⇒
      deliver(destination)(deliveryId ⇒ Msg(deliveryId, s))

    case MsgConfirmed(deliveryId) ⇒ {
      println(s"Confirmed $deliveryId")
      confirmDelivery(deliveryId)
    }
  }
}

class MyDestination extends Actor {

  def receive = {
    case Msg(deliveryId, s) ⇒
      println("Confirming message delivery")
      // ...
      sender() ! Confirm(deliveryId)
  }
}
