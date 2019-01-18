package com.evernym.extension.agency.agent.client

import akka.actor.ActorSystem
import com.evernym.agent.common.actor.{AgentDetail, InitAgent}
import com.evernym.agent.common.transport.{Endpoint, HttpRemoteMsgSendingSvc}
import com.evernym.extension.agency.agent.akka.AkkaTestBasic
import com.evernym.extension.agency.common.AgencyJsonTransformationUtil
import com.typesafe.config.Config


object TestAgencyAgentApiClient extends TestAgencyClient with HttpRemoteMsgSendingSvc
  with AgencyJsonTransformationUtil {
  import spray.json._

  override val config: Config = AkkaTestBasic.getConfig
  override val actorSystem: ActorSystem = AkkaTestBasic.system
  implicit val endpoint: Endpoint = Endpoint("localhost", 7000, "agency/msg")

  def sendCreateAgencyAgent(): Unit = {
    val req = InitAgent(agencyOwnerDIDDetail.DID, agencyOwnerDIDDetail.verKey)
    val respMsg = sendSyncMsgExpectingStringResponse(req, "agency/init")
    handleAgencyDetailRespMsg(respMsg.parseJson.convertTo[AgentDetail])
  }

  def sendConnect(): Unit = {
    val req = buildConnectReq()
    val resp = sendSyncMsgExpectingBinaryResponse(req)
    handleConnectedRespMsg(resp)
  }

  def sendGetAgencyAgentOwnerDetail(): Unit = {
    val req = buildGetOwnerAgentDetailReq(myAgencyAgentPairwiseDetail.agentPairwiseId)
    val resp = sendSyncMsgExpectingBinaryResponse(req)
    handleOwnerAgentDetailRespMsg(resp)
  }

  def sendCreateUserAgent(): Unit = {
    val req = buildCreateUserAgentReq()
    val resp = sendSyncMsgExpectingBinaryResponse(req)
    handleAgentCratedRespMsg(resp)
  }

  def sendCreateUserPairwiseKey(): Unit = {
    val (didDetail, req) = buildCreatePairwiseKeyReq()
    val resp = sendSyncMsgExpectingBinaryResponse(req)
    handlePairwiseKeyCreatedRespMsg(didDetail.DID, resp)
  }

}
