package fsm.simple

import akka.actor.ActorRef

import scala.collection._

object Protocol {

  // received events
  final case class SetTarget(ref: ActorRef)
  final case class Queue(obj: Any)
  case object Flush

  // sent events
  final case class Batch(obj: immutable.Seq[Any])

  // states
  sealed trait State
  case object Idle extends State
  case object Active extends State

  sealed trait Data
  case object Uninitialized extends Data
  final case class Todo(target: ActorRef, queue: immutable.Seq[Any]) extends Data

}
