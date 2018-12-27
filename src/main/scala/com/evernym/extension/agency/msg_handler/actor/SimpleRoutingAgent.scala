package com.evernym.extension.agency.msg_handler.actor

import akka.actor.Props
import com.evernym.agent.api.ConfigProvider
import com.evernym.agent.common.actor.PersistentActorBase
import com.evernym.extension.agency.actor.RouteSet


//cmd
case class SetRoute(agentID: String, routeInfo: String)
case class GetRoute(agentID: String)

object SimpleRoutingAgent {
  def props(configProvider: ConfigProvider) = Props(new SimpleRoutingAgent(configProvider))
}

class SimpleRoutingAgent(configProvider: ConfigProvider) extends PersistentActorBase {

  var routes: Set[RouteSet] = Set.empty

  override def receiveRecover: Receive = {
    case rs: RouteSet =>
      routes += rs
  }

  override def receiveCommand: Receive = {
    case sr: SetRoute =>
      writeApplyAndSendItBack(RouteSet(sr.agentID, sr.routeInfo))

    case gr: GetRoute =>
      sender ! routes.find(_.agentID == gr.agentID)
  }
}
