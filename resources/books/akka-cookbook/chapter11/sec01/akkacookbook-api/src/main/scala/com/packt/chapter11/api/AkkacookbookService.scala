package com.packt.chapter11.api

import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import play.api.libs.json.{Format, Json}

// ---------------------------------------------------------------------------------------------------------------------

case class GreetingMessage(message: String)

object GreetingMessage {
  implicit val format: Format[GreetingMessage] = Json.format[GreetingMessage]
}

case class GreetingMessageChanged(name: String, message: String)

object GreetingMessageChanged {
  implicit val format: Format[GreetingMessageChanged] = Json.format[GreetingMessageChanged]
}

// ---------------------------------------------------------------------------------------------------------------------

trait AkkacookbookService extends Service {

  def hello(id: String): ServiceCall[NotUsed, String]

  def useGreeting(id: String): ServiceCall[GreetingMessage, Done]

  def healthCheck(): ServiceCall[NotUsed, Done]

  override final def descriptor = {
    import Service._
    named("akkacookbook").withCalls(
      pathCall("/api/hello/:id", hello _),
      pathCall("/api/hello/:id", useGreeting _),
      pathCall("/api/healthcheck", healthCheck _)
    ).withAutoAcl(true)
  }
}

