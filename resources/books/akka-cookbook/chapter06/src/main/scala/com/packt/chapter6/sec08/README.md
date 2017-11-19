docker run --name redis -d -p 6379:6379 redis

sbt -Dconfig.resource=application-redis.conf "runMain com.packt.chapter6.sec08.StockApp"
sbt -Dconfig.resource=application-redis.conf "runMain com.packt.chapter6.sec08.StockRecoveryApp"