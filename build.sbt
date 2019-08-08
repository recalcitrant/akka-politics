name := "akka-politics"
version := "1.0"
scalaVersion := "2.12.6"

Compile / run / fork := true
javaOptions += "-Xmx256M"
javaOptions += "-Xms32M"

val scalaTestVersion = "3.0.8"
val akkaHttpVersion = "10.1.8"
val alpakkaVersion = "1.0.0"
val akkaVersion = "2.5.21"

libraryDependencies ++= Seq(
  "com.lightbend.akka" %% "akka-stream-alpakka-csv" % alpakkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  // Used from Scala
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "org.scalactic" %% "scalactic" % "3.0.8" % Test,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)
