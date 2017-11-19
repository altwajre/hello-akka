package com.packt.chapter11.impl

import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.packt.chapter11.api.AkkacookbookService

import scala.concurrent.Future

class AkkacookbookServiceImpl extends AkkacookbookService {

  override def toUppercase = ServiceCall { x =>
    Future.successful(x.toUpperCase)
  }

  override def toLowercase = ServiceCall { x =>
    Future.successful(x.toLowerCase)
  }

  override def isEmpty(str: String) = ServiceCall { _ =>
    Future.successful(str.isEmpty)
  }

  override def areEqual(str1: String, str2: String) = ServiceCall { _ =>
    Future.successful(str1 == str2)
  }

}
