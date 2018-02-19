# Fault Tolerance - Overview
- Each actor is the supervisor of its children, and as such each actor defines fault handling supervisor strategy. 
- This strategy cannot be changed afterwards as it is an integral part of the actor system’s structure.

# Fault Handling in Practice
- First, let us look at a sample that illustrates one way to handle data store errors:
    - Which is a typical source of failure in real world applications. 
- Of course it depends on the actual application what is possible to do when the data store is unavailable:
    - But in this sample we use a best effort re-connect approach.
- [Read the following source code](./fault-tolerance-examples/src/main/scala/faulttolerance/example1/FaultHandlingDocSample.scala). 
- [View the diagrams here](https://doc.akka.io/docs/akka/2.5.9/fault-tolerance-sample.html?language=scala)
- The inlined comments explain the different pieces of the fault handling and why they are added. 
- It is also highly recommended to run this sample as it is easy to follow the log output to understand what is happening at runtime.

# Creating a Supervisor Strategy
- The following sections explain the fault handling mechanism and alternatives in more depth.
- For the sake of demonstration let us consider the following strategy:
```scala
override val supervisorStrategy =
  OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
    case _: ArithmeticException      ⇒ Resume
    case _: NullPointerException     ⇒ Restart
    case _: IllegalArgumentException ⇒ Stop
    case _: Exception                ⇒ Escalate
  }
```
- We have chosen a few well-known exception types:
    - In order to demonstrate the application of the fault handling directives described in supervision. 
- First off, it is a **one-for-one strategy**:
    - Meaning that each child is treated separately.
- An **all-for-one strategy** works very similarly:
    - The only difference is that any decision is applied to all children of the supervisor, not only the failing one. 
- In the above example, `10` and `1 minute` are passed to the `maxNrOfRetries` and `withinTimeRange` parameters respectively.
- Which means that the strategy restarts a child up to 10 restarts per minute. 
- The child actor is stopped if the restart count exceeds `maxNrOfRetries` during the `withinTimeRange` duration.
- There are special values for these parameters. If you specify:
    - `-1` to `maxNrOfRetries`, and `Duration.inf` to `withinTimeRange`:
        - Then the child is always restarted without any limit.
    - `-1` to `maxNrOfRetries`, and a non-infinite `Duration` to `withinTimeRange`:
        - `maxNrOfRetries` is treated as `1`.
    - A non-negative number to `maxNrOfRetries` and `Duration.inf` to `withinTimeRange`:
        - `withinTimeRange` is treated as infinite duration.
        - No matter how long it takes, once the restart count exceeds `maxNrOfRetries`, the child actor is stopped.
- The match statement which forms the bulk of the body is of type `Decider`:
    - Which is a `PartialFunction[Throwable, Directive]`. 
- This is the piece which maps child failure types to their corresponding directives.
- If the strategy is declared inside the supervising actor (as opposed to within a companion object):
    - Its decider has access to all internal state of the actor in a thread-safe fashion.
    - And can obtain a reference to the currently failed child.
        - Available as the sender of the failure message.
        
## Default Supervisor Strategy
- `Escalate` is used if the defined strategy doesn’t cover the exception that was thrown.
- When the supervisor strategy is not defined for an actor the following exceptions are handled by default:
    - `ActorInitializationException`: Stop child.
    - `ActorKilledException`: Stop child.
    - `DeathPactException`: Stop child.
    - `Exception`: Restart child.
    - Other types of `Throwable`: Escalated to parent.
- If the exception escalate all the way up to the root guardian it will handle it in the same way as the default strategy defined above.
- You can combine your own strategy with the default strategy:
```scala
override val supervisorStrategy =
  OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
    case _: ArithmeticException ⇒ Resume
    case t ⇒
      super.supervisorStrategy.decider.applyOrElse(t, (_: Any) ⇒ Escalate)
  }
```

## Stopping Supervisor Strategy
- Closer to the _Erlang_ way is the strategy to:
    - Just stop children when they fail.
    - Then take corrective action in the supervisor when DeathWatch signals the loss of the child. 
- This strategy is also provided pre-packaged as `SupervisorStrategy.stoppingStrategy`:
    - With an accompanying `StoppingSupervisorStrategy` configurator to be used when you want the `/user` guardian to apply it.

## Logging of Actor Failures
- By default the `SupervisorStrategy` logs failures unless they are escalated. 
- Escalated failures are supposed to be handled, and potentially logged, at a level higher in the hierarchy.
- You can mute the default logging of a `SupervisorStrategy` by setting `loggingEnabled` to `false` when instantiating it. 
- Customized logging can be done inside the `Decider`. 
- The reference to the currently failed child is available as the `sender`:
    - When the `SupervisorStrategy` is declared inside the supervising actor.
- You may also customize the logging in your own `SupervisorStrategy` implementation by overriding the `logFailure` method.

# Supervision of Top-Level Actors
- Toplevel actors means those which are created using `system.actorOf()`, and they are children of the [User Guardian](../../02-general-concepts/04-supervision-and-monitoring#user-the-guardian-actor). 
- There are no special rules applied in this case, the guardian simply applies the configured strategy.

# Test Application
- The following section shows the effects of the different directives in practice, where a test setup is needed. 
- See [Example 2](./fault-tolerance-examples/src/main/scala/faulttolerance/example2)
- First off, we need a suitable supervisor:
```scala
class Supervisor extends Actor {
  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case _: ArithmeticException      ⇒ Resume
      case _: NullPointerException     ⇒ Restart
      case _: IllegalArgumentException ⇒ Stop
      case _: Exception                ⇒ Escalate
    }

  def receive = {
    case p: Props ⇒ sender() ! context.actorOf(p)
  }
}
```
- This supervisor will be used to create a child, with which we can experiment:
```scala
class Child extends Actor {
  var state = 0
  def receive = {
    case ex: Exception ⇒ throw ex
    case x: Int        ⇒ state = x
    case "get"         ⇒ sender() ! state
  }
}
```
- The test is easier by using the utilities described in [Testing Actor Systems](../11-testing-actor-systems):
- Where `TestProbe` provides an actor ref useful for receiving and inspecting replies.
```scala
class FaultHandlingDocSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem(
    "FaultHandlingDocSpec",
    ConfigFactory.parseString("""
      akka {
        loggers = ["akka.testkit.TestEventListener"]
        loglevel = "WARNING"
      }
      """)))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A supervisor" must {
    "apply the chosen strategy for its child" in {
      // code here
    }
  }
}
```
- Let us create actors:
```scala
val supervisor = system.actorOf(Props[Supervisor], "supervisor")

supervisor ! Props[Child]
val child = expectMsgType[ActorRef] // retrieve answer from TestKit’s testActor
```
- The first test shall demonstrate the `Resume` directive.
- So we try it out by setting some non-initial state in the actor and have it fail:
```scala
child ! 42 // set state to 42
child ! "get"
expectMsg(42)

child ! new ArithmeticException // crash it
child ! "get"
expectMsg(42)
```
- As you can see the value `42` survives the fault handling directive. 
- Now, if we change the failure to a more serious `NullPointerException`, that will no longer be the case:
```scala
child ! new NullPointerException // crash it harder
child ! "get"
expectMsg(0)
```
- And finally in case of the fatal `IllegalArgumentException` the child will be terminated by the supervisor:
```scala
watch(child) // have testActor watch “child”
child ! new IllegalArgumentException // break it
expectMsgPF() { case Terminated(`child`) ⇒ () }
```
- Up to now the supervisor was completely unaffected by the child’s failure, because the directives set did handle it. 
- In case of an `Exception`, this is not true anymore and the supervisor escalates the failure.
```scala
supervisor ! Props[Child] // create new child
val child2 = expectMsgType[ActorRef]
watch(child2)
child2 ! "get" // verify it is alive
expectMsg(0)

child2 ! new Exception("CRASH") // escalate failure
expectMsgPF() {
  case t @ Terminated(`child2`) if t.existenceConfirmed ⇒ ()
}
```
- The supervisor itself is supervised by the top-level actor provided by the `ActorSystem`:
    - Which has the default policy to restart in case of all `Exception` cases.
    - With the notable exceptions of `ActorInitializationException` and `ActorKilledException`. 
- Since the default directive in case of a restart is to kill all children:
    - We expected our poor child not to survive this failure.
- In case this is not desired, we need to use a different supervisor which overrides this behavior.
```scala
class Supervisor2 extends Actor {
  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case _: ArithmeticException      ⇒ Resume
      case _: NullPointerException     ⇒ Restart
      case _: IllegalArgumentException ⇒ Stop
      case _: Exception                ⇒ Escalate
    }

  def receive = {
    case p: Props ⇒ sender() ! context.actorOf(p)
  }
  // override default to kill all children during restart
  override def preRestart(cause: Throwable, msg: Option[Any]) {}
}
```
- With this parent, the child survives the escalated restart, as demonstrated in the last test:
```scala
val supervisor2 = system.actorOf(Props[Supervisor2], "supervisor2")

supervisor2 ! Props[Child]
val child3 = expectMsgType[ActorRef]

child3 ! 23
child3 ! "get"
expectMsg(23)

child3 ! new Exception("CRASH")
child3 ! "get"
expectMsg(0)
```



































