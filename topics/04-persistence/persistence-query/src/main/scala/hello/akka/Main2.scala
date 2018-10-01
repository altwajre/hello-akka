package hello.akka

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source

object Main2 extends App {

  val system = ActorSystem("persistence-query")

  implicit val mat = ActorMaterializer()(system)

  val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal] {
    LeveldbReadJournal.Identifier
  }

  val evts: Source[EventEnvelope, NotUsed] = queries
      .eventsByPersistenceId("account")

  evts.runForeach { evt =>
    println(s"Event $evt")
  }

  Thread.sleep(1000)
  system.terminate()

}
