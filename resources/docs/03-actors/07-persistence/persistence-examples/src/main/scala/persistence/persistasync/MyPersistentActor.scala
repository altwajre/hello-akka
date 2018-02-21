package persistence.persistasync

import akka.persistence.PersistentActor

class MyPersistentActor extends PersistentActor {

  override def persistenceId = "my-stable-persistence-id"

  override def receiveRecover: Receive = {
    case evt ⇒ println(s"receiveRecover: $evt")
  }

  override def receiveCommand: Receive = {
    case c: String ⇒ {
      persistAsync(s"evt-$c-1") { e ⇒
        println(s"Persisted $e")
        sender() ! e
      }
      persistAsync(s"evt-$c-2") { e ⇒
        println(s"Persisted $e")
        sender() ! e
      }
      sender() ! c
    }
  }
}
