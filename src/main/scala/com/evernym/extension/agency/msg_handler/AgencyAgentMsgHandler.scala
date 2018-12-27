package com.evernym.extension.agency.msg_handler

import akka.actor.ActorRef
import akka.pattern.ask
import com.evernym.agent.api.{AgentMsgHandler, CommonParam, RoutingAgent}
import com.evernym.agent.common.actor.AgentActorCommonParam
import com.evernym.agent.common.util.TransformationUtilBase
import com.evernym.extension.agency.common.{ActorRefResolver, GeneralTimeout, JsonTransformationUtil}
import com.evernym.extension.agency.msg_handler.actor.{GetRoute, RouteDetail, SetRoute, SimpleRoutingAgent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class DefaultRoutingAgent(implicit val param: CommonParam)
  extends RoutingAgent
    with TransformationUtilBase
    with JsonTransformationUtil
    with GeneralTimeout
    with ActorRefResolver {

  val ACTOR_TYPE_USER_AGENT_ACTOR = 1
  val ACTOR_TYPE_USER_AGENT_PAIRWISE_ACTOR = 2

  val ACTOR_TYPE_AGENCY_AGENT_ACTOR = 3
  val ACTOR_TYPE_AGENCY_AGENT_PAIRWISE_ACTOR = 4

  val routingAgent: ActorRef = param.actorSystem.actorOf(SimpleRoutingAgent.props(param.config))

  def getTargetActorRef(routeJson: String): ActorRef = {
    val routeDetail = convertJsonToNativeMsg[RouteDetail](routeJson)
    routeDetail.actorTypeId match {
      case ACTOR_TYPE_USER_AGENT_ACTOR => agencyAgentRegion   //TODO: replace with correct region actor
      case ACTOR_TYPE_USER_AGENT_PAIRWISE_ACTOR => agencyAgentPairwiseRegion //TODO: replace with correct region actor
      case ACTOR_TYPE_AGENCY_AGENT_ACTOR => agencyAgentRegion
      case ACTOR_TYPE_AGENCY_AGENT_PAIRWISE_ACTOR => agencyAgentPairwiseRegion
    }
  }

  def setRoute(forId: String, routeJson: String): Future[Either[Throwable, String]] = {
    val futResp = routingAgent ? SetRoute(forId, routeJson)
    futResp map {
      case r: String => Right(r)
      case x => Left(new RuntimeException(s"error while setting route: ${x.toString}"))
    }
  }

  def getRoute(forId: String): Future[Either[Throwable, String]] = {
    val futResp = routingAgent ? GetRoute(forId)
    futResp map {
      case r: String => Right(r)
      case x => Left(new RuntimeException(s"error while getting route: ${x.toString}"))
    }
  }

  def routeMsgToAgent(toId: String, msg: Any): Future[Either[Throwable, Any]] = {
    getRoute(toId).flatMap {
      case Right(r: String) =>
        val actorRef = getTargetActorRef(r)
        val futResp = actorRef ? msg
        futResp map {
          case r: Any => Right(r)
          case _ => Left(new RuntimeException(s"error while sending msg to route: $r"))
        }
      case x => Future(Left(new RuntimeException(s"error while getting route: ${x.toString}")))
    }
  }
}


class CoreAgentMsgHandler(val agentCommonParam: AgentActorCommonParam)
  extends AgentMsgHandler with ActorRefResolver{

  implicit def param: CommonParam = agentCommonParam.commonParam


  def handleMsg(msg: Any): Future[Any] = {
    msg match {
      //case tam: TransportAgnosticMsg => userAgent ? tam.payload
      case _ => Future("not implemented")
    }
  }
}


