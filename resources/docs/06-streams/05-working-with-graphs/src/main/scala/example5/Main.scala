package example5

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source, Zip}

import scala.concurrent.Future

object Main extends App {

  implicit val system = ActorSystem("akka-streams-examples")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val pairUpWithToString: Flow[Int, (Int, String), NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit b ⇒
    import GraphDSL.Implicits._

    // prepare graph elements
    val broadcast = b.add(Broadcast[Int](2))
    val zip = b.add(Zip[Int, String]())

    // connect the graph
    broadcast.out(0).map(identity) ~> zip.in0
    broadcast.out(1).map(_.toString) ~> zip.in1

    // expose ports
    FlowShape(broadcast.in, zip.out)

  })

  val result: (NotUsed, Future[(Int, String)]) = pairUpWithToString.runWith(Source(List(1)), Sink.head)

  result._2.onComplete(_ => system.terminate())

}
