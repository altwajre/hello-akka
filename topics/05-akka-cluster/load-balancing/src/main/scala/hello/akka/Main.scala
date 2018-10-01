package hello.akka

object Main extends App {

  Backend.initiate(2551)
  Backend.initiate(2552)
  Backend.initiate(2561)

  Frontend.initiate()

}
