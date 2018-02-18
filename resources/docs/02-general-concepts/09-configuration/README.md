# Configuration - Overview
- You can start using Akka without defining any configuration, since sensible default values are provided. 
- Later on you might need to amend the settings to change the default behavior or adapt for specific runtime environments. 
- Typical examples of settings that you might amend:
    - Log level and logger backend.
    - Enable remoting.
    - Message serializers.
    - Definition of routers.
    - Tuning of dispatchers.
- Akka uses the [Lightbend Config Library](https://github.com/lightbend/config).
- This might also be a good choice for the configuration of your own application or library built with or without Akka. 
- It is implemented in Java with no external dependencies.

# Where configuration is read from
- All configuration for Akka is held within instances of `ActorSystem`.
- As viewed from the outside, `ActorSystem` is the only consumer of configuration information. 
- While constructing an actor system, you can either pass in a `Config` object or not.
    - Where the second case is equivalent to passing `ConfigFactory.load()` (with the right class loader). 
- This means roughly that:
    - The default is to parse all `application.conf`, `application.json` and `application.properties` found at the root of the class path.
    - The actor system then merges in all reference.conf resources found at the root of the class path to form the fallback configuration, i.e. it internally uses




# When using JarJar, OneJar, Assembly or any jar-bundler





# Custom application.conf





# Including files





# Logging of Configuration





# A Word About ClassLoaders





# Application specific settings





# Configuring multiple ActorSystem





# Reading configuration from a custom location





# Actor Deployment Configuration





# Listing of the Reference Configuration










