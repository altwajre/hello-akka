package persistence.persistasync

import akka.persistence.PersistentActor

/** Explicit shutdown message */
case object Shutdown

class SafePersistentActor extends PersistentActor {
  override def persistenceId = "safe-actor"

  override def receiveRecover: Receive = {
    case evt ⇒ println(s"receiveRecover: $evt")
  }

  override def receiveCommand: Receive = {
    case c: String ⇒
      println(s"Persisting $c")
      persist(s"handle-$c") { e =>
        println(s"Persisted $e")
        sender() ! e
      }
    case Shutdown ⇒ context.stop(self)
    case other => println(s"Received: $other")
  }

}