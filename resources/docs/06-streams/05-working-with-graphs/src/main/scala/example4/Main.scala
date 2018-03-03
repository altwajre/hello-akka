package example4

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{GraphDSL, Sink, Source, Zip}

import scala.concurrent.Future

object Main extends App {

  implicit val system = ActorSystem("akka-streams-examples")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val pairs: Source[(Int, Int), NotUsed] = Source.fromGraph(GraphDSL.create() { implicit b ⇒
    import GraphDSL.Implicits._

    // prepare graph elements
    val zip: FanInShape2[Int, Int, (Int, Int)] = b.add(Zip[Int, Int]())

    def ints = Source.fromIterator(() ⇒ Iterator.from(1))

    // connect the graph
    ints.filter(_ % 2 != 0) ~> zip.in0
    ints.filter(_ % 2 == 0) ~> zip.in1

    // expose port
    SourceShape(zip.out)

  })

  val firstPair: Future[(Int, Int)] = pairs.runWith(Sink.head)

}
