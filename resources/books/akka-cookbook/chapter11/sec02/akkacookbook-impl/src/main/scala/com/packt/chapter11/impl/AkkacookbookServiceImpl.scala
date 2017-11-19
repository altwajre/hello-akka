package com.packt.chapter11.impl

import akka.Done
import com.lightbend.lagom.scaladsl.api.{ServiceCall, ServiceLocator}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.packt.chapter11.api.AkkacookbookService

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class AkkacookbookServiceImpl(per: PersistentEntityRegistry, serviceLocator: ServiceLocator)(implicit ex: ExecutionContext) extends AkkacookbookService {

  override def healthCheck() = ServiceCall { _ =>
    Await.result(serviceLocator.locate("login"), 2 seconds) match {
      case Some(serviceUri) => println(s"Service found at: [$serviceUri]")
      case None => println("Service not found")
    }
    Future.successful(Done)
  }

  override def hello(id: String) = ServiceCall { _ =>
    val ref = per.refFor[AkkacookbookEntity](id)
    ref.ask(Hello(id))
  }

  override def useGreeting(id: String) = ServiceCall { request =>
    val ref = per.refFor[AkkacookbookEntity](id)
    ref.ask(UseGreetingMessage(request.message))
  }

}