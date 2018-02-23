# Cluster Metrics Extension - Overview

# Introduction

The member nodes of the cluster can collect system health metrics and publish that to other cluster nodes and to the registered subscribers on the system event bus with the help of Cluster Metrics Extension.

Cluster metrics information is primarily used for load-balancing routers, and can also be used to implement advanced metrics-based node life cycles, such as “Node Let-it-crash” when CPU steal time becomes excessive.

Cluster Metrics Extension is a separate Akka module delivered in akka-cluster-metrics jar.

To enable usage of the extension you need to add the following dependency to your project: :

Scala

    "com.typesafe.akka" % "akka-cluster-metrics_2.12" % "2.5.9"

Java

and add the following configuration stanza to your application.conf :

akka.extensions = [ "akka.cluster.metrics.ClusterMetricsExtension" ]

Cluster members with status WeaklyUp, if that feature is enabled, will participate in Cluster Metrics collection and dissemination.

# Metrics Collector

Metrics collection is delegated to an implementation of akka.cluster.metrics.MetricsCollector.

Different collector implementations provide different subsets of metrics published to the cluster. Certain message routing and let-it-crash functions may not work when Sigar is not provisioned.

Cluster metrics extension comes with two built-in collector implementations:

    akka.cluster.metrics.SigarMetricsCollector, which requires Sigar provisioning, and is more rich/precise
    akka.cluster.metrics.JmxMetricsCollector, which is used as fall back, and is less rich/precise

You can also plug-in your own metrics collector implementation.

By default, metrics extension will use collector provider fall back and will try to load them in this order:

    configured user-provided collector
    built-in akka.cluster.metrics.SigarMetricsCollector
    and finally akka.cluster.metrics.JmxMetricsCollector


# Metrics Events

Metrics extension periodically publishes current snapshot of the cluster metrics to the node system event bus.

The publication interval is controlled by the akka.cluster.metrics.collector.sample-interval setting.

The payload of the akka.cluster.metrics.ClusterMetricsChanged event will contain latest metrics of the node as well as other cluster member nodes metrics gossip which was received during the collector sample interval.

You can subscribe your metrics listener actors to these events in order to implement custom node lifecycle:

Scala

    ClusterMetricsExtension(system).subscribe(metricsListenerActor)

Java


# Hyperic Sigar Provisioning

Both user-provided and built-in metrics collectors can optionally use Hyperic Sigar for a wider and more accurate range of metrics compared to what can be retrieved from ordinary JMX MBeans.

Sigar is using a native o/s library, and requires library provisioning, i.e. deployment, extraction and loading of the o/s native library into JVM at runtime.

User can provision Sigar classes and native library in one of the following ways:

    Use Kamon sigar-loader as a project dependency for the user project. Metrics extension will extract and load sigar library on demand with help of Kamon sigar provisioner.
    Use Kamon sigar-loader as java agent: java -javaagent:/path/to/sigar-loader.jar. Kamon sigar loader agent will extract and load sigar library during JVM start.
    Place sigar.jar on the classpath and Sigar native library for the o/s on the java.library.path. User is required to manage both project dependency and library deployment manually.


#### Warning

When using Kamon sigar-loader and running multiple instances of the same application on the same host, you have to make sure that sigar library is extracted to a unique per instance directory. You can control the extract directory with the akka.cluster.metrics.native-library-extract-folder configuration setting.

To enable usage of Sigar you can add the following dependency to the user project :

Scala

    "io.kamon" % "sigar-loader" % "1.6.6-rev002"

Java

You can download Kamon sigar-loader from Maven Central

# Adaptive Load Balancing

The AdaptiveLoadBalancingPool / AdaptiveLoadBalancingGroup performs load balancing of messages to cluster nodes based on the cluster metrics data. It uses random selection of routees with probabilities derived from the remaining capacity of the corresponding node. It can be configured to use a specific MetricsSelector to produce the probabilities, a.k.a. weights:

    heap / HeapMetricsSelector - Used and max JVM heap memory. Weights based on remaining heap capacity; (max - used) / max
    load / SystemLoadAverageMetricsSelector - System load average for the past 1 minute, corresponding value can be found in top of Linux systems. The system is possibly nearing a bottleneck if the system load average is nearing number of cpus/cores. Weights based on remaining load capacity; 1 - (load / processors)
    cpu / CpuMetricsSelector - CPU utilization in percentage, sum of User + Sys + Nice + Wait. Weights based on remaining cpu capacity; 1 - utilization
    mix / MixMetricsSelector - Combines heap, cpu and load. Weights based on mean of remaining capacity of the combined selectors.
    Any custom implementation of akka.cluster.metrics.MetricsSelector

The collected metrics values are smoothed with exponential weighted moving average. In the Cluster configuration you can adjust how quickly past data is decayed compared to new data.

Let’s take a look at this router in action. What can be more demanding than calculating factorials?

The backend worker that performs the factorial calculation:

Scala

    class FactorialBackend extends Actor with ActorLogging {

      import context.dispatcher

      def receive = {
        case (n: Int) ⇒
          Future(factorial(n)) map { result ⇒ (n, result) } pipeTo sender()
      }

      def factorial(n: Int): BigInt = {
        @tailrec def factorialAcc(acc: BigInt, n: Int): BigInt = {
          if (n <= 1) acc
          else factorialAcc(acc * n, n - 1)
        }
        factorialAcc(BigInt(1), n)
      }

    }

Java

The frontend that receives user jobs and delegates to the backends via the router:

Scala

    class FactorialFrontend(upToN: Int, repeat: Boolean) extends Actor with ActorLogging {

      val backend = context.actorOf(
        FromConfig.props(),
        name = "factorialBackendRouter")

      override def preStart(): Unit = {
        sendJobs()
        if (repeat) {
          context.setReceiveTimeout(10.seconds)
        }
      }

      def receive = {
        case (n: Int, factorial: BigInt) ⇒
          if (n == upToN) {
            log.debug("{}! = {}", n, factorial)
            if (repeat) sendJobs()
            else context.stop(self)
          }
        case ReceiveTimeout ⇒
          log.info("Timeout")
          sendJobs()
      }

      def sendJobs(): Unit = {
        log.info("Starting batch of factorials up to [{}]", upToN)
        1 to upToN foreach { backend ! _ }
      }
    }

Java

As you can see, the router is defined in the same way as other routers, and in this case it is configured as follows:

akka.actor.deployment {
  /factorialFrontend/factorialBackendRouter = {
    # Router type provided by metrics extension.
    router = cluster-metrics-adaptive-group
    # Router parameter specific for metrics extension.
    # metrics-selector = heap
    # metrics-selector = load
    # metrics-selector = cpu
    metrics-selector = mix
    #
    routees.paths = ["/user/factorialBackend"]
    cluster {
      enabled = on
      use-roles = ["backend"]
      allow-local-routees = off
    }
  }
}

It is only router type and the metrics-selector parameter that is specific to this router, other things work in the same way as other routers.

The same type of router could also have been defined in code:

Scala

    import akka.cluster.routing.ClusterRouterGroup
    import akka.cluster.routing.ClusterRouterGroupSettings
    import akka.cluster.metrics.AdaptiveLoadBalancingGroup
    import akka.cluster.metrics.HeapMetricsSelector

    val backend = context.actorOf(
      ClusterRouterGroup(
        AdaptiveLoadBalancingGroup(HeapMetricsSelector),
        ClusterRouterGroupSettings(
          totalInstances = 100, routeesPaths = List("/user/factorialBackend"),
          allowLocalRoutees = true, useRoles = Set("backend"))).props(),
      name = "factorialBackendRouter2")

    import akka.cluster.routing.ClusterRouterPool
    import akka.cluster.routing.ClusterRouterPoolSettings
    import akka.cluster.metrics.AdaptiveLoadBalancingPool
    import akka.cluster.metrics.SystemLoadAverageMetricsSelector

    val backend = context.actorOf(
      ClusterRouterPool(AdaptiveLoadBalancingPool(
        SystemLoadAverageMetricsSelector), ClusterRouterPoolSettings(
        totalInstances = 100, maxInstancesPerNode = 3,
        allowLocalRoutees = false, useRoles = Set("backend"))).props(Props[FactorialBackend]),
      name = "factorialBackendRouter3")

Java

The easiest way to run Adaptive Load Balancing example yourself is to download the ready to run Akka Cluster Sample with Scala together with the tutorial. It contains instructions on how to run the Adaptive Load Balancing sample. The source code of this sample can be found in the Akka Samples Repository.

# Subscribe to Metrics Events

It is possible to subscribe to the metrics events directly to implement other functionality.

Scala

    import akka.actor.ActorLogging
    import akka.actor.Actor
    import akka.cluster.Cluster
    import akka.cluster.metrics.ClusterMetricsEvent
    import akka.cluster.metrics.ClusterMetricsChanged
    import akka.cluster.ClusterEvent.CurrentClusterState
    import akka.cluster.metrics.NodeMetrics
    import akka.cluster.metrics.StandardMetrics.HeapMemory
    import akka.cluster.metrics.StandardMetrics.Cpu
    import akka.cluster.metrics.ClusterMetricsExtension

    class MetricsListener extends Actor with ActorLogging {
      val selfAddress = Cluster(context.system).selfAddress
      val extension = ClusterMetricsExtension(context.system)

      // Subscribe unto ClusterMetricsEvent events.
      override def preStart(): Unit = extension.subscribe(self)

      // Unsubscribe from ClusterMetricsEvent events.
      override def postStop(): Unit = extension.unsubscribe(self)

      def receive = {
        case ClusterMetricsChanged(clusterMetrics) ⇒
          clusterMetrics.filter(_.address == selfAddress) foreach { nodeMetrics ⇒
            logHeap(nodeMetrics)
            logCpu(nodeMetrics)
          }
        case state: CurrentClusterState ⇒ // Ignore.
      }

      def logHeap(nodeMetrics: NodeMetrics): Unit = nodeMetrics match {
        case HeapMemory(address, timestamp, used, committed, max) ⇒
          log.info("Used heap: {} MB", used.doubleValue / 1024 / 1024)
        case _ ⇒ // No heap info.
      }

      def logCpu(nodeMetrics: NodeMetrics): Unit = nodeMetrics match {
        case Cpu(address, timestamp, Some(systemLoadAverage), cpuCombined, cpuStolen, processors) ⇒
          log.info("Load: {} ({} processors)", systemLoadAverage, processors)
        case _ ⇒ // No cpu info.
      }
    }

Java


# Custom Metrics Collector

Metrics collection is delegated to the implementation of akka.cluster.metrics.MetricsCollector

You can plug-in your own metrics collector instead of built-in akka.cluster.metrics.SigarMetricsCollector or akka.cluster.metrics.JmxMetricsCollector.

Look at those two implementations for inspiration.

Custom metrics collector implementation class must be specified in the akka.cluster.metrics.collector.provider configuration property.

# Configuration

The Cluster metrics extension can be configured with the following properties:

##############################################
# Akka Cluster Metrics Reference Config File #
##############################################

# This is the reference config file that contains all the default settings.
# Make your edits in your application.conf in order to override these settings.

# Sigar provisioning:
#
#  User can provision sigar classes and native library in one of the following ways:
# 
#  1) Use https://github.com/kamon-io/sigar-loader Kamon sigar-loader as a project dependency for the user project.
#  Metrics extension will extract and load sigar library on demand with help of Kamon sigar provisioner.
# 
#  2) Use https://github.com/kamon-io/sigar-loader Kamon sigar-loader as java agent: `java -javaagent:/path/to/sigar-loader.jar`
#  Kamon sigar loader agent will extract and load sigar library during JVM start.
# 
#  3) Place `sigar.jar` on the `classpath` and sigar native library for the o/s on the `java.library.path`
#  User is required to manage both project dependency and library deployment manually.

# Cluster metrics extension.
# Provides periodic statistics collection and publication throughout the cluster.
akka.cluster.metrics {
  # Full path of dispatcher configuration key.
  # Use "" for default key `akka.actor.default-dispatcher`.
  dispatcher = ""
  # How long should any actor wait before starting the periodic tasks.
  periodic-tasks-initial-delay = 1s
  # Sigar native library extract location.
  # Use per-application-instance scoped location, such as program working directory.
  native-library-extract-folder = ${user.dir}"/native"
  # Metrics supervisor actor.
  supervisor {
    # Actor name. Example name space: /system/cluster-metrics
    name = "cluster-metrics"
    # Supervision strategy.
    strategy {
      #
      # FQCN of class providing `akka.actor.SupervisorStrategy`.
      # Must have a constructor with signature `<init>(com.typesafe.config.Config)`.
      # Default metrics strategy provider is a configurable extension of `OneForOneStrategy`.
      provider = "akka.cluster.metrics.ClusterMetricsStrategy"
      #
      # Configuration of the default strategy provider.
      # Replace with custom settings when overriding the provider.
      configuration = {
        # Log restart attempts.
        loggingEnabled = true
        # Child actor restart-on-failure window.
        withinTimeRange = 3s
        # Maximum number of restart attempts before child actor is stopped.
        maxNrOfRetries = 3
      }
    }
  }
  # Metrics collector actor.
  collector {
    # Enable or disable metrics collector for load-balancing nodes.
    # Metrics collection can also be controlled at runtime by sending control messages
    # to /system/cluster-metrics actor: `akka.cluster.metrics.{CollectionStartMessage,CollectionStopMessage}`
    enabled = on
    # FQCN of the metrics collector implementation.
    # It must implement `akka.cluster.metrics.MetricsCollector` and
    # have public constructor with akka.actor.ActorSystem parameter.
    # Will try to load in the following order of priority:
    # 1) configured custom collector 2) internal `SigarMetricsCollector` 3) internal `JmxMetricsCollector`
    provider = ""
    # Try all 3 available collector providers, or else fail on the configured custom collector provider.
    fallback = true
    # How often metrics are sampled on a node.
    # Shorter interval will collect the metrics more often.
    # Also controls frequency of the metrics publication to the node system event bus.
    sample-interval = 3s
    # How often a node publishes metrics information to the other nodes in the cluster.
    # Shorter interval will publish the metrics gossip more often.
    gossip-interval = 3s
    # How quickly the exponential weighting of past data is decayed compared to
    # new data. Set lower to increase the bias toward newer values.
    # The relevance of each data sample is halved for every passing half-life
    # duration, i.e. after 4 times the half-life, a data sample’s relevance is
    # reduced to 6% of its original relevance. The initial relevance of a data
    # sample is given by 1 – 0.5 ^ (collect-interval / half-life).
    moving-average-half-life = 12s
  }
}

# Cluster metrics extension serializers and routers.
akka.actor {
  # Protobuf serializer for remote cluster metrics messages.
  serializers {
    akka-cluster-metrics = "akka.cluster.metrics.protobuf.MessageSerializer"
  }
  # Interface binding for remote cluster metrics messages.
  serialization-bindings {
    "akka.cluster.metrics.ClusterMetricsMessage" = akka-cluster-metrics
    "akka.cluster.metrics.AdaptiveLoadBalancingPool" = akka-cluster-metrics
    "akka.cluster.metrics.MixMetricsSelector" = akka-cluster-metrics
    "akka.cluster.metrics.CpuMetricsSelector$" = akka-cluster-metrics
    "akka.cluster.metrics.HeapMetricsSelector$" = akka-cluster-metrics
    "akka.cluster.metrics.SystemLoadAverageMetricsSelector$" = akka-cluster-metrics
  }
  # Globally unique metrics extension serializer identifier.
  serialization-identifiers {
    "akka.cluster.metrics.protobuf.MessageSerializer" = 10
  }
  #  Provide routing of messages based on cluster metrics.
  router.type-mapping {
    cluster-metrics-adaptive-pool  = "akka.cluster.metrics.AdaptiveLoadBalancingPool"
    cluster-metrics-adaptive-group = "akka.cluster.metrics.AdaptiveLoadBalancingGroup"
  }
}

