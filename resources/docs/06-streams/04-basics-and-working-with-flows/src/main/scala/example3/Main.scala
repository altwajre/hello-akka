package example3

import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import akka.{Done, NotUsed}

import scala.concurrent.Future

object Main extends App {

  // Source examples ---------------------------------------------------------------------------------------------------

  // Create a source from an Iterable
  val r1: Source[Int, NotUsed] = Source(List(1, 2, 3))

  // Create a source from a Future
  val r2: Source[String, NotUsed] = Source.fromFuture(Future.successful("Hello Streams!"))

  // Create a source from a single element
  val r3: Source[String, NotUsed] = Source.single("only one element")

  // an empty source
  val r4: Source[Nothing, NotUsed] = Source.empty

  // Sink examples -----------------------------------------------------------------------------------------------------

  // Sink that folds over the stream and returns a Future
  // of the final result as its materialized value
  val r5: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)

  // Sink that returns a Future as its materialized value,
  // containing the first element of the stream
  val r6: Sink[Int, Future[Int]] = Sink.head

  // A Sink that consumes a stream without doing anything with the elements
  val r7: Sink[Int, Future[Done]] = Sink.ignore

  // A Sink that executes a side-effecting call for every element of the stream
  val r8: Sink[String, Future[Done]] = Sink.foreach[String](println(_))

  // Wiring examples ---------------------------------------------------------------------------------------------------

  // Explicitly creating and wiring up a Source, Sink and Flow
  val r9: RunnableGraph[NotUsed] = Source(1 to 6).via(Flow[Int].map(_ * 2)).to(Sink.foreach(println(_)))

  // Starting from a Source
  val source = Source(1 to 6).map(_ * 2)
  val r10: RunnableGraph[NotUsed] = source.to(Sink.foreach(println(_)))

  // Starting from a Sink
  val sink: Sink[Int, NotUsed] = Flow[Int].map(_ * 2).to(Sink.foreach(println(_)))
  val r11: RunnableGraph[NotUsed] = Source(1 to 6).to(sink)

  // Broadcast to a sink inline
  val otherSink: Sink[Int, NotUsed] = Flow[Int].alsoTo(Sink.foreach(println(_))).to(Sink.ignore)
  val r12: RunnableGraph[NotUsed] = Source(1 to 6).to(otherSink)

}
