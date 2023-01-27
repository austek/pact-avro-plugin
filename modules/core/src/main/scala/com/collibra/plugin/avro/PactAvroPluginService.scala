package com.collibra.plugin.avro

import com.google.common.io.BaseEncoding
import com.google.protobuf.ByteString
import com.google.protobuf.empty.Empty
import com.google.protobuf.struct.{Struct, Value}
import com.typesafe.scalalogging.StrictLogging
import io.pact.plugin.Body.ContentTypeHint
import io.pact.plugin._
import org.apache.avro.Schema

import java.nio.file.Path
import java.security.MessageDigest
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class PactAvroPluginService extends PactPlugin with StrictLogging {

  private val contentTypeApplicationAvro = "application/avro"
  private val contentTypeAvroBytes = "avro/bytes"
  private val contentTypeAvroBinary = "avro/binary"
  private val contentTypeAvroWildcard = "application/*+avro"
  private val contentTypes = Seq(
    contentTypeApplicationAvro,
    contentTypeAvroBytes,
    contentTypeAvroBinary,
    contentTypeAvroWildcard
  )
  private val contentTypesStr = contentTypes.mkString(";")

  private val messageTypeKey = "message-type"

  /** Check that the plugin loaded OK. Returns the catalogue entries describing what the plugin provides
    */
  override def initPlugin(request: InitPluginRequest): Future[InitPluginResponse] = {
    logger.debug(s"Init request from ${request.implementation}/${request.version}")
    Future.successful(
      InitPluginResponse.apply(
        Seq(
          CatalogueEntry(
            CatalogueEntry.EntryType.CONTENT_MATCHER,
            "avro",
            Map("content-types" -> contentTypesStr)
          ),
          CatalogueEntry(
            CatalogueEntry.EntryType.CONTENT_GENERATOR,
            "avro",
            Map("content-types" -> contentTypesStr)
          ),
          CatalogueEntry(
            CatalogueEntry.EntryType.TRANSPORT,
            "avro"
          )
        )
      )
    )
  }

  /** Updated catalogue. This will be sent when the core catalogue has been updated (probably by a plugin loading).
    */
  override def updateCatalogue(in: Catalogue): Future[Empty] = {
    logger.debug("Got update catalogue request: TODO")
    Future.successful(Empty())
  }

  /** Request to configure/setup the interaction for later verification. Data returned will be persisted in the pact file.
    */
  override def configureInteraction(in: ConfigureInteractionRequest): Future[ConfigureInteractionResponse] = {
    logger.info(s"Configure interaction request for content type '${in.contentType}': $in")

    (for {
      configuration <- getConfiguration(in.contentsConfig, "Configuration not found")
//      _ <- getContentType(configuration)
      avroSchema <- getAvroSchema(configuration)
      messageType <- getMessageType(configuration)
    } yield {
      logger.debug("Digest avro file")
      val digest: MessageDigest = MessageDigest.getInstance("MD5")
      digest.update(avroSchema.toString.getBytes)
      val avroSchemaHash: String = BaseEncoding.base16().lowerCase().encode(digest.digest())
      logger.debug("Start to build response")
      val response = ConfigureInteractionResponse(
        interaction = Seq(
          InteractionResponse(
            contents = Some(
              Body(
                s"$contentTypeAvroBinary;message=$messageType",
                Some(ByteString.copyFromUtf8("""{ "key" :"value"}""")),
                ContentTypeHint.TEXT
              )
            ),
            rules = Map(
              "implementation" -> MatchingRules(Seq(MatchingRule("not-empty", None)))
            ),
            pluginConfiguration = Some(
              PluginConfiguration(
                interactionConfiguration = Some(Struct(Map("custom-field" -> Value(Value.Kind.StringValue("custom-value")))))
              )
            ),
            interactionMarkup = """
                |""".stripMargin,
            partName = "Some-Name"
          )
        ),
        pluginConfiguration = Some(
          PluginConfiguration(
            pactConfiguration = Some(
              Struct(
                Map(
                  avroSchemaHash -> Value(
                    Value.Kind.StructValue(
                      Struct(
                        Map(
                          "avroSchema" -> Value(Value.Kind.StringValue(avroSchema.toString))
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )
      logger.debug("return response")
      response
    }) match {
      case Right(response) =>
        logger.debug(s"Responding: $response")
        Future.successful(response)
      case Left(msg) =>
        logger.error(s"Configure interaction failed: $msg")
        Future.successful(ConfigureInteractionResponse(error = msg))
    }
  }

  /** Request to perform a comparison of some contents (matching request)
    */
  override def compareContents(request: CompareContentsRequest): Future[CompareContentsResponse] = {
    logger.debug(s"Got compareContents request $request")

    (request.actual, request.expected) match {
      case (Some(actualBody), Some(expectedBody)) =>
        if (actualBody.contentType != expectedBody.contentType) {
          Future.successful(CompareContentsResponse(error = "Content types don't match"))
        } else if (contentTypes.contains(actualBody.contentType)) {
          Future.successful(
            CompareContentsResponse(
              error = s"Actual body is not one of '$contentTypesStr' content type",
              typeMismatch = Some(ContentTypeMismatch(contentTypesStr, actualBody.contentType))
            )
          )
        } else if (contentTypes.contains(expectedBody.contentType)) {
          Future.successful(
            CompareContentsResponse(
              error = s"Expected body is not of '$contentTypesStr' content type",
              typeMismatch = Some(ContentTypeMismatch(contentTypesStr, expectedBody.contentType))
            )
          )
        } else {
          logger.info(s"Actual ${actualBody.content.get.toStringUtf8}")
          logger.info(s"Expected ${expectedBody.content.get.toStringUtf8}")
          Future.successful(CompareContentsResponse(results = Map.empty))
        }
      case (_, _) =>
        val msg = "Both actual and expected body are required"
        logger.error(msg)
        Future.successful(CompareContentsResponse(error = msg))
    }
  }

  /** Request to generate the content using any defined generators
    */
  override def generateContent(in: GenerateContentRequest): Future[GenerateContentResponse] = {
    logger.debug("Generate Content")
    Future.successful(GenerateContentResponse())
  }

  /** Start a mock server
    */
  override def startMockServer(in: StartMockServerRequest): Future[StartMockServerResponse] = {
    logger.debug("Start Mock Server")
    Future.successful(StartMockServerResponse(StartMockServerResponse.Response.Empty))
  }

  /** Shutdown a running mock server TODO: Replace the message types with MockServerRequest and MockServerResults in the next major version
    */
  override def shutdownMockServer(in: ShutdownMockServerRequest): Future[ShutdownMockServerResponse] = {
    logger.debug("Shutdown Mock Server")
    Future.successful(ShutdownMockServerResponse(ok = true))
  }

  /** Get the matching results from a running mock server
    */
  override def getMockServerResults(in: MockServerRequest): Future[MockServerResults] = {
    logger.debug("Get Mock Server results")
    Future.successful(MockServerResults(ok = true))
  }

  /** Prepare an interaction for verification. This should return any data required to construct any request so that it can be amended before the verification
    * is run
    */
  override def prepareInteractionForVerification(in: VerificationPreparationRequest): Future[VerificationPreparationResponse] = {
    logger.debug("Prepare interaction for verification")
    Future.successful(VerificationPreparationResponse())
  }

  /** Execute the verification for the interaction.
    */
  override def verifyInteraction(in: VerifyInteractionRequest): Future[VerifyInteractionResponse] = {
    logger.debug("Verify interaction")
    Future.successful(VerifyInteractionResponse())
  }

  private def getConfigValue(configs: Map[String, Value], id: String, msg: String): Either[String, Value] =
    configs.get(id) match {
      case Some(value) => Right(value)
      case None        => Left(msg)
    }

  private def getConfigStringValue(configs: Map[String, Value], id: String, msg: String): Either[String, String] =
    getConfigValue(configs, id, msg).map(_.getStringValue).filterOrElse(!_.isBlank, msg)

  private def getConfiguration(structOpt: Option[Struct], errorMsg: String): Either[String, Struct] =
    structOpt match {
      case Some(struct) => Right(struct)
      case None         => Left(errorMsg)
    }

  private def getAvroSchema(configuration: Struct): Either[String, Schema] = {
    getConfigStringValue(
      configuration.fields,
      "pact:avro",
      "Config item with key 'pact:avro' and path to the avro file is required"
    ).flatMap { avroFilePath =>
      Try(new Schema.Parser().parse(Path.of(avroFilePath).toFile)) match {
        case Success(schema) => Right(schema)
        case Failure(exception) =>
          exception.printStackTrace()
          Left(exception.getMessage)
      }
    }
  }

  private def getMessageType(configuration: Struct): Either[String, String] =
    getConfigStringValue(
      configuration.fields,
      s"pact:$messageTypeKey",
      s"Config item with key 'pact:$messageTypeKey' and $messageTypeKey of the payload is required"
    )
}
