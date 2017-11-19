docker run --name cassandra1 -m 2g -d -p 9042:9042 cassandra:3.0.4
docker inspect --format='{{ .NetworkSettings.IPAddress }}' cassandra1
docker run -it --link cassandra1 --rm cassandra:3.0.4 sh -c 'exec cqlsh 172.17.0.2'
docker run --name cassandra2 -m 2g -d -e CASSANDRA_SEEDS="$(docker inspect --format='{{ .NetworkSettings.IPAddress }}' cassandra1)" cassandra:3.0.4

sbt -Dconfig.resource=application-cassandra.conf "runMain com.packt.chapter6.sec07.StockApp"
sbt -Dconfig.resource=application-cassandra.conf "runMain com.packt.chapter6.sec07.StockRecoveryApp"