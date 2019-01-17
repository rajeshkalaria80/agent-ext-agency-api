package com.evernym.extension.agency.protocol.agent

import com.evernym.agent.api._
import com.evernym.agent.common.actor.ActorRefResolver
import com.evernym.extension.agency.protocol.business.AgencyBusinessProtocol

import scala.concurrent.Future


class AgencyAgentOrchestrator(val commonParam: CommonParam) extends AgentOrchestrator with ActorRefResolver {

  var protocols: Set[Component] = Set.empty
  var agencyBusinessProtocol: BusinessProtocol = _

  private def startAgencyAgentBusinessProtocol(): Unit = {
    agencyBusinessProtocol = new AgencyBusinessProtocol(commonParam)
    agencyBusinessProtocol.start()
    protocols += agencyBusinessProtocol
  }

  override def handleMsg: PartialFunction[Any, Future[Any]] = {
    case tam: TransportMsg => agencyBusinessProtocol.handleMsg(tam)
    case x => Future.failed(throw new NotImplementedError(s"messages not supported: $x"))
  }

  override def start(inputParam: Option[Any]=None): Unit = {
    startAgencyAgentBusinessProtocol()
  }

  override def stop(): Unit = {
    protocols.toSeq.reverse.foreach(_.stop())
  }
}
