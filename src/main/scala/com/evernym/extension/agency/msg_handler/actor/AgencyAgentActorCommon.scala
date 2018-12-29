package com.evernym.extension.agency.msg_handler.actor

import akka.actor.ActorRef
import akka.persistence.PersistentActor
import com.evernym.agent.common.a2a.{AnonCryptUnapplyParam, KeyInfo}
import com.evernym.agent.common.actor.{ActorRefResolver, AgentActorCommon}
import com.evernym.extension.agency.common.AgencyJsonTransformationUtil
import com.evernym.extension.agency.common.Constants._


case class ForId(id: String, msg: Any)


trait AgencyAgentActorCommon extends AgentActorCommon
  with AgencyJsonTransformationUtil with ActorRefResolver { this: PersistentActor =>

  lazy val agencyAgentActorRef: ActorRef =
    agentActorRefReq(AGENCY_AGENT_REGION_ACTOR_NAME,
      s"$SHARDED_ACTOR_PATH_PREFIX/$AGENCY_AGENT_REGION_ACTOR_NAME")

  lazy val agencyAgentPairwiseActorRef: ActorRef =
    agentActorRefReq(AGENCY_AGENT_PAIRWISE_REGION_ACTOR_NAME,
      s"$SHARDED_ACTOR_PATH_PREFIX/$AGENCY_AGENT_PAIRWISE_REGION_ACTOR_NAME")

  lazy val userAgentActorRef: ActorRef =
    agentActorRefReq(USER_AGENT_REGION_ACTOR_NAME,
      s"$SHARDED_ACTOR_PATH_PREFIX/$USER_AGENT_REGION_ACTOR_NAME")

  lazy val userAgentPairwiseActorRef: ActorRef =
    agentActorRefReq(USER_AGENT_PAIRWISE_REGION_ACTOR_NAME,
      s"$SHARDED_ACTOR_PATH_PREFIX/$USER_AGENT_PAIRWISE_REGION_ACTOR_NAME")

  def buildAnonDecryptParam(data: Array[Byte]): AnonCryptUnapplyParam =
    AnonCryptUnapplyParam(KeyInfo(Left(agentVerKeyReq)), data, walletInfo)

}
