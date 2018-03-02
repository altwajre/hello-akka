package example7

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}

import scala.util.{Failure, Success}

object Main extends App {

  implicit val system = ActorSystem("ExampleSystem")

  implicit val mat = ActorMaterializer() // created from `system`

}

final class RunWithMyself extends Actor {
  implicit val mat = ActorMaterializer()

  Source.maybe
    .runWith(Sink.onComplete {
      case Success(done) ⇒ println(s"Completed: $done")
      case Failure(ex)   ⇒ println(s"Failed: ${ex.getMessage}")
    })

  def receive = {
    case "boom" ⇒
      context.stop(self) // will also terminate the stream
  }
}

final class RunForever(implicit val mat: Materializer) extends Actor {

  Source.maybe
    .runWith(Sink.onComplete {
      case Success(done) ⇒ println(s"Completed: $done")
      case Failure(ex)   ⇒ println(s"Failed: ${ex.getMessage}")
    })

  def receive = {
    case "boom" ⇒
      context.stop(self) // will NOT terminate the stream (it's bound to the system!)
  }
}