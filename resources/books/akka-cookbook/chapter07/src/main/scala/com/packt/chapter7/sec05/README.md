sbt -Dconfig.resource=application-1.conf "runMain com.packt.chapter7.sec05.ScalingOutMaster"

sbt -Dconfig.resource=application-2.conf "runMain com.packt.chapter7.sec05.ScalingOutWorker"

sbt -Dconfig.resource=application-3.conf "runMain com.packt.chapter7.sec05.ScalingOutWorker"

// Then, kill the second application and look for the output in the master node