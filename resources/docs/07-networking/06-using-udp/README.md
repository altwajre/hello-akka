# Using UDP - Overview

UDP is a connectionless datagram protocol which offers two different ways of communication on the JDK level:

    sockets which are free to send datagrams to any destination and receive datagrams from any origin
    sockets which are restricted to communication with one specific remote socket address

In the low-level API the distinction is made—confusingly—by whether or not connect has been called on the socket (even when connect has been called the protocol is still connectionless). These two forms of UDP usage are offered using distinct IO extensions described below.

# Unconnected UDP
Simple Send

Scala

    class SimpleSender(remote: InetSocketAddress) extends Actor {
      import context.system
      IO(Udp) ! Udp.SimpleSender

      def receive = {
        case Udp.SimpleSenderReady ⇒
          context.become(ready(sender()))
      }

      def ready(send: ActorRef): Receive = {
        case msg: String ⇒
          send ! Udp.Send(ByteString(msg), remote)
      }
    }

Java

The simplest form of UDP usage is to just send datagrams without the need of getting a reply. To this end a “simple sender” facility is provided as demonstrated above. The UDP extension is queried using the SimpleSender message, which is answered by a SimpleSenderReady notification. The sender of this message is the newly created sender actor which from this point onward can be used to send datagrams to arbitrary destinations; in this example it will just send any UTF-8 encoded String it receives to a predefined remote address.
Note

The simple sender will not shut itself down because it cannot know when you are done with it. You will need to send it a PoisonPill when you want to close the ephemeral port the sender is bound to.
Bind (and Send)

Scala

    class Listener(nextActor: ActorRef) extends Actor {
      import context.system
      IO(Udp) ! Udp.Bind(self, new InetSocketAddress("localhost", 0))

      def receive = {
        case Udp.Bound(local) ⇒
          context.become(ready(sender()))
      }

      def ready(socket: ActorRef): Receive = {
        case Udp.Received(data, remote) ⇒
          val processed = // parse data etc., e.g. using PipelineStage
          socket ! Udp.Send(data, remote) // example server echoes back
          nextActor ! processed
        case Udp.Unbind  ⇒ socket ! Udp.Unbind
        case Udp.Unbound ⇒ context.stop(self)
      }
    }

Java

If you want to implement a UDP server which listens on a socket for incoming datagrams then you need to use the Bind message as shown above. The local address specified may have a zero port in which case the operating system will automatically choose a free port and assign it to the new socket. Which port was actually bound can be found out by inspecting the Bound message.

The sender of the Bound message is the actor which manages the new socket. Sending datagrams is achieved by using the Send message and the socket can be closed by sending a Unbind message, in which case the socket actor will reply with a Unbound notification.

Received datagrams are sent to the actor designated in the Bind message, whereas the Bound message will be sent to the sender of the Bind.

# Connected UDP

The service provided by the connection based UDP API is similar to the bind-and-send service we saw earlier, but the main difference is that a connection is only able to send to the remoteAddress it was connected to, and will receive datagrams only from that address.

Scala

    class Connected(remote: InetSocketAddress) extends Actor {
      import context.system
      IO(UdpConnected) ! UdpConnected.Connect(self, remote)

      def receive = {
        case UdpConnected.Connected ⇒
          context.become(ready(sender()))
      }

      def ready(connection: ActorRef): Receive = {
        case UdpConnected.Received(data) ⇒
          // process data, send it on, etc.
        case msg: String ⇒
          connection ! UdpConnected.Send(ByteString(msg))
        case UdpConnected.Disconnect ⇒
          connection ! UdpConnected.Disconnect
        case UdpConnected.Disconnected ⇒ context.stop(self)
      }
    }

Java

Consequently the example shown here looks quite similar to the previous one, the biggest difference is the absence of remote address information in Send and Received messages.
Note

There is a small performance benefit in using connection based UDP API over the connectionless one. If there is a SecurityManager enabled on the system, every connectionless message send has to go through a security check, while in the case of connection-based UDP the security check is cached after connect, thus writes do not suffer an additional performance penalty.

# UDP Multicast

Akka provides a way to control various options of DatagramChannel through the akka.io.Inet.SocketOption interface. The example below shows how to setup a receiver of multicast messages using IPv6 protocol.

To select a Protocol Family you must extend akka.io.Inet.DatagramChannelCreator class which extends akka.io.Inet.SocketOption. Provide custom logic for opening a datagram channel by overriding create method.

Scala

    final case class Inet6ProtocolFamily() extends DatagramChannelCreator {
      override def create() =
        DatagramChannel.open(StandardProtocolFamily.INET6)
    }

Java

Another socket option will be needed to join a multicast group.

Scala

    final case class MulticastGroup(address: String, interface: String) extends SocketOptionV2 {
      override def afterBind(s: DatagramSocket) {
        val group = InetAddress.getByName(address)
        val networkInterface = NetworkInterface.getByName(interface)
        s.getChannel.join(group, networkInterface)
      }
    }

Java

Socket options must be provided to UdpMessage.Bind message.

Scala

    import context.system
    val opts = List(Inet6ProtocolFamily(), MulticastGroup(group, iface))
    IO(Udp) ! Udp.Bind(self, new InetSocketAddress(port), opts)

Java

