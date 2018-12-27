package com.evernym.extension.agency.msg_handler.actor

import akka.persistence.PersistentActor
import com.evernym.agent.common.actor.AgentActorCommon


case class RouteDetail(persistenceId: String, actorTypeId: Int)


trait AgencyAgentActorCommon extends AgentActorCommon { this: PersistentActor =>

}
