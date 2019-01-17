package com.evernym.extension.agency.protocol.agent

import com.evernym.agent.api._

import scala.concurrent.Future


class AgencyAgentWrapper(val commonParam: CommonParam) extends Agent {

  lazy val agentOrchestrator: AgentOrchestrator = new AgencyAgentOrchestrator(commonParam)

  override def handleMsg: PartialFunction[Any, Future[Any]] = {
    case x => agentOrchestrator.handleMsg(x)
  }

  override def start(inputParam: Option[Any]): Unit = {
    agentOrchestrator.start()
  }

  override def stop(): Unit = {
    agentOrchestrator.stop()
  }
}
