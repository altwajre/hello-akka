package com.packt.chapter7.sec06

import akka.actor.{ActorRef, ActorSystem}

import scala.concurrent.duration._

object ChatServerApplication extends App {
  val actorSystem = ActorSystem("ChatServer")
  actorSystem.actorOf(ChatServer.props, "chatServer")
}

object ChatClientApplication extends App {
  val actorSystem = ActorSystem("ChatServer")
  implicit val dispatcher = actorSystem.dispatcher

  val chatServerAddress = "akka.tcp://ChatServer@127.0.0.1:2552/user/chatServer"
  actorSystem.actorSelection(chatServerAddress).resolveOne(3 seconds).onSuccess {
    case chatServer: ActorRef =>
      val client = actorSystem.actorOf(ChatClient.props(chatServer), "chatClient")
      actorSystem.actorOf(ChatClientInterface.props(client), "chatClientInterface")
  }
}
