import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys._

import scalariform.formatter.preferences._

name := "expander"

val expanderV = "0.4.2"

val akkaV = "2.4.8"

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
    "com.typesafe.play" %% "play-json" % "2.5.3",
    "com.lihaoyi" %% "fastparse" % "0.3.7",
    "org.scalatest" %% "scalatest" % "2.2.5" % Test,
    "junit" % "junit" % "4.12" % Test
  )
)

lazy val `expander-akka` = (project in file("akka")).settings(commons:_*).settings(
  name := "expander-akka",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaV % Test,
    "org.scalatest" %% "scalatest" % "2.2.5" % Test
  )
) .dependsOn(`expander-core`)
  .aggregate(`expander-core`)

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