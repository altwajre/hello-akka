# Configuration - Overview
- You can start using Akka without defining any configuration, since sensible default values are provided. 
- Later on you might need to amend the settings to change the default behavior or adapt for specific runtime environments. 
- Typical examples of settings that you might amend:
    - log level and logger backend
    - enable remoting
    - message serializers
    - definition of routers
    - tuning of dispatchers
  
- Akka uses the Typesafe Config Library, which might also be a good choice for the configuration of your own application or library built with or without Akka. This library is implemented in Java with no external dependencies; you should have a look at its documentation (in particular about ConfigFactory), which is only summarized in the following.

# Where configuration is read from
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
