package com.evernym.extension.agency.platform.transport

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives.{complete, logRequestResult, options, path, pathPrefix, post, _}
import akka.http.scaladsl.server.{Directive0, Route}
import akka.stream.Materializer
import com.evernym.agent.api._
import com.evernym.agent.common.a2a.{AnonCryptedMsg, AuthCryptedMsg}
import com.evernym.agent.common.actor.{AgentDetail, InitAgent}
import com.evernym.extension.agency.common.AgencyJsonTransformationUtil

import scala.concurrent.{ExecutionContextExecutor, Future}


trait CorsSupport {

  def configProvider: ConfigProvider

  //this directive adds access control headers to normal responses
  private def addAccessControlHeaders(): Directive0 = {
    respondWithHeaders(
      //TODO: Insecure way of handling CORS, Consider securing it before moving to production
      `Access-Control-Allow-Origin`.*,
      `Access-Control-Allow-Credentials`(true),
      `Access-Control-Allow-Headers`("Origin", "Authorization", "Accept", "Content-Type")
    )
  }

  //this handles preFlight OPTIONS requests.
  private def preFlightRequestHandler: Route = options {
    complete(HttpResponse(StatusCodes.OK).
      withHeaders(`Access-Control-Allow-Methods`(OPTIONS, HEAD, POST, PUT, GET, DELETE)))
  }

  def corsHandler(r: Route): Route = addAccessControlHeaders() {
    preFlightRequestHandler ~ r
  }
}

class AgencyTransportPlatform(commonParam: CommonParam, val msgHandler: PartialFunction[Any, Future[Any]])
  extends TransportPlatform with CorsSupport
    with AgencyJsonTransformationUtil {

  override def configProvider: ConfigProvider = commonParam.configProvider
  implicit def system: ActorSystem = commonParam.actorSystem
  implicit def materializer: Materializer = commonParam.materializer

  implicit val executor: ExecutionContextExecutor = commonParam.actorSystem.dispatcher

  def msgResponseHandler: PartialFunction[Any, ToResponseMarshallable] = {
    case ad: AgentDetail => ad
    case a2aMsg: AuthCryptedMsg => HttpEntity(MediaTypes.`application/octet-stream`, a2aMsg.payload)
  }

  def sendToAgentPlatformMsgHandler(msg: Any): Route = {
    complete {
      msgHandler(TransportMsg("extension-agency-agent-transport", GenericMsg(msg))).map[ToResponseMarshallable] {
        msgResponseHandler
      }
    }
  }

  def handleAgentMsgReq(): Route = {
    extractRequest { implicit req: HttpRequest =>
      post {
        import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.byteArrayUnmarshaller
        req.entity.contentType.mediaType match {
          case MediaTypes.`application/octet-stream` =>
            entity(as[Array[Byte]]) { data =>
              sendToAgentPlatformMsgHandler(AnonCryptedMsg(data))
            }
          case _ => reject
        }
      }
    }
  }

  lazy val route: Route = logRequestResult("extension-agency-agent-transport") {
    pathPrefix("agency") {
      path("init") {
        (post & entity(as[InitAgent])) { ai =>
          sendToAgentPlatformMsgHandler(ai)
        }
      } ~
        path("msg") {
          handleAgentMsgReq()
        }
    }
  }

  override def start(inputParam: Option[Any]=None): Unit = {
    Http().bindAndHandle(corsHandler(route), "0.0.0.0", 7000)
  }

  override def stop(): Unit = {
    println("TODO: agency-transport-platform stopped")
  }
}


class AgencyTransportPlatformExtension extends Extension {

  override val name: String = "extension-agency-transport-platform"
  override val category: String = "transport-platform"

  var transport: TransportPlatform = _

  override def getSupportedMsgTypes: Set[MsgType] = Set.empty

  override def handleMsg: PartialFunction[Any, Future[Any]] = {
    case m =>
      Future.failed(throw new RuntimeException("this extension doesn't support any message"))
  }

  override def start(inputParam: Option[Any] = None): Unit = {
    val transportExtensionParam: TransportExtensionParam = inputParam.map(_.asInstanceOf[TransportExtensionParam]).getOrElse(
      throw new RuntimeException("invalid input parameter"))
    transport = new AgencyTransportPlatform(transportExtensionParam.commonParam, transportExtensionParam.msgHandler)
    transport.start()
  }

  override def stop(): Unit = {
    transport.stop()
    println(s"$name stopped")
  }
}