organization := "net.successk"

name := "k-akka-openid"

version := "0.1.0"

scalaVersion := "2.11.7"

scalacOptions := Seq("-encoding", "utf8")

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

libraryDependencies ++= {
  Seq(
    "com.typesafe.akka" %% "akka-http-experimental" % "2.0.2",
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "2.0.2",
    "org.scala-lang.modules" %% "scala-xml" % "1.0.5",
    "com.nimbusds" % "nimbus-jose-jwt" % "4.11",

    "org.scalatest" %% "scalatest" % "2.2.6" % "test",
    "com.typesafe.akka" %% "akka-testkit" % "2.3.12" % "test",
    "com.typesafe.akka" %% "akka-http-testkit-experimental" % "2.0.2" % "test"
  )
}