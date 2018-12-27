package com.evernym.extension.agency.transport.http.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Route}
import akka.stream.Materializer
import com.evernym.agent.api._


import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait CorsSupport {

  def config: ConfigProvider

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


class AgencyAPIExtension extends Extension {

  override val name: String = "agent-ext-agency-api"
  override val category: String = "transport.http.akka"

  var transport: Transport= _

  override def init(inputParam: Option[Any] = None): Unit = {
    val extParam = inputParam.asInstanceOf[Option[CommonParam]].
      getOrElse(throw new RuntimeException("unexpected input parameter"))
    transport = new AgencyAPI(extParam)
    transport.start()
  }

  override def handleMsg(msg: Any): Future[Any] = {
    Future.failed(throw new RuntimeException("this extension doesn't support any message"))
  }

  override def getSupportedMsgTypes: Set[MsgType] = Set.empty
}


class AgencyAPI(commonParam: CommonParam) extends Transport with CorsSupport {

  override def config: ConfigProvider = commonParam.config
  implicit def system: ActorSystem = commonParam.actorSystem
  implicit def materializer: Materializer = commonParam.materializer

  override def start(): Unit = {
    lazy val route: Route = logRequestResult("agent-ext-agency-api") {
      pathPrefix("agency") {
        path("msg") {
          post {
            complete("successful-agency-api")
          }
        }
      }
    }
    Http().bindAndHandle(corsHandler(route), "0.0.0.0", 7000)
  }

  override def stop(): Unit = {
    println("agency-api extension transport stopped")
  }

}
