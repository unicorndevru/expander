import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys._

import scalariform.formatter.preferences._

name := "expander"

val expanderV = "0.5.1"

val akkaV = "2.4.12"

val akkaHttpV = "2.4.11"

val json = "com.typesafe.play" %% "play-json" % "2.5.9"

val gitHeadCommitSha = settingKey[String]("current git commit SHA")

val commonScalariform = scalariformSettings :+ (ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveSpaceBeforeArguments, true)
  .setPreference(RewriteArrowSymbols, true))

val commons = Seq(
  organization := "ru.unicorndev",
  scalaVersion := "2.11.8",
  version := expanderV + "." +gitHeadCommitSha.value,
  resolvers ++= Seq(
    "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
    Resolver.bintrayRepo("alari", "generic")
  ),
  gitHeadCommitSha in ThisBuild := Process("git rev-parse --short HEAD").lines.head,
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  bintrayPackageLabels := Seq("scala", "akka-http", "api", "json", "rest"),
  bintrayRepository := "generic"
) ++ commonScalariform

commons

lazy val `expander-core` = (project in file("core")).settings(commons:_*).settings(
  name := "expander-core",
  libraryDependencies ++= Seq(
    json,
    "com.lihaoyi" %% "fastparse" % "0.4.1",
    "org.scalatest" %% "scalatest" % "3.0.0" % Test
  )
)

lazy val `expander-akka` = (project in file("akka")).settings(commons:_*).settings(
  name := "expander-akka",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpV % Provided,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % Test,
    "org.scalatest" %% "scalatest" % "3.0.0" % Test
  )
) .dependsOn(`expander-core`, `resolve`)
  .aggregate(`expander-core`, `resolve`)

lazy val `resolve` = (project in file("resolve")).settings(commons:_*).settings(
  name := "resolve",
  libraryDependencies ++= Seq(
    json,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % Test,
    "org.scalatest" %% "scalatest" % "3.0.0" % Test
  )
)

lazy val `expander` = (project in file("."))
  .dependsOn(`expander-akka`, `expander-core`)
  .aggregate(`expander-akka`, `expander-core`)


offline := true

testOptions in Test += Tests.Argument("junitxml")

parallelExecution in Test := false

fork in Test := true

ivyScala := ivyScala.value map {
  _.copy(overrideScalaVersion = true)
}