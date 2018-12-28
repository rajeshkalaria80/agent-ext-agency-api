package com.evernym.extension.agency.common


object Constants {

  val AGENCY_ACTOR_SYSTEM_NAME = "agency"

  val AGENCY_AGENT_ID = "139d6e07-8515-4773-b428-8090b94cd480"

  val ACTOR_TYPE_AGENCY_AGENT_ACTOR = 3
  val ACTOR_TYPE_AGENCY_AGENT_PAIRWISE_ACTOR = 4

  val SHARDED_ACTOR_PATH_PREFIX = "/system/sharding"
  val AGENCY_AGENT_REGION_ACTOR_NAME = "AgencyAgent"
  val AGENCY_AGENT_PAIRWISE_REGION_ACTOR_NAME = "AgencyAgentPairwise"
  val USER_AGENT_REGION_ACTOR_NAME = "UserAgent"
  val USER_AGENT_PAIRWISE_REGION_ACTOR_NAME = "UserAgentPairwise"

  private val ACTOR_DISPATCHER_NAME = s"akka.actor.dispatchers"
  val ACTOR_DISPATCHER_NAME_AGENT_AGENT = s"$ACTOR_DISPATCHER_NAME.agency-agent-dispatcher"
  val ACTOR_DISPATCHER_NAME_AGENT_AGENT_PAIRWISE = s"$ACTOR_DISPATCHER_NAME.agency-agent-pairwise-dispatcher"
  val ACTOR_DISPATCHER_NAME_USER_AGENT = s"$ACTOR_DISPATCHER_NAME.user-agent-dispatcher"
  val ACTOR_DISPATCHER_NAME_USER_AGENT_PAIRWISE = s"$ACTOR_DISPATCHER_NAME.user-agent-pairwise-dispatcher"


  val MSG_TYPE_CREATE_AGENT = "CREATE_AGENT"

}
