package example6

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}

object Main extends App {

  implicit val system = ActorSystem("akka-streams-examples")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val matValuePoweredSource: Source[String, ActorRef] = Source.actorRef[String](bufferSize = 100, overflowStrategy = OverflowStrategy.fail)

  val (actorRef, source) = matValuePoweredSource.preMaterialize()

  actorRef ! "Hello!"

  // pass source around for materialization
  source.runWith(Sink.foreach(println))

}
