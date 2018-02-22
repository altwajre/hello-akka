# Serialization - Overview

The messages that Akka actors send to each other are JVM objects (e.g. instances of Scala case classes). Message passing between actors that live on the same JVM is straightforward. It is simply done via reference passing. However, messages that have to escape the JVM to reach an actor running on a different host have to undergo some form of serialization (i.e. the objects have to be converted to and from byte arrays).

Akka itself uses Protocol Buffers to serialize internal messages (i.e. cluster gossip messages). However, the serialization mechanism in Akka allows you to write custom serializers and to define which serializer to use for what.

# Usage
Configuration

For Akka to know which Serializer to use for what, you need edit your Configuration, in the “akka.actor.serializers”-section you bind names to implementations of the akka.serialization.Serializer you wish to use, like this:

akka {
  actor {
    serializers {
      java = "akka.serialization.JavaSerializer"
      proto = "akka.remote.serialization.ProtobufSerializer"
      myown = "docs.serialization.MyOwnSerializer"
    }
  }
}

After you’ve bound names to different implementations of Serializer you need to wire which classes should be serialized using which Serializer, this is done in the “akka.actor.serialization-bindings”-section:

akka {
  actor {
    serializers {
      java = "akka.serialization.JavaSerializer"
      proto = "akka.remote.serialization.ProtobufSerializer"
      myown = "docs.serialization.MyOwnSerializer"
    }

    serialization-bindings {
      "java.lang.String" = java
      "docs.serialization.Customer" = java
      "com.google.protobuf.Message" = proto
      "docs.serialization.MyOwnSerializable" = myown
      "java.lang.Boolean" = myown
    }
  }
}

You only need to specify the name of an interface or abstract base class of the messages. In case of ambiguity, i.e. the message implements several of the configured classes, the most specific configured class will be used, i.e. the one of which all other candidates are superclasses. If this condition cannot be met, because e.g. java.io.Serializable and MyOwnSerializable both apply and neither is a subtype of the other, a warning will be issued.
Note

If your messages are contained inside of a Scala object, then in order to reference those messages, you will need to use the fully qualified Java class name. For a message named Message contained inside the object named Wrapper you would need to reference it as Wrapper$Message instead of Wrapper.Message.

Akka provides serializers for java.io.Serializable and protobuf com.google.protobuf.GeneratedMessage by default (the latter only if depending on the akka-remote module), so normally you don’t need to add configuration for that; since com.google.protobuf.GeneratedMessage implements java.io.Serializable, protobuf messages will always be serialized using the protobuf protocol unless specifically overridden. In order to disable a default serializer, see Disabling the Java Serializer
Enable additional bindings

A few types in Akka are, for backwards-compatibility reasons, still serialized by using Java serializer by default. You can switch them to using protocol buffers instead by adding the following bindings or set akka.actor.allow-java-serialization=off, which will make them serialized using protocol buffers instead. Refer to Rolling Upgrades to understand how it is possible to turn and start using these new serializers in your clustered applications.

You can enable them one by one adding by adding their bindings to the misc serializer, like this:

akka.actor.serialization-bindings {
    "akka.Done"                 = akka-misc
    "akka.actor.Address"        = akka-misc
    "akka.remote.UniqueAddress" = akka-misc
}

Alternatively, you can disable all Java serialization which then automatically will add the java-serialization-disabled-additional-serialization-bindings bindings to the active bindings.
Verification

Normally, messages sent between local actors (i.e. same JVM) do not undergo serialization. For testing, sometimes, it may be desirable to force serialization on all messages (both remote and local). If you want to do this in order to verify that your messages are serializable you can enable the following config option:

akka {
  actor {
    serialize-messages = on
  }
}

If you want to verify that your Props are serializable you can enable the following config option:

akka {
  actor {
    serialize-creators = on
  }
}

Warning

We recommend having these config options turned on only when you’re running tests. Turning these options on in production is pointless, as it would negatively impact the performance of local message passing without giving any gain.
Programmatic

If you want to programmatically serialize/deserialize using Akka Serialization, here’s some examples:

Scala

    import akka.actor.{ ActorRef, ActorSystem }
    import akka.serialization._
    import com.typesafe.config.ConfigFactory

Java

Scala

    val system = ActorSystem("example")

    // Get the Serialization Extension
    val serialization = SerializationExtension(system)

    // Have something to serialize
    val original = "woohoo"

    // Find the Serializer for it
    val serializer = serialization.findSerializerFor(original)

    // Turn it into bytes
    val bytes = serializer.toBinary(original)

    // Turn it back into an object
    val back = serializer.fromBinary(bytes, manifest = None)

    // Voilá!
    back should be(original)

Java

For more information, have a look at the ScalaDoc for akka.serialization._

# Customization

The first code snippet on this page contains a configuration file that references a custom serializer docs.serialization.MyOwnSerializer. How would we go about creating such a custom serializer?
Creating new Serializers

A custom Serializer has to inherit from akka.serialization.Serializer and can be defined like the following:

Scala

    import akka.actor.{ ActorRef, ActorSystem }
    import akka.serialization._
    import com.typesafe.config.ConfigFactory

Java

Scala

    class MyOwnSerializer extends Serializer {

      // If you need logging here, introduce a constructor that takes an ExtendedActorSystem.
      // class MyOwnSerializer(actorSystem: ExtendedActorSystem) extends Serializer
      // Get a logger using:
      // private val logger = Logging(actorSystem, this)

      // This is whether "fromBinary" requires a "clazz" or not
      def includeManifest: Boolean = true

      // Pick a unique identifier for your Serializer,
      // you've got a couple of billions to choose from,
      // 0 - 40 is reserved by Akka itself
      def identifier = 1234567

      // "toBinary" serializes the given object to an Array of Bytes
      def toBinary(obj: AnyRef): Array[Byte] = {
        // Put the code that serializes the object here
        //#...
        Array[Byte]()
        //#...
      }

      // "fromBinary" deserializes the given array,
      // using the type hint (if any, see "includeManifest" above)
      def fromBinary(
        bytes: Array[Byte],
        clazz: Option[Class[_]]): AnyRef = {
        // Put your code that deserializes here
        //#...
        null
        //#...
      }
    }

Java

The manifest is a type hint so that the same serializer can be used for different classes. The manifest parameter in fromBinary is the class of the object that was serialized. In fromBinary you can match on the class and deserialize the bytes to different objects.

Then you only need to fill in the blanks, bind it to a name in your Configuration and then list which classes that should be serialized using it.
Serializer with String Manifest

The Serializer illustrated above supports a class based manifest (type hint). For serialization of data that need to evolve over time the SerializerWithStringManifest is recommended instead of Serializer because the manifest (type hint) is a String instead of a Class. That means that the class can be moved/removed and the serializer can still deserialize old data by matching on the String. This is especially useful for Persistence.

The manifest string can also encode a version number that can be used in fromBinary to deserialize in different ways to migrate old data to new domain objects.

If the data was originally serialized with Serializer and in a later version of the system you change to SerializerWithStringManifest the manifest string will be the full class name if you used includeManifest=true, otherwise it will be the empty string.

This is how a SerializerWithStringManifest looks like:

Scala

    class MyOwnSerializer2 extends SerializerWithStringManifest {

      val CustomerManifest = "customer"
      val UserManifest = "user"
      val UTF_8 = StandardCharsets.UTF_8.name()

      // Pick a unique identifier for your Serializer,
      // you've got a couple of billions to choose from,
      // 0 - 40 is reserved by Akka itself
      def identifier = 1234567

      // The manifest (type hint) that will be provided in the fromBinary method
      // Use `""` if manifest is not needed.
      def manifest(obj: AnyRef): String =
        obj match {
          case _: Customer ⇒ CustomerManifest
          case _: User     ⇒ UserManifest
        }

      // "toBinary" serializes the given object to an Array of Bytes
      def toBinary(obj: AnyRef): Array[Byte] = {
        // Put the real code that serializes the object here
        obj match {
          case Customer(name) ⇒ name.getBytes(UTF_8)
          case User(name)     ⇒ name.getBytes(UTF_8)
        }
      }

      // "fromBinary" deserializes the given array,
      // using the type hint
      def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
        // Put the real code that deserializes here
        manifest match {
          case CustomerManifest ⇒
            Customer(new String(bytes, UTF_8))
          case UserManifest ⇒
            User(new String(bytes, UTF_8))
        }
      }
    }

Java

You must also bind it to a name in your Configuration and then list which classes that should be serialized using it.

It’s recommended to throw java.io.NotSerializableException in fromBinary if the manifest is unknown. This makes it possible to introduce new message types and send them to nodes that don’t know about them. This is typically needed when performing rolling upgrades, i.e. running a cluster with mixed versions for while. NotSerializableException is treated as a transient problem in the TCP based remoting layer. The problem will be logged and message is dropped. Other exceptions will tear down the TCP connection because it can be an indication of corrupt bytes from the underlying transport.
Serializing ActorRefs

All ActorRefs are serializable using JavaSerializer, but in case you are writing your own serializer, you might want to know how to serialize and deserialize them properly. In the general case, the local address to be used depends on the type of remote address which shall be the recipient of the serialized information. Use Serialization.serializedActorPath(actorRef) like this:

Scala

    import akka.actor.{ ActorRef, ActorSystem }
    import akka.serialization._
    import com.typesafe.config.ConfigFactory

Java

Scala

    // Serialize
    // (beneath toBinary)
    val identifier: String = Serialization.serializedActorPath(theActorRef)

    // Then just serialize the identifier however you like

    // Deserialize
    // (beneath fromBinary)
    val deserializedActorRef = extendedSystem.provider.resolveActorRef(identifier)
    // Then just use the ActorRef

Java

This assumes that serialization happens in the context of sending a message through the remote transport. There are other uses of serialization, though, e.g. storing actor references outside of an actor application (database, etc.). In this case, it is important to keep in mind that the address part of an actor’s path determines how that actor is communicated with. Storing a local actor path might be the right choice if the retrieval happens in the same logical context, but it is not enough when deserializing it on a different network host: for that it would need to include the system’s remote transport address. An actor system is not limited to having just one remote transport per se, which makes this question a bit more interesting. To find out the appropriate address to use when sending to remoteAddr you can use ActorRefProvider.getExternalAddressFor(remoteAddr) like this:

Scala

    object ExternalAddress extends ExtensionId[ExternalAddressExt] with ExtensionIdProvider {
      override def lookup() = ExternalAddress

      override def createExtension(system: ExtendedActorSystem): ExternalAddressExt =
        new ExternalAddressExt(system)

      override def get(system: ActorSystem): ExternalAddressExt = super.get(system)
    }

    class ExternalAddressExt(system: ExtendedActorSystem) extends Extension {
      def addressFor(remoteAddr: Address): Address =
        system.provider.getExternalAddressFor(remoteAddr) getOrElse
          (throw new UnsupportedOperationException("cannot send to " + remoteAddr))
    }

    def serializeTo(ref: ActorRef, remote: Address): String =
      ref.path.toSerializationFormatWithAddress(ExternalAddress(extendedSystem).
        addressFor(remote))

Java

Note

ActorPath.toSerializationFormatWithAddress differs from toString if the address does not already have host and port components, i.e. it only inserts address information for local addresses.

toSerializationFormatWithAddress also adds the unique id of the actor, which will change when the actor is stopped and then created again with the same name. Sending messages to a reference pointing the old actor will not be delivered to the new actor. If you don’t want this behavior, e.g. in case of long term storage of the reference, you can use toStringWithAddress, which doesn’t include the unique id.

This requires that you know at least which type of address will be supported by the system which will deserialize the resulting actor reference; if you have no concrete address handy you can create a dummy one for the right protocol using Address(protocol, "", "", 0) (assuming that the actual transport used is as lenient as Akka’s RemoteActorRefProvider).

There is also a default remote address which is the one used by cluster support (and typical systems have just this one); you can get it like this:

Scala

    object ExternalAddress extends ExtensionId[ExternalAddressExt] with ExtensionIdProvider {
      override def lookup() = ExternalAddress

      override def createExtension(system: ExtendedActorSystem): ExternalAddressExt =
        new ExternalAddressExt(system)

      override def get(system: ActorSystem): ExternalAddressExt = super.get(system)
    }

    class ExternalAddressExt(system: ExtendedActorSystem) extends Extension {
      def addressForAkka: Address = system.provider.getDefaultAddress
    }

    def serializeAkkaDefault(ref: ActorRef): String =
      ref.path.toSerializationFormatWithAddress(ExternalAddress(theActorSystem).
        addressForAkka)

Java

Deep serialization of Actors

The recommended approach to do deep serialization of internal actor state is to use Akka Persistence.

# A Word About Java Serialization

When using Java serialization without employing the JavaSerializer for the task, you must make sure to supply a valid ExtendedActorSystem in the dynamic variable JavaSerializer.currentSystem. This is used when reading in the representation of an ActorRef for turning the string representation into a real reference. DynamicVariable is a thread-local variable, so be sure to have it set while deserializing anything which might contain actor references.

# Serialization compatibility

It is not safe to mix major Scala versions when using the Java serialization as Scala does not guarantee compatibility and this could lead to very surprising errors.

If using the Akka Protobuf serializers (implicitly with akka.actor.allow-java-serialization = off or explicitly with enable-additional-serialization-bindings = true) for the internal Akka messages those will not require the same major Scala version however you must also ensure the serializers used for your own types does not introduce the same incompatibility as Java serialization does.

# Rolling upgrades

A serialized remote message (or persistent event) consists of serializer-id, the manifest, and the binary payload. When deserializing it is only looking at the serializer-id to pick which Serializer to use for fromBinary. The message class (the bindings) is not used for deserialization. The manifest is only used within the Serializer to decide how to deserialize the payload, so one Serializer can handle many classes.

That means that it is possible to change serialization for a message by performing two rolling upgrade steps to switch to the new serializer.

    Add the Serializer class and define it in akka.actor.serializers config section, but not in akka.actor.serialization-bindings. Perform a rolling upgrade for this change. This means that the serializer class exists on all nodes and is registered, but it is still not used for serializing any messages. That is important because during the rolling upgrade the old nodes still don’t know about the new serializer and would not be able to deserialize messages with that format.

    The second change is to register that the serializer is to be used for certain classes by defining those in the akka.actor.serialization-bindings config section. Perform a rolling upgrade for this change. This means that new nodes will use the new serializer when sending messages and old nodes will be able to deserialize the new format. Old nodes will continue to use the old serializer when sending messages and new nodes will be able to deserialize the old format.

As an optional third step the old serializer can be completely removed if it was not used for persistent events. It must still be possible to deserialize the events that were stored with the old serializer.

# External Akka Serializers

Akka-quickser by Roman Levenstein

Akka-kryo by Roman Levenstein

Twitter Chill Scala extensions for Kryo (based on Akka Version 2.3.x but due to backwards compatibility of the Serializer Interface this extension also works with 2.4.x)
