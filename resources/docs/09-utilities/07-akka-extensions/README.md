# Akka Extensions - Overview

If you want to add features to Akka, there is a very elegant, but powerful mechanism for doing so. It’s called Akka Extensions and is comprised of 2 basic components: an Extension and an ExtensionId.

Extensions will only be loaded once per ActorSystem, which will be managed by Akka. You can choose to have your Extension loaded on-demand or at ActorSystem creation time through the Akka configuration. Details on how to make that happens are below, in the “Loading from Configuration” section.
Warning

Since an extension is a way to hook into Akka itself, the implementor of the extension needs to ensure the thread safety of his/her extension.

# Building an Extension

So let’s create a sample extension that just lets us count the number of times something has happened.

First, we define what our Extension should do:

Scala

    import akka.actor.Extension

    class CountExtensionImpl extends Extension {
      //Since this Extension is a shared instance
      // per ActorSystem we need to be threadsafe
      private val counter = new AtomicLong(0)

      //This is the operation this Extension provides
      def increment() = counter.incrementAndGet()
    }

Java

Then we need to create an ExtensionId for our extension so we can grab a hold of it.

Scala

    import akka.actor.ActorSystem
    import akka.actor.ExtensionId
    import akka.actor.ExtensionIdProvider
    import akka.actor.ExtendedActorSystem

    object CountExtension
      extends ExtensionId[CountExtensionImpl]
      with ExtensionIdProvider {
      //The lookup method is required by ExtensionIdProvider,
      // so we return ourselves here, this allows us
      // to configure our extension to be loaded when
      // the ActorSystem starts up
      override def lookup = CountExtension

      //This method will be called by Akka
      // to instantiate our Extension
      override def createExtension(system: ExtendedActorSystem) = new CountExtensionImpl

      /**
       * Java API: retrieve the Count extension for the given system.
       */
      override def get(system: ActorSystem): CountExtensionImpl = super.get(system)
    }

Java

Wicked! Now all we need to do is to actually use it:

Scala

    CountExtension(system).increment

Java

Or from inside of an Akka Actor:

Scala


    class MyActor extends Actor {
      def receive = {
        case someMessage ⇒
          CountExtension(context.system).increment()
      }
    }

Java

You can also hide extension behind traits:


trait Counting { self: Actor ⇒
  def increment() = CountExtension(context.system).increment()
}
class MyCounterActor extends Actor with Counting {
  def receive = {
    case someMessage ⇒ increment()
  }
}

That’s all there is to it!

# Loading from Configuration

To be able to load extensions from your Akka configuration you must add FQCNs of implementations of either ExtensionId or ExtensionIdProvider in the akka.extensions section of the config you provide to your ActorSystem.

Scala

    akka {
      extensions = ["docs.extension.CountExtension"]
    }

Java


# Applicability

The sky is the limit! By the way, did you know that Akka’s Typed Actors, Serialization and other features are implemented as Akka Extensions?
Application specific settings

The configuration can be used for application specific settings. A good practice is to place those settings in an Extension.

Sample configuration:

myapp {
  db {
    uri = "mongodb://example1.com:27017,example2.com:27017"
  }
  circuit-breaker {
    timeout = 30 seconds
  }
}

The Extension:

Scala

    import akka.actor.ActorSystem
    import akka.actor.Extension
    import akka.actor.ExtensionId
    import akka.actor.ExtensionIdProvider
    import akka.actor.ExtendedActorSystem
    import scala.concurrent.duration.Duration
    import com.typesafe.config.Config
    import java.util.concurrent.TimeUnit

    class SettingsImpl(config: Config) extends Extension {
      val DbUri: String = config.getString("myapp.db.uri")
      val CircuitBreakerTimeout: Duration =
        Duration(
          config.getMilliseconds("myapp.circuit-breaker.timeout"),
          TimeUnit.MILLISECONDS)
    }
    object Settings extends ExtensionId[SettingsImpl] with ExtensionIdProvider {

      override def lookup = Settings

      override def createExtension(system: ExtendedActorSystem) =
        new SettingsImpl(system.settings.config)

      /**
       * Java API: retrieve the Settings extension for the given system.
       */
      override def get(system: ActorSystem): SettingsImpl = super.get(system)
    }

Java

Use it:

Scala


    class MyActor extends Actor {
      val settings = Settings(context.system)
      val connection = connect(settings.DbUri, settings.CircuitBreakerTimeout)

Java


# Library extensions

A third part library may register it’s extension for auto-loading on actor system startup by appending it to akka.library-extensions in its reference.conf.

akka.library-extensions += "docs.extension.ExampleExtension"

As there is no way to selectively remove such extensions, it should be used with care and only when there is no case where the user would ever want it disabled or have specific support for disabling such sub-features. One example where this could be important is in tests.
Warning

Theakka.library-extensions must never be assigned (= ["Extension"]) instead of appending as this will break the library-extension mechanism and make behavior depend on class path ordering.
