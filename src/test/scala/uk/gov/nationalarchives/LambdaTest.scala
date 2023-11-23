package uk.gov.nationalarchives

import cats.effect.IO
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.reactivestreams.Publisher
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.ssm.SsmAsyncClient
import software.amazon.awssdk.services.ssm.model.{GetParameterRequest, GetParameterResponse, Parameter}
import software.amazon.awssdk.transfer.s3.model.CompletedUpload

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

class LambdaTest extends AnyFlatSpec with MockitoSugar {

  val validJson =
    """{"BatchInput": {"tnaBucket": "tna-bucket","preservicaBucket": "preservica-bucket"},"Items": [{"Key": "testKey","Size": 1}]}"""

  val uploadCaptor: ArgumentCaptor[Publisher[ByteBuffer]] = ArgumentCaptor.forClass(classOf[Publisher[ByteBuffer]])
  val tnaS3BucketCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
  val tnaS3KeyCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
  val preservicaS3BucketCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
  val preservicaS3KeyCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
  val preservicaContentLengthCaptor: ArgumentCaptor[Long] = ArgumentCaptor.forClass(classOf[Long])
  val getParameterCaptor: ArgumentCaptor[GetParameterRequest] = ArgumentCaptor.forClass(classOf[GetParameterRequest])

  val mockSsmClient: SsmAsyncClient = mock[SsmAsyncClient]

  case class TestLambda() extends Lambda {
    val mockTnaS3Client: DAS3Client[IO] = mock[DAS3Client[IO]]
    val mockPreservicaS3Client: DAS3Client[IO] = mock[DAS3Client[IO]]

    override val tnaS3Client: DAS3Client[IO] = mockTnaS3Client
    override val preservicaS3Client: AwsBasicCredentials => DAS3Client[IO] = _ => mockPreservicaS3Client
    override val ssmClient: SsmAsyncClient = mockSsmClient

    val downloadResponse: IO[Flux[ByteBuffer]] = IO(Flux.just(ByteBuffer.wrap("test".getBytes())))
    val uploadResponse: IO[CompletedUpload] = IO(
      CompletedUpload.builder.response(PutObjectResponse.builder.build).build
    )
    val parameterValue: Parameter =
      Parameter.builder().value("{\"awsAccessKeyId\": \"access\", \"awsSecretAccessKey\": \"secret\"}").build()
    val getParameterResponse: GetParameterResponse = GetParameterResponse.builder.parameter(parameterValue).build()
    when(tnaS3Client.download(tnaS3BucketCaptor.capture(), tnaS3KeyCaptor.capture()))
      .thenReturn(downloadResponse)
    when(
      mockPreservicaS3Client.upload(
        preservicaS3BucketCaptor.capture(),
        preservicaS3KeyCaptor.capture(),
        preservicaContentLengthCaptor.capture(),
        uploadCaptor.capture()
      )
    )
      .thenReturn(uploadResponse)
    when(mockSsmClient.getParameter(getParameterCaptor.capture()))
      .thenReturn(CompletableFuture.completedFuture(getParameterResponse))
  }
  "handleRequest" should "call the AWS methods with the correct parameters" in {
    TestLambda().handleRequest(new ByteArrayInputStream(validJson.getBytes()), null, null)
    tnaS3BucketCaptor.getValue should equal("tna-bucket")
    tnaS3KeyCaptor.getValue should equal("testKey")
    preservicaS3BucketCaptor.getValue should equal("preservica-bucket")
    preservicaS3KeyCaptor.getValue should equal("testKey")
    preservicaContentLengthCaptor.getValue should equal(1)
    getParameterCaptor.getValue.name() should equal("/test/preservica/bulk1/s3/user")
    StepVerifier.create(uploadCaptor.getValue).expectNext(ByteBuffer.wrap("test".getBytes())).expectComplete().verify()
  }

  "handleRequest" should "return an error if the input json is missing required fields" in {
    val json = "{}"
    val ex = intercept[Exception] {
      TestLambda().handleRequest(new ByteArrayInputStream(json.getBytes()), null, null)
    }
    ex.getMessage should equal("DecodingFailure at .Items: Missing required field")
  }

  "handleRequest" should "return an error if the response from parameter store is in an invalid format" in {
    val parameterValue: Parameter = Parameter.builder().value("{\"invalid\": \"invalidValue\"}").build()
    val getParameterResponse: GetParameterResponse = GetParameterResponse.builder.parameter(parameterValue).build()
    val testLambda = TestLambda()
    reset(mockSsmClient)
    when(mockSsmClient.getParameter(any[GetParameterRequest]))
      .thenReturn(CompletableFuture.completedFuture(getParameterResponse))
    val ex = intercept[Exception] {
      testLambda.handleRequest(new ByteArrayInputStream(validJson.getBytes()), null, null)
    }
    ex.getMessage should equal("DecodingFailure at .awsAccessKeyId: Missing required field")
  }
}
