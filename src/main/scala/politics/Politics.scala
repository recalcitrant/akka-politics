package politics

import java.time.LocalDate

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream._
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.util.ByteString
import politics.Constants._

import scala.concurrent.Future

case class Input(name: String,
                 topic: String,
                 date: String,
                 wordCount: String)

case class Speech(topic: String,
                  date: String,
                  wordCount: Int)

case class Aggregate(name: Option[String],
                     speeches: List[Speech],
                     speeches2013Count: Int,
                     securitySpeechesCount: Int,
                     wordCountTotal: Int)

object Politics {

  implicit val actorSystem: ActorSystem = ActorSystem("csv")
  implicit val mat: Materializer = ActorMaterializer()

  def get(urls: Iterable[String]): RunnableGraph[FlowResult] = {
    urls match {
      case Nil => throw new RuntimeException("no urls provided")
      case _ => getGraph(urls)
    }
  }

  def getGraph(urls: Iterable[String]): RunnableGraph[FlowResult] = {

    RunnableGraph.fromGraph(GraphDSL.create(

      Sink.head[(String, Option[String])],
      Sink.head[(String, Option[String])],
      Sink.head[(String, Option[String])])((_, _, _)) {

      implicit builder =>

        (s1, s2, s3) =>

          import GraphDSL.Implicits._

          val SourceCsv: Outlet[Map[String, String]] =
            builder.add(Source(urls.map(uri => HttpRequest(uri = uri)).toList)
              .mapAsync(1)(Http().singleRequest(_)) //: HttpResponse
              .flatMapConcat(getSource) //: ByteString
              .via(CsvParsing.lineScanner()) //: List[ByteString]
              .via(CsvToMap.toMap()) //: Map[String, ByteString]
              .map(normalizeCsv) //: Map[String, String]
            ).out

          val aggregateBroadcast = builder.add(Broadcast[Aggregate](3))

          SourceCsv ~> csvToInput ~> aggregate ~> aggregateBroadcast.in
          s1.in <~ mapAggregate(MostSpeeches) <~ mostSpeechesIn2013 <~ optionShape <~ aggregateBroadcast.out(0)
          s2.in <~ mapAggregate(MostSecurity) <~ mostSecuritySpeeches <~ optionShape <~ aggregateBroadcast.out(1)
          s3.in <~ mapAggregate(LeastWordy) <~ leastWords <~ optionShape <~ aggregateBroadcast.out(2)

          ClosedShape
    })
  }

  val csvToInput: Flow[Map[String, String], Input, NotUsed] = {
    Flow[Map[String, String]].map(m => {
      Input(m.getOrElse(Who, Unavailable).trim,
        m.getOrElse(" " + What, Unavailable).trim,
        m.getOrElse(" " + When, Unavailable).trim,
        m.getOrElse(" " + Words, Unavailable).trim)
    })
  }

  val aggregate: Flow[Input, Aggregate, NotUsed] = Flow[Input]
    .filterNot(i => i.name == Who)
    .groupBy(10, _.name)
    //.async
    .fold(Aggregate(None, List[Speech](), 0, 0, 0)) {
      (aggregate: Aggregate, input: Input) =>
        val totalWordCount = aggregate.wordCountTotal + input.wordCount.toInt
        val speech = Speech(input.topic, input.date, input.wordCount.toInt)
        val speeches = speech :: aggregate.speeches
        val securitySpeechesCount = if (Security == input.topic) aggregate.securitySpeechesCount + 1 else aggregate.securitySpeechesCount
        val speeches2013Count = if (LocalDate.parse(speech.date).getYear == 2013) aggregate.speeches2013Count + 1 else aggregate.speeches2013Count
        Aggregate(Some(input.name), speeches, speeches2013Count, securitySpeechesCount, totalWordCount)
    }.mergeSubstreams

  val optionShape: Flow[Aggregate, Some[Aggregate], NotUsed] = Flow[Aggregate].map(Some(_))

  val leastWords: Flow[Option[Aggregate], Option[Aggregate], NotUsed] = Flow[Option[Aggregate]].reduce {
    (a1: Option[Aggregate], a2: Option[Aggregate]) =>
      if (a1.orNull.wordCountTotal < a2.orNull.wordCountTotal) a1
      else if (a1.orNull.wordCountTotal > a2.orNull.wordCountTotal) a2
      else Some(Aggregate(None, List(), 0, 0, 0))
  }

  val mostSecuritySpeeches: Flow[Option[Aggregate], Option[Aggregate], NotUsed] = Flow[Option[Aggregate]].reduce {
    (a1: Option[Aggregate], a2: Option[Aggregate]) =>
      if (a1.orNull.securitySpeechesCount > a2.orNull.securitySpeechesCount) a1
      else if (a1.orNull.securitySpeechesCount < a2.orNull.securitySpeechesCount) a2
      else Some(Aggregate(None, List(), 0, 0, 0))
  }

  def mapAggregate(key: String): Flow[Option[Aggregate], (String, Option[String]), NotUsed] =
    Flow[Option[Aggregate]].map(aOpt => key -> aOpt.flatMap(ag => ag.name))

  val mostSpeechesIn2013: Flow[Option[Aggregate], Option[Aggregate], NotUsed] = Flow[Option[Aggregate]].reduce {
    (a1: Option[Aggregate], a2: Option[Aggregate]) =>
      if (a1.orNull.speeches2013Count > a2.orNull.speeches2013Count) a1
      else if (a1.orNull.speeches2013Count < a2.orNull.speeches2013Count) a2
      else Some(Aggregate(None, List(), 0, 0, 0))
  }

  def getSource(response: HttpResponse): Source[ByteString, _] =
    response match {
      case HttpResponse(OK, _, entity, _) => entity.dataBytes
      case failure =>
        Source.failed(new RuntimeException(s"failed with: $failure"))
    }

  def normalizeCsv(csv: Map[String, ByteString]): Map[String, String] = {
    csv
      .filterNot { case (key, _) => key.isEmpty }
      .mapValues(s => s.utf8String)
  }
}