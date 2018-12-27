package com.evernym.extension.agency.common

import akka.actor.{ActorRef, ActorSystem}
import com.evernym.agent.api.{CommonParam, ConfigProvider}
import com.evernym.agent.common.util.Util.getActorRefFromSelection


trait ActorRefResolver extends GeneralTimeout {

  implicit def param: CommonParam
  implicit def config: ConfigProvider = param.config
  implicit def system: ActorSystem = param.actorSystem

  val ACTOR_PATH_PREFIX = "/system/sharding"

  val AGENCY_AGENT_ACTOR_NAME = "AgencyAgent"
  val AGENCY_AGENT_PAIRWISE_ACTOR_NAME = "AgencyAgentPairwise"

  val USER_AGENT_ACTOR_NAME = "UserAgent"
  val USER_AGENT_PAIRWISE_ACTOR_NAME = "UserAgentPairwise"

  lazy val agencyAgentRegion: ActorRef =
    getActorRefFromSelection(s"$ACTOR_PATH_PREFIX/$AGENCY_AGENT_ACTOR_NAME", system)

  lazy val agencyAgentPairwiseRegion: ActorRef =
    getActorRefFromSelection(s"$ACTOR_PATH_PREFIX/$AGENCY_AGENT_PAIRWISE_ACTOR_NAME", system)

  lazy val userAgentRegion: ActorRef =
    getActorRefFromSelection(s"$ACTOR_PATH_PREFIX/$USER_AGENT_ACTOR_NAME", system)

  lazy val userAgentPairwiseRegion: ActorRef =
    getActorRefFromSelection(s"$ACTOR_PATH_PREFIX/$USER_AGENT_PAIRWISE_ACTOR_NAME", system)

}
