package com.evernym.extension.agency.msg_handler.actor

import akka.persistence.PersistentActor
import com.evernym.agent.common.actor.AgentActorCommon


case class ForId(id: String, msg: Any)

trait AgencyAgentActorCommon extends AgentActorCommon { this: PersistentActor =>

}
