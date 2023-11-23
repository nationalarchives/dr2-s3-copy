import Dependencies._
import uk.gov.nationalarchives.sbt.Log4j2MergePlugin.log4j2MergeStrategy

ThisBuild / organization := "uk.gov.nationalarchives"
ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file(".")).
  settings(
    name := "dr2-s3-copy",
    libraryDependencies ++= Seq(
      circeParser,
      lambdaCore,
      log4jCore,
      log4jSlf4j,
      log4jTemplateJson,
      pureConfig,
      pureConfigCats,
      s3Client,
      ssm,
      mockito % Test,
      reactorTest % Test,
      scalaTest % Test
    ),
    scalacOptions += "-deprecation"
  )
(assembly / assemblyJarName) := "dr2-s3-copy.jar"

scalacOptions ++= Seq("-Wunused:imports", "-Werror")

(assembly / assemblyMergeStrategy) := {
  case PathList(ps@_*) if ps.last == "Log4j2Plugins.dat" => log4j2MergeStrategy
  case _ => MergeStrategy.first
}

