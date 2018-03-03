package streams.quickstart

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent._

object Example1 extends App {

  implicit val system = ActorSystem("QuickStart")
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 100)
  val done: Future[Done] = source.runForeach(i ⇒ println(i))(materializer)

  // Reusing 'source':
  val factorials = source.scan(BigInt(1))((acc, next) ⇒ acc * next)
  val result: Future[IOResult] = factorials
    .map(num ⇒ ByteString(s"$num\n"))
    .runWith(FileIO.toPath(Paths.get("factorials1.txt")))

  result.onComplete(_ ⇒ system.terminate())

}
