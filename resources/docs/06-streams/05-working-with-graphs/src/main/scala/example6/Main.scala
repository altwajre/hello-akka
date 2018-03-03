package example6

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Merge, Sink, Source}

import scala.concurrent.Future

object Main extends App {

  implicit val system = ActorSystem("akka-streams-examples")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  // Fan-in ------------------------------------------------------------------------------------------------------------

  val sourceOne = Source(List(1))
  val sourceTwo = Source(List(2))
  val merged: Source[Int, NotUsed] = Source.combine(sourceOne, sourceTwo)(Merge(_))

  val mergedResult: Future[Int] = merged.runWith(Sink.fold(0)(_ + _))

  // Fan-out -----------------------------------------------------------------------------------------------------------

  val actorRef = ???

  val sendRemotely = Sink.actorRef(actorRef, "Done")
  val localProcessing = Sink.foreach[Int](_ ⇒ /* do something usefull */ ())

  val sink: Sink[Int, NotUsed] = Sink.combine(sendRemotely, localProcessing)(Broadcast[Int](_))

  Source(List(0, 1, 2)).runWith(sink)

}
