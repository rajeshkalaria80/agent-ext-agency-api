package com.evernym.extension.agency.client

import com.evernym.agent.common.a2a.{EncryptParam, GetVerKeyByDIDParam, ImplicitParam, KeyInfo}
import com.evernym.agent.common.actor.AgentDetail
import com.evernym.agent.common.CommonConstants._
import com.evernym.agent.common.wallet.CreateNewKeyParam
import com.evernym.extension.agency.common.Constants._
import spray.json.RootJsonFormat


case class TestCreatePairwiseKeyReqMsg(`@type`: TestTypeDetail, forDID: String, forDIDVerKey: String)

case class TestPairwiseKeyCreatedRespMsg(`@type`: TestTypeDetail, agentPairwiseId: String, agentPairwiseVerKey: String)

case class TestGetOwnerAgentDetailReqMsg(`@type`: TestTypeDetail)

case class TestOwnerAgentDetailRespMsg(`@type`: TestTypeDetail, ownerDID: String, agentId: String)

case class TestCreateAgentReqMsg(`@type`: TestTypeDetail, forDID: String, forDIDVerKey: String)

case class TestAgentCreatedRespMsg(`@type`: TestTypeDetail, agentId: String, agentVerKey: String)


class TestUserClient extends TestClientBase {

  case class AgentPairwiseKeyDetail(myPairwiseVerKey: String, agentPairwiseId: String, agentPairwiseVerKey: String)

  override val agentMsgPath: String = "/agency/msg"

  implicit val testCreateAgentReqMsg: RootJsonFormat[TestCreateAgentReqMsg] = jsonFormat3(TestCreateAgentReqMsg.apply)
  implicit val testAgentCreatedRespMsg: RootJsonFormat[TestAgentCreatedRespMsg] = jsonFormat3(TestAgentCreatedRespMsg.apply)

  implicit val testCreatePairwiseKeyReqMsg: RootJsonFormat[TestCreatePairwiseKeyReqMsg] = jsonFormat3(TestCreatePairwiseKeyReqMsg.apply)
  implicit val testPairwiseKeyCreatedRespMsg: RootJsonFormat[TestPairwiseKeyCreatedRespMsg] = jsonFormat3(TestPairwiseKeyCreatedRespMsg.apply)

  implicit val testGetOwnerAgentDetailReqMsg: RootJsonFormat[TestGetOwnerAgentDetailReqMsg] = jsonFormat1(TestGetOwnerAgentDetailReqMsg.apply)
  implicit val testOwnerAgentDetailRespMsg: RootJsonFormat[TestOwnerAgentDetailRespMsg] = jsonFormat3(TestOwnerAgentDetailRespMsg.apply)


  var myAgencyAgentDetail: AgentDetail = _
  var myAgencyAgentPairwiseDetail: AgentPairwiseKeyDetail = _

  var pairwiseDIDDetails: Map[String, AgentPairwiseKeyDetail] = Map.empty

  def getVerKeyForAuthCrypt(agentId: String): String = {
      val allRecord =
        Option(myAgencyAgentDetail).map(r => Map(r.id -> r.verKey)).getOrElse(Map.empty) ++
          Option(myAgencyAgentPairwiseDetail).map(r => Map(r.agentPairwiseId -> r.agentPairwiseVerKey)).getOrElse(Map.empty) ++
          pairwiseDIDDetails.map(r => r._2.agentPairwiseId -> r._2.agentPairwiseVerKey)
    allRecord(agentId)
  }

  def createNewPairwiseKey(): DIDDetail = {
    val newKey = walletAPI.createNewKey(CreateNewKeyParam())(walletInfo)
    val dd = DIDDetail(newKey.DID, newKey.verKey)
    pairwiseDIDDetails += dd.DID -> AgentPairwiseKeyDetail(dd.verKey, null, null)
    dd
  }

  def myAgentVerKey: String = myAgencyAgentDetail.verKey

  def encryptParamForAgent: EncryptParam = EncryptParam (
    KeyInfo(Right(GetVerKeyByDIDParam(myDID, getKeyFromPool = false))),
    KeyInfo(Left(myAgentVerKey))
  )

  def setAgentDetail(id: String, verKey: String): Unit = {
    myAgencyAgentDetail = AgentDetail(id, verKey)
  }

  def setAgencyPairwiseAgentDetail(forMyDID: String, agentPairwiseId: String, agentPairwiseVerKey: String): Unit = {
    myAgencyAgentPairwiseDetail = AgentPairwiseKeyDetail(forMyDID, agentPairwiseId, agentPairwiseVerKey)
  }

  def setPairwiseAgentDetail(forMyDID: String, agentPairwiseId: String, agentPairwiseVerKey: String): Unit = {
    pairwiseDIDDetails.get(forMyDID).foreach { r =>
      pairwiseDIDDetails += forMyDID -> AgentPairwiseKeyDetail(r.myPairwiseVerKey, agentPairwiseId, agentPairwiseVerKey)
    }
  }

  def handleAgentCreatedRespMsg(rm: Array[Byte]): TestAgentCreatedRespMsg = {
    val ac = authDecryptAndUnpackRespMsg[TestAgentCreatedRespMsg](rm)
    setAgentDetail(ac.agentId, ac.agentVerKey)
    ac
  }

  def buildAnonCryptedFwdMsgForAgency(fwdTo: String, origMsg: Array[Byte]): Array[Byte] = {
    val authCryptedPackedMsg = defaultA2AAPI.authCrypt(buildAuthCryptParam(getVerKeyForAuthCrypt(fwdTo), origMsg))
    val fwdMsg = TestFwdReqMsg(TestTypeDetail(MSG_TYPE_FWD, version), fwdTo, authCryptedPackedMsg)
    val fwdPackedMsg = defaultA2AAPI.packMsg(fwdMsg)(ImplicitParam[RootJsonFormat[TestFwdReqMsg]](implicitly))
    defaultA2AAPI.anonCrypt(buildAnonCryptParam(myAgencyAgentDetail.verKey, fwdPackedMsg))
  }

  def buildCreatePairwiseKeyReq(): (DIDDetail, Array[Byte]) = {
    val DIDDetail = createNewPairwiseKey()
    val nativeMsg = TestCreatePairwiseKeyReqMsg(
      TestTypeDetail(MSG_TYPE_CREATE_PAIRWISE_KEY, version), DIDDetail.DID, DIDDetail.verKey)
    val packedMsg = defaultA2AAPI.packMsg(nativeMsg)(ImplicitParam[RootJsonFormat[TestCreatePairwiseKeyReqMsg]](implicitly))
    val reqPlayload = buildAnonCryptedFwdMsgForAgency(myAgencyAgentDetail.id, packedMsg)
    (DIDDetail, reqPlayload)
  }

  def handlePairwiseKeyCreatedRespMsg(forMyDID: String, rm: Array[Byte]): TestPairwiseKeyCreatedRespMsg = {
    val msg = authDecryptAndUnpackRespMsg[TestPairwiseKeyCreatedRespMsg](rm)
    println("### pairwise key created: " + msg)
    setPairwiseAgentDetail(forMyDID, msg.agentPairwiseId, msg.agentPairwiseVerKey)
    msg
  }

  def buildGetOwnerAgentDetailReq(agentId: String): Array[Byte] = {
    val nativeMsg = TestGetOwnerAgentDetailReqMsg(TestTypeDetail(MSG_TYPE_GET_OWNER_AGENT_DETAIL, version))
    val packedMsg = defaultA2AAPI.packMsg(nativeMsg)(ImplicitParam[RootJsonFormat[TestGetOwnerAgentDetailReqMsg]](implicitly))
    buildAnonCryptedFwdMsgForAgency(agentId, packedMsg)
  }

  def handleOwnerAgentDetailRespMsg(rm: Array[Byte]): TestOwnerAgentDetailRespMsg = {
    val msg = authDecryptAndUnpackRespMsg[TestOwnerAgentDetailRespMsg](rm)
    println("### owner detail: " + msg)
    msg
  }

  def buildCreateAgentReq(): Array[Byte] = {
    val nativeMsg = TestCreateAgentReqMsg(TestTypeDetail(MSG_TYPE_CREATE_AGENT, version), myDIDDetail.DID, myDIDDetail.verKey)
    val packedMsg = defaultA2AAPI.packMsg(nativeMsg)(ImplicitParam[RootJsonFormat[TestCreateAgentReqMsg]](implicitly))
    buildAnonCryptedFwdMsgForAgency(myAgencyAgentDetail.id, packedMsg)
  }

}
