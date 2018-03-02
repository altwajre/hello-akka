package example4

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future

object Main extends App {

  implicit val system = ActorSystem("akka-streams-examples")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  // Operator fusion ---------------------------------------------------------------------------------------------------

  Source(List(1, 2, 3))
    .map(_ + 1).async // <------------- Async boundary here
    .map(_ * 2)
    .to(Sink.ignore)

}
