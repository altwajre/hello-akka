package streams.quickstart

import java.nio.file.Paths

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString

import scala.concurrent._

object Example2 extends App {

  implicit val system = ActorSystem("QuickStart")
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 100)
  val factorials = source.scan(BigInt(1))((acc, next) ⇒ acc * next)

  def lineSink(filename: String): Sink[String, Future[IOResult]] = Flow[String]
    .map(s ⇒ ByteString(s + "\n"))
    .toMat(FileIO.toPath(Paths.get(filename)))(Keep.right)

  val result: Future[IOResult] = factorials.map(_.toString).runWith(lineSink("factorials2.txt"))

  result.onComplete(_ ⇒ system.terminate())

}
