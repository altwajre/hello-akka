#!/bin/bash

echo "====================Removing target before making assembly================================"

rm -rf ../target && rm -rf dist && mkdir dist


echo "====================Entering to Hello-Akka directory and creating assembly================"

cd ../ && sbt assembly


echo "====================Coming back to dockerApp directory===================================="

cd -


echo "====================Copying application jar to docker folder=============================="

cp ../target/scala-2.11/Hello-Akka-assembly-1.0.jar dist/


echo "====================Building docker image================================================="

docker build -t akkaapp .


#echo "====================Running docker container=============================================="
#
#docker run akkaapp