import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys._

import scalariform.formatter.preferences._

name := "expander-gather"

val expanderV = "0.3.3"

scalaVersion := "2.11.7"


val commonScalariform = scalariformSettings :+ (ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveSpaceBeforeArguments, true)
  .setPreference(RewriteArrowSymbols, true))

val commons = Seq(
  organization := "me.passenger",
  scalaVersion := "2.11.7",
  resolvers ++= Seq(
    "dgtl" at "http://dev.dgtl.pro/repo/",
    "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
    Resolver.sonatypeRepo("snapshots")
  ),
  publishTo := Some(Resolver.file("file", new File("/mvn-repo")))
) ++ commonScalariform

commons

lazy val `psgr-expander` = (project in file("."))
  .dependsOn(`psgr-expander-realtime`)
  .aggregate(`psgr-expander-realtime`)

lazy val `psgr-failures` = (project in file("failures")).settings(commons: _*).settings(
  name := "failures",
  version := "0.1.0",
  libraryDependencies ++= Seq(
    json % Provided
  )
)

lazy val `psgr-expander-core` = (project in file("core")).settings(commons: _*).settings(
  name := "expander",
  version := expanderV,
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "fastparse" % "0.2.1",
    json % Provided,
    "org.specs2" %% "specs2-core" % "3.6" % Test
  )
)

lazy val `psgr-expander-protocol` = (project in file("protocol")).settings(commons: _*).settings(
  name := "expander-protocol",
  version := expanderV,
  libraryDependencies ++= Seq(
    json % Provided,
    "org.specs2" %% "specs2-core" % "3.6" % Test
  )
)

lazy val `psgr-expander-play` = (project in file("play")).settings(commons: _*).settings(
  name := "expander-play",
  version := expanderV,
  libraryDependencies ++= Seq(
    ws % Provided
  )
).enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)
  .dependsOn(`psgr-expander-core`, `psgr-expander-protocol`, `psgr-failures`)
  .aggregate(`psgr-expander-core`, `psgr-expander-protocol`, `psgr-failures`)

lazy val `psgr-expander-realtime` = (project in file("realtime")).settings(commons: _*).settings(
  name := "expander-realtime",
  version := expanderV,
  libraryDependencies ++= Seq(
    ws % Provided,
    specs2 % Test
  )
).enablePlugins(PlayScala)
  .disablePlugins(PlayLayoutPlugin)
  .dependsOn(`psgr-expander-play`)
  .aggregate(`psgr-expander-play`)

offline := true

testOptions in Test += Tests.Argument("junitxml")

parallelExecution in Test := false

fork in Test := true

ivyScala := ivyScala.value map {
  _.copy(overrideScalaVersion = true)
}
