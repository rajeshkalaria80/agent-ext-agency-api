package com.evernym.extension.agency.platform.business

import akka.actor.ActorRef
import com.evernym.agent.api.CommonParam
import com.evernym.agent.common.actor._
import com.evernym.agent.common.router.RoutingAgentBase
import com.evernym.extension.agency.common.Constants._
import com.evernym.extension.agency.platform.business.actor.ForId


class DefaultRoutingAgent(val commonParam: CommonParam)
  extends RoutingAgentBase
    with ActorRefResolver {

  val ACTOR_TYPE_USER_AGENT_REGION_ACTOR = 1
  val ACTOR_TYPE_USER_AGENT_PAIRWISE_REGION_ACTOR = 2

  val ACTOR_TYPE_AGENCY_AGENT_REGION_ACTOR = 3
  val ACTOR_TYPE_AGENCY_AGENT_PAIRWISE_REGION_ACTOR = 4

  override def buildTargetActorRef(forId: String, routeDetail: RouteDetail): ActorRef = {
    routeDetail.actorTypeId match {
      case ACTOR_TYPE_USER_AGENT_REGION_ACTOR =>
        agentActorRefReq(USER_AGENT_REGION_ACTOR_NAME, s"$SHARDED_ACTOR_PATH_PREFIX/$USER_AGENT_REGION_ACTOR_NAME")
      case ACTOR_TYPE_USER_AGENT_PAIRWISE_REGION_ACTOR =>
        agentActorRefReq(USER_AGENT_PAIRWISE_REGION_ACTOR_NAME, s"$SHARDED_ACTOR_PATH_PREFIX/$USER_AGENT_PAIRWISE_REGION_ACTOR_NAME")

      case ACTOR_TYPE_AGENCY_AGENT_REGION_ACTOR =>
        agentActorRefReq(AGENCY_AGENT_REGION_ACTOR_NAME, s"$SHARDED_ACTOR_PATH_PREFIX/$AGENCY_AGENT_REGION_ACTOR_NAME")
      case ACTOR_TYPE_AGENCY_AGENT_PAIRWISE_REGION_ACTOR =>
        agentActorRefReq(AGENCY_AGENT_PAIRWISE_REGION_ACTOR_NAME, s"$SHARDED_ACTOR_PATH_PREFIX/$AGENCY_AGENT_PAIRWISE_REGION_ACTOR_NAME")

      case x => throw new NotImplementedError(s"path building not supported for actor type: $x")
    }
  }

  override def buildTargetMsg(forId: String, msg: Any): Any = {
    ForId(forId, msg)
  }
}


