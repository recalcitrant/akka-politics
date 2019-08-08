package politics

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

class PoliticsSpec extends FlatSpec with Matchers {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val url = "http://localhost:8080/politics.csv"
  
  "A politics flow" should "return a tuple of three futures containing valid results" in {

    val result = Politics.get(List(url)).run()

    val nullResult = Await.result(result._1, 3.seconds)
    val sResult = Await.result(result._2, 3.seconds)
    val lResult = Await.result(result._3, 3.seconds)

    nullResult._1 should equal("mostSpeeches")
    nullResult._2 should equal(None)

    sResult._1 should equal("mostSecurity")
    sResult._2.get should equal("Alexander Abel")

    lResult._1 should equal("leastWordy")
    lResult._2.get should equal("Caesare Collins")
  }

  it should "throw a RuntimeException if no urls are passed" in {
    a[RuntimeException] should be thrownBy {
      Await.result(Politics.get(List()).run()._1, 3.seconds)
    }
  }

  it should "throw an Exception if an invalid url is passed" in {
    an[Exception] should be thrownBy {
      Await.result(Politics.get(List("http://localhost:6666/foo")).run()._1, 10.seconds)
    }
  }
}
