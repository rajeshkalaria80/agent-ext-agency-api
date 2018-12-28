package com.evernym.extension.agency.msg_handler.actor

import akka.Done
import akka.actor.Props
import com.evernym.agent.common.a2a.AuthCryptedMsg
import com.evernym.agent.common.actor._
import com.evernym.agent.common.util.Util.buildRouteJson
import com.evernym.agent.common.wallet.{CreateNewKeyParam, StoreTheirKeyParam}
import com.evernym.extension.agency.actor.{OwnerAgentDetailSet, OwnerDIDSet, OwnerPairwiseDIDSet}
import com.evernym.extension.agency.common.Constants._

import scala.concurrent.ExecutionContext.Implicits.global

object AgencyAgent {
  def props(agentCommonParam: AgentActorCommonParam) = Props(new AgencyAgent(agentCommonParam))
}

class AgencyAgent(val agentActorCommonParam: AgentActorCommonParam)
  extends PersistentActorBase
    with AgencyAgentActorCommon with JsonTransformationUtil {

  var ownerDIDOpt: Option[String] = None
  var agentVerKeyOpt: Option[String] = None
  var ownerAgentPairwiseDIDS: Set[String] = Set.empty

  def ownerDIDReq: String = ownerDIDOpt.getOrElse(throwAgentNotInitializedYet())

  def agentVerKeyReq: String = agentVerKeyOpt.getOrElse(throwAgentNotInitializedYet())

  def getRouteJson: String = buildRouteJson(ACTOR_TYPE_AGENCY_AGENT_ACTOR)

  override val receiveRecover: Receive = {
    case ods: OwnerDIDSet => ownerDIDOpt = Option(ods.DID)
    case oads: OwnerAgentDetailSet => agentVerKeyOpt = Option(oads.verKey)
    case opds: OwnerPairwiseDIDSet =>  ownerAgentPairwiseDIDS += opds.DID
  }

  def initAgent(ia: InitAgent): Unit = {

    val wad = buildWalletAccessDetail(entityId)
    setWalletInfo(wad)
    agentActorCommonParam.walletAPI.createAndOpenWallet(wad)
    agentActorCommonParam.walletAPI.storeTheirKey(StoreTheirKeyParam(ia.ownerDID, ia.ownerDIDVerKey))
    val agentKeyResult = agentActorCommonParam.walletAPI.createNewKey(CreateNewKeyParam())

    writeAndApply(OwnerDIDSet(ia.ownerDID))
    writeAndApply(OwnerAgentDetailSet(agentKeyResult.verKey))

    val sndr = sender()
    val addRouteInfoSetFut = agentActorCommonParam.routingAgent.setRoute(entityId, getRouteJson)
    addRouteInfoSetFut.map {
      case Right(_: Any) =>
        val acm = buildAgentCreatedRespMsg(entityId, agentKeyResult.verKey)
        val respMsg = agentToAgentAPI.packAndAuthCrypt(buildPackAndAuthCryptParam(acm))(implParam[AgentCreatedRespMsg])
        sndr ! AuthCryptedMsg(respMsg)
      case Left(e: Throwable) =>
        throw e
    }
  }

  override val receiveCommand: Receive = {

    case _: InitAgent if ownerDIDOpt.isDefined => sender ! Done

    case ia: InitAgent => initAgent(ia)

    case GetAgentDetail => sender ! agentVerKeyOpt

  }
}
