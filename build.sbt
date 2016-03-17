import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys._

import scalariform.formatter.preferences._

name := "expander"

version := "0.1"

val expanderV = "0.4.0"

scalaVersion := "2.11.7"

val gitHeadCommitSha = settingKey[String]("current git commit SHA")

val commonScalariform = scalariformSettings :+ (ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveSpaceBeforeArguments, true)
  .setPreference(RewriteArrowSymbols, true))

val commons = Seq(
  organization := "ru.unicorndev",
  scalaVersion := "2.11.7",
  resolvers ++= Seq(
    "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
    Resolver.bintrayRepo("alari", "generic")
  ),
  gitHeadCommitSha in ThisBuild := Process("git rev-parse --short HEAD").lines.head,
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  bintrayPackageLabels := Seq("scala", "play", "api"),
  bintrayRepository := "generic"
) ++ commonScalariform

commons

lazy val `expander-core` = (project in file("core")).settings(commons:_*).settings(
  name := "expander-core",
  version := expanderV + "." +gitHeadCommitSha.value,
  libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play-json" % "2.5.0",
    "com.lihaoyi" %% "fastparse" % "0.2.1",
    "org.scalatest" %% "scalatest" % "2.2.5" % Test,
    "junit" % "junit" % "4.12" % Test
  )
)

lazy val `expander` = (project in file("."))
  .dependsOn(`expander-core`)
  .aggregate(`expander-core`)


offline := true

testOptions in Test += Tests.Argument("junitxml")

parallelExecution in Test := false

fork in Test := true

ivyScala := ivyScala.value map {
  _.copy(overrideScalaVersion = true)
}