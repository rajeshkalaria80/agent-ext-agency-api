package com.evernym.extension.agency.router

import com.evernym.agent.api.{AgentMsgHandler, ConfigProvider, TransportAgnosticMsg, TransportMsgRouter}

import scala.concurrent.Future

class DefaultMsgRouter(config: ConfigProvider, val agentMsgHandler: AgentMsgHandler) extends TransportMsgRouter {

  def handleMsg(msg: TransportAgnosticMsg): Future[Any] = {
    agentMsgHandler.handleMsg(msg)
  }
}
