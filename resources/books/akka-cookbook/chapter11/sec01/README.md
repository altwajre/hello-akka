curl -v -X GET http://localhost:9000/api/hello/hveiga

curl -v -H "Content-Type: application/json" -X POST -d '{"message":"Hello Lagom!"}' http://localhost:9000/api/hello/hveiga

curl -v -X GET http://localhost:9000/api/healthcheck