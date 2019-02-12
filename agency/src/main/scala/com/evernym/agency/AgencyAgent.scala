package com.evernym.agency

import com.evernym.agent.api.{Agent, AgentOrchestrator, CommonParam}


import scala.concurrent.Future


class AgencyAgentApp(val commonParam: CommonParam) extends Agent {

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


