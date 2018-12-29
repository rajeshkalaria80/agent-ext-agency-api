package com.evernym.extension.agency.msg_handler.actor

import akka.Done
import akka.actor.Props
import akka.pattern.ask
import com.evernym.agent.common.CommonConstants.{MSG_TYPE_GET_OWNER_AGENT_DETAIL, VERSION_1_0}
import com.evernym.agent.common.a2a.{AuthCryptedMsg, EncryptParam, GetVerKeyByDIDParam, KeyInfo}
import com.evernym.agent.common.actor._
import com.evernym.agent.common.util.Util.{buildRouteJson, getNewEntityId}
import com.evernym.agent.common.wallet.{CreateNewKeyParam, StoreTheirKeyParam}
import com.evernym.extension.agency.actor.UserAgentCreated
import com.evernym.extension.agency.common.Constants._
import spray.json.RootJsonFormat

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Left


case class InitAgentForPairwiseKey(ownerDID: String, agentId: String, ownerPairwiseDID: String, ownerPairwiseDIDVerKey: String)

object AgencyAgentPairwise {
  def props(agentCommonParam: AgentActorCommonParam) = Props(new AgencyAgentPairwise(agentCommonParam))
}

class AgencyAgentPairwise(val agentActorCommonParam: AgentActorCommonParam)
  extends PersistentActorBase with AgencyAgentActorCommon {

  var ownerDIDOpt: Option[String] = None
  var ownerPairwiseDIDOpt: Option[String] = None
  var ownerAgentPairwiseDetail: Option[OwnerAgentPairwiseDetail] = None

  var userAgentId: Option[String] = None

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
    case uac: UserAgentCreated => userAgentId = Option(uac.agentId)
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

  override def getEncryptParam = EncryptParam(
    KeyInfo(Left(agentVerKeyReq)),
    KeyInfo(Right(GetVerKeyByDIDParam(ownerPairwiseDIDReq, getKeyFromPool = false))))

  def handleGetOwnerAgentDetail(): Unit = {
    val acm = buildOwnerAgentDetailRespMsg(ownerDIDReq, entityId)
    val respMsg = agentToAgentAPI.packAndAuthCrypt(buildPackAndAuthCryptParam(acm))(implParam[OwnerAgentDetailRespMsg])
    sender ! AuthCryptedMsg(respMsg)
  }

  def handleCreateAgent(decryptedMsg: Array[Byte]): Unit = {
    val cam = agentToAgentAPI.unpackMsg[CreateAgentReqMsg,
      RootJsonFormat[CreateAgentReqMsg]](decryptedMsg)(implParam[CreateAgentReqMsg])
    val agentId = getNewEntityId
    writeAndApply(UserAgentCreated(agentId))  //assumption is that the agentId is not used earlier (TODO: need to handle it better)
    val msg = InitAgent(cam.forDID, cam.forDIDVerKey)
    val iaFut = userAgentActorRef ? ForId(agentId, msg)
    val sndr = sender()
    iaFut.map {
      case uac: AuthCryptedMsg => sndr ! uac
    }
  }

  def handleAuthCryptedMsg(acm: AuthCryptedMsg): Unit = {
    val (typedMsg, decryptedMsg) = agentToAgentAPI.authDecryptAndUnpack[AgentTypedMsg,
      RootJsonFormat[AgentTypedMsg]](buildAuthDecryptParam(acm.payload))(implParam[AgentTypedMsg])

    typedMsg.`@type` match {

      case TypeDetail(MSG_TYPE_GET_OWNER_AGENT_DETAIL, VERSION_1_0, _) => handleGetOwnerAgentDetail()
      case TypeDetail(MSG_TYPE_CREATE_AGENT, VERSION_1_0, _) => handleCreateAgent(decryptedMsg)

      case _ => throw new RuntimeException(s"msg $typedMsg not supported")
    }
  }

  override val receiveCommand: Receive = {
    case _: InitAgentForPairwiseKey if ownerDIDOpt.isDefined => sender ! Done

    case ia: InitAgentForPairwiseKey => initAgentForPairwiseKey(ia)

    case any: Any if ownerDIDOpt.isEmpty => throw new RuntimeException("agent not initialized yet")

    case acm: AuthCryptedMsg => handleAuthCryptedMsg(acm)

  }
}
