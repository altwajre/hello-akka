package example3

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source, ZipWith}
import akka.stream.{ActorMaterializer, ClosedShape, Graph, UniformFanInShape}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Main extends App {

  implicit val system = ActorSystem("akka-streams-examples")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val pickMaxOfThree: Graph[UniformFanInShape[Int, Int], NotUsed] = GraphDSL.create() { implicit b ⇒
    import GraphDSL.Implicits._

    val zip1 = b.add(ZipWith[Int, Int, Int](math.max))
    val zip2 = b.add(ZipWith[Int, Int, Int](math.max))
    zip1.out ~> zip2.in0

    UniformFanInShape(zip2.out, zip1.in0, zip1.in1, zip2.in1)
  }

  val resultSink = Sink.head[Int]

  val g = RunnableGraph.fromGraph(GraphDSL.create(resultSink) { implicit b ⇒
    sink ⇒
      import GraphDSL.Implicits._

      // importing the partial graph will return its shape (inlets & outlets)
      val pm3: UniformFanInShape[Int, Int] = b.add(pickMaxOfThree)

      Source.single(1) ~> pm3.in(0)
      Source.single(2) ~> pm3.in(1)
      Source.single(3) ~> pm3.in(2)
      pm3.out ~> sink.in
      ClosedShape
  })

  val max: Future[Int] = g.run()
  println(Await.result(max, 300.millis))
  system.terminate()

}
