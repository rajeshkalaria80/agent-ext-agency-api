package com.evernym.extension.agency.msg_handler.actor

import akka.Done
import akka.actor.Props
import com.evernym.agent.common.actor.{AgentActorCommonParam, OwnerAgentPairwiseDetail, PersistentActorBase}
import com.evernym.agent.common.util.Util.buildRouteJson
import com.evernym.agent.common.wallet.{CreateNewKeyParam, StoreTheirKeyParam}
import com.evernym.extension.agency.actor.{OwnerAgentPairwiseDetailSet, OwnerDIDSet, OwnerPairwiseDIDSet}
import com.evernym.extension.agency.common.Constants._

import scala.concurrent.ExecutionContext.Implicits.global


case class InitAgentForPairwiseKey(ownerDID: String, agentId: String, ownerPairwiseDID: String, ownerPairwiseDIDVerKey: String)

object AgencyAgentPairwise {
  def props(agentCommonParam: AgentActorCommonParam) = Props(new AgencyAgentPairwise(agentCommonParam))
}

class AgencyAgentPairwise(val agentActorCommonParam: AgentActorCommonParam)
  extends PersistentActorBase with AgencyAgentActorCommon {

  var ownerDIDOpt: Option[String] = None
  var ownerPairwiseDIDOpt: Option[String] = None
  var ownerAgentPairwiseDetail: Option[OwnerAgentPairwiseDetail] = None

  def agentVerKeyReq: String = ownerAgentPairwiseDetail.map(_.agentPairwiseVerKey).getOrElse(throwAgentNotInitializedYet())

  def ownerDIDReq: String = ownerDIDOpt.getOrElse(throwAgentNotInitializedYet())

  def ownerPairwiseDIDReq: String = ownerPairwiseDIDOpt.getOrElse(throwAgentNotInitializedYet())

  def getRouteJson: String = buildRouteJson(ACTOR_TYPE_AGENCY_AGENT_PAIRWISE_ACTOR)

  override val receiveRecover: Receive = {
    case ods: OwnerDIDSet => ownerDIDOpt = Option(ods.DID)
    case opds: OwnerPairwiseDIDSet => ownerPairwiseDIDOpt = Option(opds.DID)
    case oapds: OwnerAgentPairwiseDetailSet =>
      ownerAgentPairwiseDetail = Option(OwnerAgentPairwiseDetail(oapds.agentId, oapds.agentPairwiseVerKey))
      setWalletInfo(buildWalletAccessDetail(oapds.agentId))
  }

  def initAgentForPairwiseKey(ia: InitAgentForPairwiseKey): Unit = {
    val wad = buildWalletAccessDetail(ia.agentId)
    setWalletInfo(wad)

    agentActorCommonParam.walletAPI.storeTheirKey(StoreTheirKeyParam(ia.ownerPairwiseDID, ia.ownerPairwiseDIDVerKey))
    val agentPairwiseNewKeyResult = agentActorCommonParam.walletAPI.createNewKey(CreateNewKeyParam())

    writeAndApply(OwnerDIDSet(ia.ownerDID))
    writeAndApply(OwnerPairwiseDIDSet(ia.ownerPairwiseDID))
    val event = OwnerAgentPairwiseDetailSet(ia.agentId, agentPairwiseNewKeyResult.verKey)
    writeAndApply(event)

    val sndr = sender()
    val addRouteInfoSetFut = agentActorCommonParam.routingAgent.setRoute(entityId, getRouteJson)
    addRouteInfoSetFut.map {
      case Right(_: Any) => sndr ! event
      case Left(e: Throwable) => throw e
    }
  }


  override val receiveCommand: Receive = {
    case _: InitAgentForPairwiseKey if ownerDIDOpt.isDefined => sender ! Done

    case ia: InitAgentForPairwiseKey => initAgentForPairwiseKey(ia)

    case GetAgentDetail => sender ! ownerAgentPairwiseDetail

  }
}
