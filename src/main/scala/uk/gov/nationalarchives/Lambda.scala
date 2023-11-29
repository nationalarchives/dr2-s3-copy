package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.toTraverseOps
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.circe.parser.decode
import io.circe.{Decoder, HCursor}
import pureconfig.ConfigSource
import pureconfig.module.catseffect.syntax._
import pureconfig.generic.auto._
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  DefaultCredentialsProvider,
  StaticCredentialsProvider
}
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.ssm.SsmAsyncClient
import software.amazon.awssdk.services.ssm.model.{GetParameterRequest, GetParameterResponse}
import software.amazon.awssdk.transfer.s3.model.CompletedUpload
import uk.gov.nationalarchives.Lambda._

import java.io.{InputStream, OutputStream}

class Lambda extends RequestStreamHandler {

  val tnaS3Client: DAS3Client[IO] = DAS3Client[IO]()

  val ssmClient: SsmAsyncClient = SsmAsyncClient
    .builder()
    .credentialsProvider(DefaultCredentialsProvider.create())
    .httpClient(NettyNioAsyncHttpClient.builder().build())
    .region(Region.EU_WEST_2)
    .build()

  val preservicaS3Client: AwsBasicCredentials => DAS3Client[IO] = (awsBasicCredentials: AwsBasicCredentials) => {
    val preservicaAsyncClient = S3AsyncClient
      .crtBuilder()
      .region(Region.EU_WEST_2)
      .credentialsProvider(StaticCredentialsProvider.create(awsBasicCredentials))
      .targetThroughputInGbps(20.0)
      .minimumPartSizeInBytes(10 * 1024 * 1024)
      .build()
    DAS3Client[IO](preservicaAsyncClient)
  }

  private def getParameterResponse(environment: String): IO[GetParameterResponse] = {
    val getParameterRequest = GetParameterRequest
      .builder()
      .name(s"/$environment/preservica/bulk1/s3/user")
      .withDecryption(true)
      .build()
    IO.fromCompletableFuture(IO(ssmClient.getParameter(getParameterRequest)))
  }

  private def getCredentials(getParameterResponse: GetParameterResponse): IO[AwsBasicCredentials] = {
    val valueAsString = getParameterResponse.parameter().value()
    IO.fromEither(decode[AwsBasicCredentials](valueAsString))
  }

  private def processInputObject(
      inputObject: InputObject,
      tnaBucket: String,
      preservicaBucket: String,
      credentials: AwsBasicCredentials
  ): IO[CompletedUpload] = for {
    downloadPublisher <- tnaS3Client.download(tnaBucket, inputObject.key)
    completedUpload <- preservicaS3Client(credentials).upload(
      preservicaBucket,
      inputObject.key,
      inputObject.size,
      downloadPublisher
    )
  } yield completedUpload

  override def handleRequest(inputStream: InputStream, output: OutputStream, context: Context): Unit = {
    for {
      config <- ConfigSource.default.loadF[IO, Config]()
      input <- IO.fromEither(decode[Input](inputStream.readAllBytes().map(_.toChar).mkString))
      parameterResponse <- getParameterResponse(config.environment)
      credentials <- getCredentials(parameterResponse)
      _ <- input.items
        .map(item => processInputObject(item, input.tnaBucket, input.preservicaBucket, credentials))
        .sequence
    } yield ()
  }.unsafeRunSync()
}
object Lambda {
  implicit val inputObjectDecoder: Decoder[InputObject] = (c: HCursor) =>
    for {
      key <- c.downField("Key").as[String]
      size <- c.downField("Size").as[Long]
    } yield InputObject(key, size)

  implicit val inputDecoder: Decoder[Input] = (c: HCursor) =>
    for {
      items <- c.downField("Items").as[List[InputObject]]
      tnaBucket <- c.downField("BatchInput").downField("tnaBucket").as[String]
      preservicaBucket <- c.downField("BatchInput").downField("preservicaBucket").as[String]
    } yield Input(tnaBucket, preservicaBucket, items)

  implicit val credentialsDecoder: Decoder[AwsBasicCredentials] = (c: HCursor) =>
    for {
      accessKey <- c.downField("awsAccessKeyId").as[String]
      secretKey <- c.downField("awsSecretAccessKey").as[String]
    } yield AwsBasicCredentials.create(accessKey, secretKey)

  case class Input(tnaBucket: String, preservicaBucket: String, items: List[InputObject])
  case class InputObject(key: String, size: Long)
  private case class Config(environment: String)
}
