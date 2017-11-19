package com.packt.chapter11.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.packt.chapter11.api.AkkacookbookService
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents

class AkkacookbookLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new AkkacookbookApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new AkkacookbookApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[AkkacookbookService])
}

abstract class AkkacookbookApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with CassandraPersistenceComponents
    with LagomKafkaComponents
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer = serverFor[AkkacookbookService](wire[AkkacookbookServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry = AkkacookbookSerializerRegistry

  // Register the akkacookbook persistent entity
  persistentEntityRegistry.register(wire[AkkacookbookEntity])
}
