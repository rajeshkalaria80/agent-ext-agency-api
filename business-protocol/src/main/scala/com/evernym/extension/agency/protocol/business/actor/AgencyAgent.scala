package com.evernym.extension.agency.protocol.business.actor

import akka.Done
import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import com.evernym.agent.common.CommonConstants._
import com.evernym.agent.common.a2a._
import com.evernym.agent.common.actor._
import com.evernym.agent.common.util.Util.{buildRouteJson, getNewEntityId}
import com.evernym.agent.common.wallet.{CreateNewKeyParam, StoreTheirKeyParam}
import com.evernym.extension.agency.actor.{UserAgencyPairwiseAgentIdSet, UserConnected}
import com.evernym.extension.agency.common.Constants._
import spray.json.RootJsonFormat

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Left

case object GetAgencyAgentDetail

object AgencyAgent {
  def props(agentCommonParam: AgentActorCommonParam) = Props(new AgencyAgent(agentCommonParam))
}

class AgencyAgent(val agentActorCommonParam: AgentActorCommonParam)
  extends PersistentActorBase
    with AgencyAgentActorCommon
    with ActorRefResolver {

  var ownerDIDOpt: Option[String] = None
  var agentVerKeyOpt: Option[String] = None

  var connectedUsers: Map[String, Option[String]] = Map.empty


  def ownerDIDReq: String = ownerDIDOpt.getOrElse(throwAgentNotInitializedYet())
  def agentVerKeyReq: String = agentVerKeyOpt.getOrElse(throwAgentNotInitializedYet())

  def getRouteJson: String = buildRouteJson(ACTOR_TYPE_AGENCY_AGENT_ACTOR)

  override val receiveRecover: Receive = {
    case ods: OwnerDIDSet => ownerDIDOpt = Option(ods.DID)
    case oads: OwnerAgentDetailSet => agentVerKeyOpt = Option(oads.verKey)
    case uc: UserConnected =>  connectedUsers += uc.DID -> None
    case uapais: UserAgencyPairwiseAgentIdSet => connectedUsers += uapais.DID -> Option(uapais.agencyPairwiseAgentId)
  }

  def initAgent(ia: InitAgent): Unit = {

    val wad = buildWalletAccessDetail(entityId)
    setWalletInfo(wad)
    agentActorCommonParam.walletAPI.createAndOpenWallet(wad)

    val agentKeyResult = agentActorCommonParam.walletAPI.createNewKey(CreateNewKeyParam())
    writeAndApply(OwnerDIDSet(ia.ownerDID))

    agentActorCommonParam.walletAPI.storeTheirKey(StoreTheirKeyParam(ia.ownerDID, ia.ownerDIDVerKey))
    writeAndApply(OwnerAgentDetailSet(agentKeyResult.verKey))

    val sndr = sender()
    val addRouteInfoSetFut = agentActorCommonParam.routingAgent.setRoute(entityId, getRouteJson)
    addRouteInfoSetFut.map {
      case Right(_: Any) => sendAgencyAgentDetail(sndr)
      case Left(e: Throwable) => throw e
    }
  }

  def handleGetOwnerAgentDetail(): Unit = {
    val acm = buildOwnerAgentDetailRespMsg(ownerDIDReq, entityId)
    val respMsg = agentToAgentAPI.packAndAuthCrypt(buildPackAndAuthCryptParam(acm))(implParam[OwnerAgentDetailRespMsg])
    sender ! AuthCryptedMsg(respMsg)
  }

  def handleFwdMsg(decryptedMsg: Array[Byte]): Unit = {
    val fwdMsg = agentToAgentAPI.unpackMsg[FwdMsg, RootJsonFormat[FwdMsg]](decryptedMsg)(implParam[FwdMsg])
    println(s"## fwdMsg (${fwdMsg.fwd}): " + fwdMsg)
    if (fwdMsg.fwd == entityId) {
      handleAuthCryptedMsg(AuthCryptedMsg(fwdMsg.msg))
    } else {
      agentActorCommonParam.routingAgent.fwdMsgToAgent(fwdMsg.fwd, AuthCryptedMsg(fwdMsg.msg), sender)
    }
  }

  def handleConnectMsg(decryptedMsg: Array[Byte]): Unit = {
    val cm = agentToAgentAPI.unpackMsg[ConnectReqMsg,
      RootJsonFormat[ConnectReqMsg]](decryptedMsg)(implParam[ConnectReqMsg])
    writeAndApply(UserConnected(cm.fromDID))
    val agentPairwiseId = getNewEntityId
    val msg = InitAgentForPairwiseKey(ownerDIDReq, entityId, cm.fromDID, cm.fromDIDVerKey)
    val iaFut = agencyAgentPairwiseActorRef ? ForId(agentPairwiseId, msg)
    val sndr = sender()
    iaFut map {
      case oapds: OwnerAgentPairwiseDetailSet =>
        val crm = buildConnectedRespMsg(agentPairwiseId, oapds.agentPairwiseVerKey)
        val encryptParam = EncryptParam(
          KeyInfo(Left(agentVerKeyReq)),
          KeyInfo(Right(GetVerKeyByDIDParam(cm.fromDID, getKeyFromPool = false))))
        val respMsg = agentToAgentAPI.packAndAuthCrypt(buildPackAndAuthCryptParam(crm, encryptParam))(implParam[ConnectedRespMsg])
        self ! UserAgencyPairwiseAgentIdSet(cm.fromDID, agentPairwiseId)
        sndr ! AuthCryptedMsg(respMsg)
    }
  }

  def handleAuthCryptedMsg(acm: AuthCryptedMsg): Unit = {
    val (typedMsg, decryptedMsg) = agentToAgentAPI.authDecryptAndUnpack[AgentTypedMsg,
      RootJsonFormat[AgentTypedMsg]](buildAuthDecryptParam(acm.payload))(implParam[AgentTypedMsg])

    typedMsg.`@type` match {

      case TypeDetail(MSG_TYPE_FWD, VERSION_1_0, _) => handleFwdMsg(decryptedMsg)

      case TypeDetail(MSG_TYPE_CONNECT, VERSION_1_0, _) => handleConnectMsg(decryptedMsg)

      case TypeDetail(MSG_TYPE_GET_OWNER_AGENT_DETAIL, VERSION_1_0, _) => handleGetOwnerAgentDetail()

      case _ => throw new RuntimeException(s"msg $typedMsg not supported")
    }
  }

  def handleAnonCryptedMsg(acm: AnonCryptedMsg): Unit = {
    val (typedMsg, unsealedMsg) = agentToAgentAPI.anonDecryptAndUnpack[AgentTypedMsg,
      RootJsonFormat[AgentTypedMsg]](buildAnonDecryptParam(acm.payload))(implParam[AgentTypedMsg])

    println("## typedMsg: " + typedMsg)
    typedMsg.`@type` match {

      case TypeDetail(MSG_TYPE_FWD, VERSION_1_0, _) => handleFwdMsg(unsealedMsg)

      case _ => throw new RuntimeException(s"msg $typedMsg not supported")
    }
  }

  def sendAgencyAgentDetail(sndr: ActorRef): Unit = {
    sndr ! AgentDetail(entityId, agentVerKeyReq)
  }

  override val receiveCommand: Receive = {

    case _: InitAgent if ownerDIDOpt.isDefined => sender ! Done

    case ia: InitAgent => initAgent(ia)

    case GetAgencyAgentDetail if ownerDIDOpt.isEmpty => throwAgentNotInitializedYet()

    case GetAgencyAgentDetail => sendAgencyAgentDetail(sender)

    case acm: AnonCryptedMsg =>
      println("### acm: " + acm)
      handleAnonCryptedMsg(acm)

    case uapais: UserAgencyPairwiseAgentIdSet => writeAndApply(uapais)

  }
}
