package example2

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape}

import scala.concurrent.Future

object Main extends App {

  implicit val system = ActorSystem("akka-streams-examples")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val topHeadSink: Sink[Int, Future[Int]] = Sink.head[Int]
  val bottomHeadSink: Sink[Int, Future[Int]] = Sink.head[Int]
  val sharedDoubler: Flow[Int, Int, NotUsed] = Flow[Int].map(_ * 2)

  val rg: RunnableGraph[(Future[Int], Future[Int])] = RunnableGraph.fromGraph(GraphDSL.create(topHeadSink, bottomHeadSink)((_, _)) { implicit builder =>
    (topHS, bottomHS) =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))

      // @formatter:off
      Source.single(1) ~> broadcast.in

                          broadcast.out(0) ~> sharedDoubler ~> topHS.in
                          broadcast.out(1) ~> sharedDoubler ~> bottomHS.in
      // @formatter:on

      ClosedShape
  })

}
