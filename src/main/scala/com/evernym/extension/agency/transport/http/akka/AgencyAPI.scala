package com.evernym.extension.agency.transport.http.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.{complete, logRequestResult, options, path, pathPrefix, post}
import akka.http.scaladsl.server.{Directive0, Route}
import akka.stream.Materializer
import com.evernym.agent.api._
import com.evernym.agent.common.a2a.{AnonCryptedMsg, AuthCryptedMsg}
import com.evernym.agent.common.actor.{InitAgent, JsonTransformationUtil}
import com.evernym.extension.agency.platform.PlatformBase

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

class AgencyAPI(commonParam: CommonParam, val transportMsgRouter: TransportMsgRouter)
  extends Transport with CorsSupport
    with JsonTransformationUtil {

  override def configProvider: ConfigProvider = commonParam.configProvider
  implicit def system: ActorSystem = commonParam.actorSystem
  implicit def materializer: Materializer = commonParam.materializer

  implicit val executor: ExecutionContextExecutor = commonParam.actorSystem.dispatcher

  def msgResponseHandler: PartialFunction[Any, ToResponseMarshallable] = {
    case a2aMsg: AuthCryptedMsg =>
      HttpEntity(MediaTypes.`application/octet-stream`, a2aMsg.payload)
  }

  lazy val route: Route = logRequestResult("agent-ext-agency-api") {
    pathPrefix("agency") {
      path("init") {
        (post & entity(as[InitAgent])) { ai =>
          complete {
            transportMsgRouter.handleMsg(TransportAgnosticMsg(ai)).map[ToResponseMarshallable] {
              msgResponseHandler
            }
          }
        }
      } ~
        path("msg") {
          extractRequest { implicit req: HttpRequest =>
            post {
              import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.byteArrayUnmarshaller
              req.entity.contentType.mediaType match {
                case MediaTypes.`application/octet-stream` =>
                  entity(as[Array[Byte]]) { data =>
                    complete {
                      transportMsgRouter.handleMsg(TransportAgnosticMsg(AnonCryptedMsg(data))).map[ToResponseMarshallable] {
                        msgResponseHandler
                      }
                    }
                  }
                case _ => reject
              }
            }
          }
        }
    }
  }

  override def start(): Unit = {
    Http().bindAndHandle(corsHandler(route), "0.0.0.0", 7000)
  }

  override def stop(): Unit = {
    println("agency-api extension transport stopped")
  }

}

class Platform (implicit val commonParam: CommonParam) extends PlatformBase

class AgencyAPIExtension extends Extension {

  override val name: String = "agent-ext-agency-api"
  override val category: String = "transport.http.akka"

  override def init(inputParam: Option[Any] = None): Unit = {
    implicit val commonParam: CommonParam = inputParam.asInstanceOf[Option[CommonParam]].
      getOrElse(throw new RuntimeException("unexpected input parameter"))
    new Platform
  }

  override def handleMsg(msg: Any): Future[Any] = {
    Future.failed(throw new RuntimeException("this extension doesn't support any message"))
  }

  override def getSupportedMsgTypes: Set[MsgType] = Set.empty
}