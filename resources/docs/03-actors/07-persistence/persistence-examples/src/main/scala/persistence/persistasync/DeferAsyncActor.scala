package persistence.persistasync

import akka.persistence.PersistentActor

class DeferAsyncActor extends PersistentActor {

  override def persistenceId = "my-stable-persistence-id"

  override def receiveRecover: Receive = {
    case evt ⇒ println(s"receiveRecover: $evt")
  }

  override def receiveCommand: Receive = {
    case c: String ⇒
      persistAsync(s"evt-$c-1") { e ⇒
        println(s"Persisted $e")
        sender() ! e
      }
      persistAsync(s"evt-$c-2") { e ⇒
        println(s"Persisted $e")
        sender() ! e
      }
      deferAsync(s"evt-$c-3") { e ⇒
        println(s"Deferred $e")
        sender() ! e
      }
      sender() ! c
  }
}
