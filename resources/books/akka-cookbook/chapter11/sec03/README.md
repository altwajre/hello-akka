Browse: http://localhost:9000/

curl -X POST -H "Content-Type: text/plain" --data "UPPERCASE" http://localhost:9000/toLowercase

curl -X POST -H "Content-Type: text/plain" --data "lowercase" http://localhost:9000/toUppercase

curl -X GET http://localhost:9000/isEmpty/notEmpty

curl -X GET http://localhost:9000/areEqual/something/another/somethingElse