package com.evernym.agency

import com.evernym.agent.api._
import com.evernym.agent.common.actor.ActorRefResolver
import com.evernym.extension.agency.protocol.business.AgencyBusinessProtocol
import com.evernym.extension.agency.protocol.transport.AgencyTransportProtocol

import scala.concurrent.Future


class AgencyAgentOrchestrator(val commonParam: CommonParam) extends AgentOrchestrator with ActorRefResolver {

  var protocols: Set[Component] = Set.empty
  var agencyBusinessProtocol: BusinessProtocol = _

  private def startAgencyAgentBusinessProtocol(): Unit = {
    agencyBusinessProtocol = new AgencyBusinessProtocol(commonParam)
    agencyBusinessProtocol.start()
    protocols += agencyBusinessProtocol
  }

  private def startAgencyAgentTransportProtocol(): Unit = {
    val transportProtocol = new AgencyTransportProtocol(commonParam, handleMsg)
    transportProtocol.start()
    protocols += transportProtocol
  }

  override def handleMsg: PartialFunction[Any, Future[Any]] = {
    case tam: TransportMsg => agencyBusinessProtocol.handleMsg(tam)
    case x => Future.failed(throw new NotImplementedError(s"messages not supported: $x"))
  }

  override def start(inputParam: Option[Any]=None): Unit = {
    startAgencyAgentBusinessProtocol()
    startAgencyAgentTransportProtocol()
  }

  override def stop(): Unit = {
    protocols.toSeq.reverse.foreach(_.stop())
  }
}
