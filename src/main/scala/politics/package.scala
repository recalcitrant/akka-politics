import scala.concurrent.Future

package object politics {

  type CSVFuture = (Future[(String, Option[String])], Future[(String, Option[String])], Future[(String, Option[String])])
}