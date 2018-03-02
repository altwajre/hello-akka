package example2

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future

object Main extends App {

  implicit val system = ActorSystem("akka-streams-examples")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  // connect the Source to the Sink, obtaining a RunnableGraph
  val sink = Sink.fold[Int, Int](0)(_ + _)
  val runnable: RunnableGraph[Future[Int]] = Source(1 to 10).toMat(sink)(Keep.right)

  // get the materialized value of the FoldSink
  val sum1: Future[Int] = runnable.run()
  val sum2: Future[Int] = runnable.run()

  // sum1 and sum2 are different Futures!
  sum1.foreach(println)
  sum2.foreach(println)

  sum1.zip(sum2).onComplete(_ => system.terminate())

}
