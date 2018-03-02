package streams.quickstart

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.{Done, NotUsed}

import scala.concurrent._
import scala.concurrent.duration._

object Example3 extends App {

  implicit val system = ActorSystem("QuickStart")
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 100)
  val factorials = source.scan(BigInt(1))((acc, next) ⇒ acc * next)

  val result: Future[Done] = factorials
    .zipWith(Source(0 to 100))((num, idx) ⇒ s"$idx! = $num")
    .throttle(2, 1.second, 1, ThrottleMode.shaping) // Throttle input to 2 elements per second
    .runForeach(println)

  result.onComplete(_ ⇒ system.terminate())

}
