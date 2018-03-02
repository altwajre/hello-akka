package com.goticks

import akka.remote.testkit.MultiNodeConfig

object zClientServerConfig extends MultiNodeConfig {
  val frontend = role("frontend")
  val backend = role("backend")
}
