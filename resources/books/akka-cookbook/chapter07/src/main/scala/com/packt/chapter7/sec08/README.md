sbt -Dconfig.resource=application-cluster-1.conf "runMain com.packt.chapter7.sec08.DistributedPubSubApplication"

sbt -Dconfig.resource=application-cluster-2.conf "runMain com.packt.chapter7.sec08.DistributedPubSubApplication"