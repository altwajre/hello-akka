package com.packt.chapter11.api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import play.api.libs.json.{Format, Json}

trait AkkacookbookService extends Service {

  def toUppercase: ServiceCall[String, String]

  def toLowercase: ServiceCall[String, String]

  def isEmpty(str: String): ServiceCall[NotUsed, Boolean]

  def areEqual(str1: String, str2: String): ServiceCall[NotUsed, Boolean]

  override final def descriptor = {
    import Service._
    named("stringutils").withCalls(
      call(toUppercase),
      namedCall("toLowercase", toLowercase),
      pathCall("/isEmpty/:str", isEmpty _),
      restCall(Method.GET, "/areEqual/:one/another/:other", areEqual _)
    ).withAutoAcl(true)
  }
}

case class GreetingMessage(message: String)

object GreetingMessage {
  implicit val format: Format[GreetingMessage] = Json.format[GreetingMessage]
}


case class GreetingMessageChanged(name: String, message: String)

object GreetingMessageChanged {
  implicit val format: Format[GreetingMessageChanged] = Json.format[GreetingMessageChanged]
}
