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

#### `dev.conf`
```hocon
include "application"

akka {
  loglevel = "DEBUG"
}
```

# Logging of Configuration





# A Word About ClassLoaders





# Application specific settings





# Configuring multiple ActorSystem





# Reading configuration from a custom location





# Actor Deployment Configuration





# Listing of the Reference Configuration










