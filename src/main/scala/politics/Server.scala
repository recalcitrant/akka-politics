package politics

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import spray.json.{DefaultJsonProtocol, NullOptions}

import scala.concurrent.{ExecutionContextExecutor, Future}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {
}

object Server extends Directives with JsonSupport {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  def main(args: Array[String]) {

    val route =
      path("evaluation") {
        get {
          parameters('url.*) { urls =>
            onSuccess(Future {
              Politics.get(urls).run()
            }) {
              case item: FlowResult =>
                complete(for {
                  r1 <- item._1
                  r2 <- item._2
                  r3 <- item._3
                } yield (List(r1, r2, r3)).toMap)
              case _ =>
                complete(StatusCodes.NotFound)
            }
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 7070)
    println(s"Server online at http://localhost:7070")
    /*StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())*/
  }
}