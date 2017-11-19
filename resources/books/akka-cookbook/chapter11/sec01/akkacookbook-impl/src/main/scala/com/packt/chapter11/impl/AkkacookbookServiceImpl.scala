package com.packt.chapter11.impl

import akka.Done
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.packt.chapter11.api.AkkacookbookService

import scala.concurrent.Future

class AkkacookbookServiceImpl(persistentEntityRegistry: PersistentEntityRegistry) extends AkkacookbookService {

  override def hello(id: String) = ServiceCall { _ =>
    // Look up the akkacookbook entity for the given ID.
    val ref = persistentEntityRegistry.refFor[AkkacookbookEntity](id)

    // Ask the entity the Hello command.
    ref.ask(Hello(id))
  }

  override def useGreeting(id: String) = ServiceCall { request =>
    // Look up the akkacookbook entity for the given ID.
    val ref = persistentEntityRegistry.refFor[AkkacookbookEntity](id)

    // Tell the entity to use the greeting message specified.
    ref.ask(UseGreetingMessage(request.message))
  }

  override def healthCheck() = ServiceCall { _ =>
    Future.successful(Done)
  }

}
