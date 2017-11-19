package com.packt.chapter11stream.impl

import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.packt.chapter11stream.api.AkkacookbookStreamService
import com.packt.chapter11.api.AkkacookbookService

import scala.concurrent.Future

/**
  * Implementation of the AkkacookbookStreamService.
  */
class AkkacookbookStreamServiceImpl(akkacookbookService: AkkacookbookService) extends AkkacookbookStreamService {
  def stream = ServiceCall { hellos =>
    Future.successful(hellos.mapAsync(8)(akkacookbookService.hello(_).invoke()))
  }
}
