import scala.concurrent.Future

package object politics {

  type FlowResult = (Future[(String, Option[String])], Future[(String, Option[String])], Future[(String, Option[String])])
}