package com.evernym.extension.agency.msg_handler

import akka.actor.ActorRef
import akka.pattern.ask
import com.evernym.agent.api.{AgentMsgHandler, CommonParam, RoutingAgent, TransportAgnosticMsg}
import com.evernym.agent.common.actor._
import com.evernym.extension.agency.common.Constants._
import com.evernym.extension.agency.msg_handler.actor.ForId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class DefaultRoutingAgent(implicit val param: CommonParam)
  extends RoutingAgent
    with JsonTransformationUtil
    with ActorRefResolver {

  val ACTOR_TYPE_USER_AGENT_REGION_ACTOR = 1
  val ACTOR_TYPE_USER_AGENT_PAIRWISE_REGION_ACTOR = 2

  val ACTOR_TYPE_AGENCY_AGENT_REGION_ACTOR = 3
  val ACTOR_TYPE_AGENCY_AGENT_PAIRWISE_REGION_ACTOR = 4

  val routingAgent: ActorRef = param.actorSystem.actorOf(SimpleRoutingAgent.props(param.configProvider))

  def getTargetActorRef(routeJson: String): ActorRef = {
    val routeDetail = convertJsonToNativeMsg[RouteDetail](routeJson)
    routeDetail.actorTypeId match {
      case ACTOR_TYPE_USER_AGENT_REGION_ACTOR =>
        agentActorRefReq(USER_AGENT_REGION_ACTOR_NAME, s"$SHARDED_ACTOR_PATH_PREFIX/$USER_AGENT_REGION_ACTOR_NAME")
      case ACTOR_TYPE_USER_AGENT_PAIRWISE_REGION_ACTOR =>
        agentActorRefReq(USER_AGENT_PAIRWISE_REGION_ACTOR_NAME, s"$SHARDED_ACTOR_PATH_PREFIX/$USER_AGENT_PAIRWISE_REGION_ACTOR_NAME")

      case ACTOR_TYPE_AGENCY_AGENT_REGION_ACTOR =>
        agentActorRefReq(AGENCY_AGENT_REGION_ACTOR_NAME, s"$SHARDED_ACTOR_PATH_PREFIX/$AGENCY_AGENT_REGION_ACTOR_NAME")
      case ACTOR_TYPE_AGENCY_AGENT_PAIRWISE_REGION_ACTOR =>
        agentActorRefReq(AGENCY_AGENT_PAIRWISE_REGION_ACTOR_NAME, s"$SHARDED_ACTOR_PATH_PREFIX/$AGENCY_AGENT_PAIRWISE_REGION_ACTOR_NAME")
    }
  }

  override def setRoute(forId: String, routeJson: String): Future[Either[Throwable, Any]] = {
    val futResp = routingAgent ? SetRoute(forId, routeJson)
    futResp map {
      case r: RouteSet => Right(r)
      case x => Left(new RuntimeException(s"error while setting route: ${x.toString}"))
    }
  }

  override def getRoute(forId: String): Future[Either[Throwable, String]] = {
    val futResp = routingAgent ? GetRoute(forId)
    futResp map {
      case Some(r: String) => Right(r)
      case x => Left(new RuntimeException(s"error while getting route: ${x.toString}"))
    }
  }

  override def sendMsgToAgent(toId: String, msg: Any): Future[Either[Throwable, Any]] = {
    getRoute(toId).flatMap {
      case Right(r: String) =>
        val actorRef = getTargetActorRef(r)
        val futResp = actorRef ? ForId(toId, msg)
        futResp map {
          case r: Any => Right(r)
          case _ => Left(new RuntimeException(s"error while sending msg to route: $r"))
        }
      case x => Future(Left(new RuntimeException(s"error while getting route: ${x.toString}")))
    }
  }
}


class AgencyAgentMsgHandler(val agentCommonParam: AgentActorCommonParam)
  extends AgentMsgHandler with ActorRefResolver{

  implicit def param: CommonParam = agentCommonParam.commonParam

  lazy val agencyAgentActorRef: ActorRef =
    agentActorRefReq(USER_AGENT_REGION_ACTOR_NAME, s"$SHARDED_ACTOR_PATH_PREFIX/$AGENCY_AGENT_REGION_ACTOR_NAME")

  def handleMsg(msg: Any): Future[Any] = {
    msg match {
      case tam: TransportAgnosticMsg => agencyAgentActorRef ? ForId(AGENCY_AGENT_ID, tam.payload)
      case _ => Future("not implemented")
    }
  }
}


