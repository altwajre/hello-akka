# Configuration - Overview
- You can start using Akka without defining any configuration, since sensible default values are provided. 
- Later on you might need to amend the settings to change the default behavior or adapt for specific runtime environments. 
- Typical examples of settings that you might amend:
    - Log level and logger backend.
    - Enable remoting.
    - Message serializers.
    - Definition of routers.
    - Tuning of dispatchers.
- Akka uses the [Lightbend Config Library](https://github.com/lightbend/config/blob/master/README.md).
- This might also be a good choice for the configuration of your own application or library built with or without Akka. 
- It is implemented in Java with no external dependencies.

# Where configuration is read from
- All configuration for Akka is held within instances of `ActorSystem`.
- As viewed from the outside, `ActorSystem` is the only consumer of configuration information. 
- While constructing an actor system, you can either pass in a `Config` object or not.
    - Where the second case is equivalent to passing `ConfigFactory.load()` (with the right class loader). 
- This means that:
    - The default is to parse all `application.conf`, `application.json` and `application.properties` found at the root of the class path.
    - The actor system then merges in all `reference.conf` resources found at the root of the class path to form the fallback configuration.
    - I.e. it internally uses:
```scala
appConfig.withFallback(ConfigFactory.defaultReference(classLoader))
```
- The philosophy is that code never contains default values:
    - But instead relies upon their presence in the `reference.conf` supplied with the library in question.
- Highest precedence is given to overrides given as system properties.
    - See [HOCON specification](https://github.com/lightbend/config/blob/master/HOCON.md). 
- The application configuration (which defaults to `application`) may be overridden using the `config.resource` property.
    - See [Config docs](https://github.com/lightbend/config/blob/master/README.md).
- If you are writing an Akka application:
    - Keep your configuration in `application.conf` at the root of the class path. 
- If you are writing an Akka-based library:
    - Keep its configuration in `reference.conf` at the root of the JAR file.

# When using JarJar, OneJar, Assembly or any jar-bundler
- Akka’s configuration approach relies heavily on the notion of every module/jar having its own `reference.conf` file.
- All of these will be discovered by the configuration and loaded. 
- Unfortunately this also means that if you put/merge multiple jars into the same jar:
    - You need to merge all the `reference.confs` as well. 
    - Otherwise all defaults will be lost and Akka will not function.

# Custom `application.conf`
- A custom `application.conf` might look like this:
```hocon
# In this file you can override any option defined in the reference files.
# Copy in parts of the reference files and modify as you please.

akka {

  # Loggers to register at boot time (akka.event.Logging$DefaultLogger logs
  # to STDOUT)
  loggers = ["akka.event.slf4j.Slf4jLogger"]

  # Log level used by the configured loggers (see "loggers") as soon
  # as they have been started; before that, see "stdout-loglevel"
  # Options: OFF, ERROR, WARNING, INFO, DEBUG
  loglevel = "DEBUG"

  # Log level for the very basic logger activated during ActorSystem startup.
  # This logger prints the log messages to stdout (System.out).
  # Options: OFF, ERROR, WARNING, INFO, DEBUG
  stdout-loglevel = "DEBUG"

  # Filter of log events that is used by the LoggingAdapter before
  # publishing log events to the eventStream.
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"

  actor {
    provider = "cluster"

    default-dispatcher {
      # Throughput for default Dispatcher, set to 1 for as fair as possible
      throughput = 10
    }
  }

  remote {
    # The port clients should connect to. Default is 2552.
    netty.tcp.port = 4711
  }
}
```

# Including files
- Sometimes it can be useful to include another configuration file:
    - For example if you have one `application.conf` with all environment independent settings.
    - Then override some settings for specific environments.
- Specifying system property with `-Dconfig.resource=/dev.conf`:
    - Will load the `dev.conf` file.
    - Which includes the `application.conf`.
- See [HOCON specification](https://github.com/lightbend/config/blob/master/HOCON.md). 

#### `dev.conf`:
```hocon
include "application"

akka {
  loglevel = "DEBUG"
}
```

# Logging of Configuration
- If the system or config property `akka.log-config-on-start` is set to `on`:
    - Then the complete configuration is logged at `INFO` level when the actor system is started. 
    - This is useful when you are uncertain of what configuration is used.
- If in doubt, you can also inspect configuration objects before or after using them to construct an actor system:
```
Welcome to Scala 2.12 (Java HotSpot(TM) 64-Bit Server VM, Java 1.8.0).
Type in expressions to have them evaluated.
Type :help for more information.

scala> import com.typesafe.config._
import com.typesafe.config._

scala> ConfigFactory.parseString("a.b=12")
res0: com.typesafe.config.Config = Config(SimpleConfigObject({"a" : {"b" : 12}}))

scala> res0.root.render
res1: java.lang.String =
{
    # String: 1
    "a" : {
        # String: 1
        "b" : 12
    }
}
```
- The comments preceding every item give detailed information about:
    - The origin of the setting (file & line number).
    - Plus possible comments which were present, e.g. in the reference configuration. 
- The settings as merged with the reference and parsed by the actor system can be displayed like this:
```scala
final ActorSystem system = ActorSystem.create();
System.out.println(system.settings());
// this is a shortcut for system.settings().config().root().render()
```

# A Word About ClassLoaders
- In several places of the configuration file:
    - It is possible to specify the fully-qualified class name of something to be instantiated by Akka. 
- This is done using Java reflection, which in turn uses a `ClassLoader`. 
- Getting the right one in challenging environments like application containers or OSGi bundles is not always trivial.
- The current approach of Akka is that:
    - Each _ActorSystem_ implementation stores the current thread’s context class loader.
    - And uses that for all reflective accesses. 
- This implies that putting Akka on the boot class path will yield `NullPointerException` from strange places: 
    - This is simply not supported.

# Application specific settings
- The configuration can also be used for application specific settings. 
- A good practice is to place those settings in an [Extension](../../09-utilities/07-akka-extensions).

# Configuring multiple `ActorSystems`
- If you have more than one `ActorSystem`:
    - You may want to separate the configuration for each system.
- Given that `ConfigFactory.load()` merges all resources with matching name from the whole class path:
    - It is easiest to utilize that functionality and differentiate actor systems within the hierarchy of the configuration:
```hocon
myapp1 {
  akka.loglevel = "WARNING"
  my.own.setting = 43
}
myapp2 {
  akka.loglevel = "ERROR"
  app2.setting = "appname"
}
my.own.setting = 42
my.other.setting = "hello"
```
```scala
val config = ConfigFactory.load()
val app1 = ActorSystem("MyApp1", 
  config.getConfig("myapp1").withFallback(config))
val app2 = ActorSystem("MyApp2",
  config.getConfig("myapp2").withOnlyPath("akka").withFallback(config))
```
- These two samples demonstrate different variations of the “lift-a-subtree” trick.
- In the first case, the configuration accessible from within the actor system is this:
```hocon
akka.loglevel = "WARNING"
my.own.setting = 43
my.other.setting = "hello"
// plus myapp1 and myapp2 subtrees
```    
- While in the second one, only the “akka” subtree is lifted, with the following result:
```hocon
akka.loglevel = "ERROR"
my.own.setting = 42
my.other.setting = "hello"
// plus myapp1 and myapp2 subtrees
```
- You may also specify and parse the configuration programmatically in other ways when instantiating the `ActorSystem`.    
```scala
val customConf = ConfigFactory.parseString("""
  akka.actor.deployment {
    /my-service {
      router = round-robin-pool
      nr-of-instances = 3
    }
  }
  """)
// ConfigFactory.load sandwiches customConfig between default reference
// config and default overrides, and then resolves it.
val system = ActorSystem("MySystem", ConfigFactory.load(customConf))
```

# Reading configuration from a custom location
- You can replace or supplement `application.conf` either in code or using system properties.
- If you’re using `ConfigFactory.load()` (which Akka does by default):
    - You can replace `application.conf` by defining:
    - `-Dconfig.resource=whatever`, `-Dconfig.file=whatever`, or `-Dconfig.url=whatever`.
- From inside your replacement file specified with `-Dconfig.resource` and friends:
    - You can include `"application"` if you still want to use `application.{conf,json,properties}` as well. 
- Settings specified before include `"application"` would be overridden by the included file.
    - While those after would override the included file.
- In code, there are many customization options.
- There are several overloads of `ConfigFactory.load()`:
    - These allow you to specify something to be sandwiched between system properties (which override) and the defaults (from reference.conf).
    - Replacing the usual `application.{conf,json,properties}` and replacing `-Dconfig.file` and friends.
- The simplest variant of `ConfigFactory.load()` takes a resource basename (instead of `application`):
    - `myname.conf`, `myname.json`, and `myname.properties` would then be used instead of `application.{conf,json,properties}`.
- The most flexible variant takes a `Config` object, which you can load using any method in `ConfigFactory`. 
    - For example you could put a config string in code using `ConfigFactory.parseString()`.
    - Or you could make a map and `ConfigFactory.parseMap()`.
    - Or you could load a file.
- You can also combine your custom config with the usual config, that might look like:
```scala
// make a Config with just your special setting
Config myConfig =
  ConfigFactory.parseString("something=somethingElse");
// load the normal config stack (system props,
// then application.conf, then reference.conf)
Config regularConfig =
  ConfigFactory.load();
// override regular stack with myConfig
Config combined =
  myConfig.withFallback(regularConfig);
// put the result in between the overrides
// (system props) and defaults again
Config complete =
  ConfigFactory.load(combined);
// create ActorSystem
ActorSystem system =
  ActorSystem.create("myname", complete);
```
- When working with Config objects, keep in mind that there are three “layers” in the cake:
    - `ConfigFactory.defaultOverrides()` (system properties).
    - the app’s settings.
    - `ConfigFactory.defaultReference()` (`reference.conf`).
- The goal is to customize the middle layer while leaving the other two alone.
    - `ConfigFactory.load()` loads the whole stack.
    - the overloads of `ConfigFactory.load()` let you specify a different middle layer.
    - the `ConfigFactory.parse()` variations load single files or resources.
- To stack two layers, use `override.withFallback(fallback)`.
    - Try to keep system props (`defaultOverrides()`) on top and `reference.conf` (`defaultReference()`) on the bottom.
- You can often just add another include statement in `application.conf` rather than writing code. 
    - Includes at the top of `application.conf` will be overridden by the rest of `application.conf`.
    - While those at the bottom will override the earlier stuff.

# Actor Deployment Configuration
- Deployment settings for specific actors can be defined in the `akka.actor.deployment` section of the configuration. 
- In the deployment section it is possible to define things like:
    - Dispatcher.
    - Mailbox.
    - Router settings.
    - Remote deployment. 
- Configuration of these features are described in the chapters detailing corresponding topics. 
- An example may look like this:
```hocon
akka.actor.deployment {

  # '/user/actorA/actorB' is a remote deployed actor
  /actorA/actorB {
    remote = "akka.tcp://sampleActorSystem@127.0.0.1:2553"
  }
  
  # all direct children of '/user/actorC' have a dedicated dispatcher 
  "/actorC/*" {
    dispatcher = my-dispatcher
  }

  # all descendants of '/user/actorC' (direct children, and their children recursively)
  # have a dedicated dispatcher
  "/actorC/**" {
    dispatcher = my-dispatcher
  }
  
  # '/user/actorD/actorE' has a special priority mailbox
  /actorD/actorE {
    mailbox = prio-mailbox
  }
  
  # '/user/actorF/actorG/actorH' is a random pool
  /actorF/actorG/actorH {
    router = random-pool
    nr-of-instances = 5
  }
}

my-dispatcher {
  fork-join-executor.parallelism-min = 10
  fork-join-executor.parallelism-max = 10
}
prio-mailbox {
  mailbox-type = "a.b.MyPrioMailbox"
}
```
- The deployment section for a specific actor is identified by the path of the actor relative to `/user`.
- You can use asterisks as wildcard matches for the actor path sections.
- You could specify: `/*/sampleActor` and that would match all `sampleActor` on that level in the hierarchy.
- You can also use wildcards in the **last position** to match all actors at a certain level: `/someParent/*`.
- You can use double-wildcards in the **last position** to match all child actors and their children recursively: `/someParent/**`.
- Match priority order:
    - Non-wildcard matches.
    - Single wildcard matches.
    - Double-wildcards matches.
- Wildcards cannot be used to partially match section, like this: `/foo*/bar`, `/f*o/bar` etc.

# Listing of the Reference Configuration
- [akka-actor](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-actor)
- [akka-agent](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-agent)
- [akka-camel](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-camel)
- [akka-cluster](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-cluster)
- [akka-cluster-metrics](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-cluster-metrics)
- [akka-cluster-sharding](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-cluster-sharding)
- [akka-cluster-tools](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-cluster-tools)
- [akka-distributed-data](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-distributed-data)
- [akka-multi-node-testkit](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-multi-node-testkit)
- [akka-persistence](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-persistence)
- [akka-remote](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-remote)
- [akka-remote (artery)](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-remote-artery-)
- [akka-testkit](https://doc.akka.io/docs/akka/current/general/configuration.html#akka-testkit)
