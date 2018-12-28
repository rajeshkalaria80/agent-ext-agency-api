package com.evernym.extension.agency.client

import com.evernym.agent.common.a2a.{EncryptParam, GetVerKeyByDIDParam, ImplicitParam, KeyInfo}
import com.evernym.agent.common.actor.AgentDetail
import com.evernym.agent.common.CommonConstants._
import com.evernym.agent.common.wallet.CreateNewKeyParam
import spray.json.RootJsonFormat


case class TestAgentCreatedRespMsg(`@type`: TestTypeDetail, agentId: String, agentVerKey: String)

case class TestCreatePairwiseKeyReqMsg(`@type`: TestTypeDetail, fromDID: String, fromDIDVerKey: String)

case class TestPairwiseKeyCreatedRespMsg(`@type`: TestTypeDetail, agentPairwiseId: String, agentPairwiseVerKey: String)

case class TestGetOwnerAgentDetailReqMsg(`@type`: TestTypeDetail)

case class TestOwnerAgentDetailRespMsg(`@type`: TestTypeDetail, ownerDID: String, agentId: String)


case class TestAgentPairwiseKeyDetail(myPairwiseVerKey: String, agentPairwiseId: String, agentPairwiseVerKey: String)


class TestAgencyClient extends TestClientBase {

  override val agentMsgPath: String = "/agency/msg"

  implicit val testAgentCreatedRespMsg: RootJsonFormat[TestAgentCreatedRespMsg] = jsonFormat3(TestAgentCreatedRespMsg.apply)

  implicit val testCreatePairwiseKeyReqMsg: RootJsonFormat[TestCreatePairwiseKeyReqMsg] = jsonFormat3(TestCreatePairwiseKeyReqMsg.apply)
  implicit val testPairwiseKeyCreatedRespMsg: RootJsonFormat[TestPairwiseKeyCreatedRespMsg] = jsonFormat3(TestPairwiseKeyCreatedRespMsg.apply)

  implicit val testGetOwnerAgentDetailReqMsg: RootJsonFormat[TestGetOwnerAgentDetailReqMsg] = jsonFormat1(TestGetOwnerAgentDetailReqMsg.apply)
  implicit val testOwnerAgentDetailRespMsg: RootJsonFormat[TestOwnerAgentDetailRespMsg] = jsonFormat3(TestOwnerAgentDetailRespMsg.apply)


  var myAgentDetail: AgentDetail = _
  var pairwiseDIDDetails: Map[String, TestAgentPairwiseKeyDetail] = Map.empty

  def createNewPairwiseKey(): DIDDetail = {
    val newKey = walletAPI.createNewKey(CreateNewKeyParam())(walletInfo)
    val dd = DIDDetail(newKey.DID, newKey.verKey)
    pairwiseDIDDetails += dd.DID -> TestAgentPairwiseKeyDetail(dd.verKey, null, null)
    dd
  }

  def myAgentVerKey: String = myAgentDetail.verKey

  def encryptParamForAgent: EncryptParam = EncryptParam (
    KeyInfo(Right(GetVerKeyByDIDParam(myDID, getKeyFromPool = false))),
    KeyInfo(Left(myAgentVerKey))
  )

  def setAgentDetail(id: String, verKey: String): Unit = {
    myAgentDetail = AgentDetail(id, verKey)
  }

  def setPairwiseAgentDetail(forMyDID: String, agentPairwiseId: String, agentPairwiseVerKey: String): Unit = {
    pairwiseDIDDetails.get(forMyDID).foreach { r =>
      pairwiseDIDDetails += forMyDID -> TestAgentPairwiseKeyDetail(r.myPairwiseVerKey, agentPairwiseId, agentPairwiseVerKey)
    }
  }

  def handleAgentCreatedRespMsg(rm: Array[Byte]): TestAgentCreatedRespMsg = {
    val ac = authDecryptAndUnpackRespMsg[TestAgentCreatedRespMsg](rm)
    setAgentDetail(ac.agentId, ac.agentVerKey)
    ac
  }

  def buildAuthCryptedFwdMsg(fwdTo: String, origMsg: Array[Byte]): Array[Byte] = {
    val finalPackedMsg = if (fwdTo == myAgentDetail.id) origMsg else {
      defaultA2AAPI.authCrypt(buildAuthCryptParam(pairwiseDIDDetails.find(_._2.agentPairwiseId==fwdTo).get._2.agentPairwiseVerKey, origMsg))
    }
    val fwdReqMsg = TestFwdReqMsg(TestTypeDetail(MSG_TYPE_FWD, version), fwdTo, finalPackedMsg)
    val fwdPackedMsg = defaultA2AAPI.packMsg(fwdReqMsg)(ImplicitParam[RootJsonFormat[TestFwdReqMsg]](implicitly))
    defaultA2AAPI.authCrypt(buildAuthCryptParam(myAgentDetail.verKey, fwdPackedMsg))
  }

  def buildCreatePairwiseKeyReq(): (DIDDetail, Array[Byte]) = {
    val DIDDetail = createNewPairwiseKey()
    val nativeMsg = TestCreatePairwiseKeyReqMsg(
      TestTypeDetail(MSG_TYPE_CREATE_PAIRWISE_KEY, version), DIDDetail.DID, DIDDetail.verKey)
    val packedMsg = defaultA2AAPI.packMsg(nativeMsg)(ImplicitParam[RootJsonFormat[TestCreatePairwiseKeyReqMsg]](implicitly))
    val req = buildAuthCryptedFwdMsg(myAgentDetail.id, packedMsg)
    (DIDDetail, req)
  }

  def handlePairwiseKeyCreatedRespMsg(forMyDID: String, rm: Array[Byte]): TestPairwiseKeyCreatedRespMsg = {
    val msg = authDecryptAndUnpackRespMsg[TestPairwiseKeyCreatedRespMsg](rm)
    println("### pairwise key created: " + msg)
    setPairwiseAgentDetail(forMyDID, msg.agentPairwiseId, msg.agentPairwiseVerKey)
    msg
  }

  def buildGetOwnerAgentDetailReq(forAgentId: String): Array[Byte] = {
    val nativeMsg = TestGetOwnerAgentDetailReqMsg(TestTypeDetail(MSG_TYPE_GET_OWNER_AGENT_DETAIL, version))
    val packedMsg = defaultA2AAPI.packMsg(nativeMsg)(ImplicitParam[RootJsonFormat[TestGetOwnerAgentDetailReqMsg]](implicitly))
    val req = buildAuthCryptedFwdMsg(forAgentId, packedMsg)
    req
  }

  def handleOwnerAgentDetailRespMsg(rm: Array[Byte]): TestOwnerAgentDetailRespMsg = {
    val msg = authDecryptAndUnpackRespMsg[TestOwnerAgentDetailRespMsg](rm)
    println("### owner detail: " + msg)
    msg
  }

}
