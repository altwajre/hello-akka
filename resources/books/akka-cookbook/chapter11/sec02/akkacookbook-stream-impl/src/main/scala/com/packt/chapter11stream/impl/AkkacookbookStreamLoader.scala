package com.packt.chapter11stream.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.packt.chapter11stream.api.AkkacookbookStreamService
import com.packt.chapter11.api.AkkacookbookService
import com.softwaremill.macwire._

class AkkacookbookStreamLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new AkkacookbookStreamApplication(context) {
      override def serviceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new AkkacookbookStreamApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[AkkacookbookStreamService])
}

abstract class AkkacookbookStreamApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer = serverFor[AkkacookbookStreamService](wire[AkkacookbookStreamServiceImpl])

  // Bind the AkkacookbookService client
  lazy val akkacookbookService = serviceClient.implement[AkkacookbookService]
}
