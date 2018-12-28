package com.evernym.extension.agency.msg_handler.actor

import akka.Done
import akka.pattern.ask
import akka.actor.Props
import com.evernym.agent.common.CommonConstants._
import com.evernym.agent.common.a2a.{AnonCryptedMsg, AuthCryptedMsg}
import com.evernym.agent.common.actor._
import com.evernym.agent.common.util.Util.{buildRouteJson, getNewEntityId}
import com.evernym.agent.common.wallet.{CreateNewKeyParam, StoreTheirKeyParam}
import com.evernym.extension.agency.common.Constants._
import spray.json.RootJsonFormat

import scala.concurrent.ExecutionContext.Implicits.global

object AgencyAgent {
  def props(agentCommonParam: AgentActorCommonParam) = Props(new AgencyAgent(agentCommonParam))
}

class AgencyAgent(val agentActorCommonParam: AgentActorCommonParam)
  extends PersistentActorBase
    with AgencyAgentActorCommon
    with ActorRefResolver {

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


  def createPairwiseKey(decryptedMsg: Array[Byte]): Unit = {
    val cpkr = agentToAgentAPI.unpackMsg[CreateAgentPairwiseKeyReqMsg,
      RootJsonFormat[CreateAgentPairwiseKeyReqMsg]](decryptedMsg)(implParam[CreateAgentPairwiseKeyReqMsg])
    if (ownerAgentPairwiseDIDS.contains(cpkr.forDID)) {
      throw new RuntimeException("already added")
    } else {
      writeAndApply(OwnerPairwiseDIDSet(cpkr.forDID))
      val agentPairwiseId = getNewEntityId
      val msg = InitAgentForPairwiseKey(ownerDIDReq, entityId, cpkr.forDID, cpkr.forDIDVerKey)
      val iaFut = agencyAgentPairwiseActorRef ? ForId(agentPairwiseId, msg)
      val sndr = sender()
      iaFut map {
        case oapds: OwnerAgentPairwiseDetailSet =>
          val acm = buildPairwiseKeyCreatedRespMsg(agentPairwiseId, oapds.agentPairwiseVerKey)
          val respMsg = agentToAgentAPI.packAndAuthCrypt(buildPackAndAuthCryptParam(acm))(implParam[PairwiseKeyCreatedRespMsg])
          sndr ! AuthCryptedMsg(respMsg)
      }
    }
  }

  def handleGetOwnerAgentDetail(): Unit = {
    val acm = buildOwnerAgentDetailRespMsg(ownerDIDReq, entityId)
    val respMsg = agentToAgentAPI.packAndAuthCrypt(buildPackAndAuthCryptParam(acm))(implParam[OwnerAgentDetailRespMsg])
    sender ! AuthCryptedMsg(respMsg)
  }

  def handleFwdMsg(decryptedMsg: Array[Byte]): Unit = {
    val fwdMsg = agentToAgentAPI.unpackMsg[FwdMsg, RootJsonFormat[FwdMsg]](decryptedMsg)(implParam[FwdMsg])

    if (fwdMsg.fwd == entityId) {
      handleAuthCryptedMsg(AuthCryptedMsg(fwdMsg.msg))
    } else {
      agentActorCommonParam.routingAgent.fwdMsgToAgent(fwdMsg.fwd, AuthCryptedMsg(fwdMsg.msg), sender)
    }
  }

  def handleAuthCryptedMsg(acm: AuthCryptedMsg): Unit = {
    val (typedMsg, decryptedMsg) = agentToAgentAPI.authDecryptAndUnpack[AgentTypedMsg,
      RootJsonFormat[AgentTypedMsg]](buildAuthDecryptParam(acm.payload))(implParam[AgentTypedMsg])

    typedMsg.`@type` match {

      case TypeDetail(MSG_TYPE_FWD, VERSION_1_0, _) => handleFwdMsg(decryptedMsg)

      case TypeDetail(MSG_TYPE_CREATE_PAIRWISE_KEY, VERSION_1_0, _) => createPairwiseKey(decryptedMsg)

      case TypeDetail(MSG_TYPE_GET_OWNER_AGENT_DETAIL, VERSION_1_0, _) => handleGetOwnerAgentDetail()

      case _ => throw new RuntimeException(s"msg $typedMsg not supported")
    }
  }

  def handleAnonCryptedMsg(acm: AnonCryptedMsg): Unit = {
    val (typedMsg, unsealedMsg) = agentToAgentAPI.anonDecryptAndUnpack[AgentTypedMsg,
      RootJsonFormat[AgentTypedMsg]](buildAnonDecryptParam(acm.payload))(implParam[AgentTypedMsg])

    typedMsg.`@type` match {

      case TypeDetail(MSG_TYPE_FWD, VERSION_1_0, _) => handleFwdMsg(unsealedMsg)

      case _ => throw new RuntimeException(s"msg $typedMsg not supported")
    }
  }

  override val receiveCommand: Receive = {

    case _: InitAgent if ownerDIDOpt.isDefined => sender ! Done

    case ia: InitAgent => initAgent(ia)

    case acm: AnonCryptedMsg => handleAnonCryptedMsg(acm)

  }
}
