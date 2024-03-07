import sbt._
object Dependencies {
  private lazy val logbackVersion = "2.23.0"
  private val log4CatsVersion = "2.6.0"

  lazy val log4jSlf4j = "org.apache.logging.log4j" % "log4j-slf4j-impl" % logbackVersion
  lazy val log4jCore = "org.apache.logging.log4j" % "log4j-core" % logbackVersion
  lazy val log4jTemplateJson = "org.apache.logging.log4j" % "log4j-layout-template-json" % logbackVersion
  lazy val log4CatsCore = "org.typelevel" %% "log4cats-core" % log4CatsVersion;
  lazy val log4CatsSlf4j = "org.typelevel" %% "log4cats-slf4j" % log4CatsVersion
  lazy val lambdaCore = "com.amazonaws" % "aws-lambda-java-core" % "1.2.3"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.18"
  lazy val s3Client = "uk.gov.nationalarchives" %% "da-s3-client" % "0.1.37"
  lazy val ssm = "software.amazon.awssdk" % "ssm" % "2.25.4"
  lazy val circeParser = "io.circe" %% "circe-parser" % "0.14.6"
  lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % "0.17.6"
  lazy val pureConfigCats = "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.6"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.17.30"
  lazy val reactorTest = "io.projectreactor" % "reactor-test" % "3.6.2"
}
