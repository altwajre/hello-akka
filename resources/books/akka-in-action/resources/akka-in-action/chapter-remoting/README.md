# 6.2.2. Remote REPL action 
```bash
cd akka-in-action/chapter-remoting

sbt console
```

## Step 1 - Start backend on Terminal 1
```
// :paste
val conf =
  """
  akka {
    actor {
      provider = "akka.remote.RemoteActorRefProvider"
    }
    remote {
      enabled - transports = ["akka.remote.netty.tcp"]
      netty.tcp {
        hostname = "0.0.0.0"
        port = 2551
      }
    }
  }
"""

import akka.actor._
import com.typesafe.config._
val config = ConfigFactory.parseString(conf)
val backend = ActorSystem("backend", config)

class Simple extends Actor {
  def receive = {
    case m => println(s"received $m!")
  }
}
backend.actorOf(Props[Simple], "simple")

// CTRL-D
```

## Step 2 - Start frontend on Terminal 2
```
// :paste
val conf =
  """
  akka {
    actor {
      provider = "akka.remote.RemoteActorRefProvider"
    }
    remote {
      enabled - transports = ["akka.remote.netty.tcp"]
      netty.tcp {
        hostname = "0.0.0.0"
        port = 2552
      }
    }
  }
"""

import akka.actor._
import com.typesafe.config._
val config = ConfigFactory.parseString(conf)
val frontend = ActorSystem("frontend", config)

// CTRL-D
```

## Step 3 - Lookup Simple Actor on Terminal 2
```
// :paste
val path = "akka.tcp://backend@0.0.0.0:2551/user/simple"
val simple = frontend.actorSelection(path)

// CTRL-D
```

## Step 4 - Send message to Simple Actor on Terminal 2
```
simple ! "Hello Remote World!"
```

------------------------------------------------------------------------------------------------------------------------

# 6.2.3. Remote lookup 

## Backend
```bash
sbt "runMain com.goticks.BackendMain"
```

## Frontend
```bash
sbt "runMain com.goticks.FrontendMain"

http POST localhost:5000/events/RHCP tickets:=10
http POST localhost:5000/events/DjMadlib tickets:=15
http GET localhost:5000/events
http POST localhost:5000/events/RHCP/tickets tickets:=2
http GET localhost:5000/events
http POST localhost:5000/events/RHCP/tickets tickets:=8
http GET localhost:5000/events
http POST localhost:5000/events/RHCP/tickets tickets:=1
```

------------------------------------------------------------------------------------------------------------------------

# 6.2.4. Remote deployment 

## Backend
```bash
sbt "runMain com.goticks.BackendRemoteDeployMain"
```

## Frontend
```bash
sbt "runMain com.goticks.FrontendRemoteDeployMain"

http POST localhost:5000/events/RHCP tickets:=10
http POST localhost:5000/events/DjMadlib tickets:=15
http GET localhost:5000/events
http POST localhost:5000/events/RHCP/tickets tickets:=2
http GET localhost:5000/events
http POST localhost:5000/events/RHCP/tickets tickets:=8
http GET localhost:5000/events
http POST localhost:5000/events/RHCP/tickets tickets:=1
```

- If you restart the backend, things are broken.
- See next section.

## Frontend with Watcher
```bash
sbt "runMain com.goticks.FrontendRemoteDeployWatchMain"

http POST localhost:5000/events/RHCP tickets:=10
http POST localhost:5000/events/DjMadlib tickets:=15
http GET localhost:5000/events
http POST localhost:5000/events/RHCP/tickets tickets:=2
http GET localhost:5000/events
http POST localhost:5000/events/RHCP/tickets tickets:=8
http GET localhost:5000/events
http POST localhost:5000/events/RHCP/tickets tickets:=1
```
------------------------------------------------------------------------------------------------------------------------

















