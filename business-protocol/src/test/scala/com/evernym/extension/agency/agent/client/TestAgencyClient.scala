package com.evernym.extension.agency.agent.client

import com.evernym.agent.common.a2a.{EncryptParam, GetVerKeyByDIDParam, ImplicitParam, KeyInfo}
import com.evernym.agent.common.actor.AgentDetail
import com.evernym.agent.common.CommonConstants._
import com.evernym.agent.common.wallet.{CreateNewKeyParam, DefaultWalletAPI, WalletAPI}
import com.evernym.extension.agency.common.Constants._
import spray.json.RootJsonFormat


case class TestConnectReqMsg(`@type`: TestTypeDetail, fromDID: String, fromDIDVerKey: String)

case class TestConnectedRespMsg(`@type`: TestTypeDetail, agencyAgentPairwiseId: String, agencyAgentPairwiseVerKey: String)

case class TestGetOwnerAgentDetailReqMsg(`@type`: TestTypeDetail)

case class TestOwnerAgentDetailRespMsg(`@type`: TestTypeDetail, ownerDID: String, agentId: String)

case class TestCreateAgentReqMsg(`@type`: TestTypeDetail, forDID: String, forDIDVerKey: String)

case class TestAgentCreatedRespMsg(`@type`: TestTypeDetail, agentId: String, agentVerKey: String)

case class TestCreatePairwiseKeyReqMsg(`@type`: TestTypeDetail, forDID: String, forDIDVerKey: String)

case class TestPairwiseKeyCreatedRespMsg(`@type`: TestTypeDetail, agentPairwiseId: String, agentPairwiseVerKey: String)


trait TestAgencyClient extends TestClientBase {

  case class AgencyAgentPairwiseKeyDetail(agentPairwiseId: String, agentPairwiseVerKey: String)

  case class AgentPairwiseKeyDetail(myPairwiseVerKey: String, agentPairwiseId: String, agentPairwiseVerKey: String)

  override val agentMsgPath: String = "/agency/msg"

  lazy val walletAPI: WalletAPI = new DefaultWalletAPI(walletProvider, ledgerPoolMngr)

  implicit val testCreateAgentReqMsg: RootJsonFormat[TestCreateAgentReqMsg] = jsonFormat3(TestCreateAgentReqMsg.apply)
  implicit val testAgentCreatedRespMsg: RootJsonFormat[TestAgentCreatedRespMsg] = jsonFormat3(TestAgentCreatedRespMsg.apply)

  implicit val testConnectReqMsg: RootJsonFormat[TestConnectReqMsg] = jsonFormat3(TestConnectReqMsg.apply)
  implicit val testConnectedRespMsg: RootJsonFormat[TestConnectedRespMsg] = jsonFormat3(TestConnectedRespMsg.apply)

  implicit val testGetOwnerAgentDetailReqMsg: RootJsonFormat[TestGetOwnerAgentDetailReqMsg] = jsonFormat1(TestGetOwnerAgentDetailReqMsg.apply)
  implicit val testOwnerAgentDetailRespMsg: RootJsonFormat[TestOwnerAgentDetailRespMsg] = jsonFormat3(TestOwnerAgentDetailRespMsg.apply)

  implicit val testCreatePairwiseKeyReqMsg: RootJsonFormat[TestCreatePairwiseKeyReqMsg] = jsonFormat3(TestCreatePairwiseKeyReqMsg.apply)
  implicit val testPairwiseKeyCreatedRespMsg: RootJsonFormat[TestPairwiseKeyCreatedRespMsg] = jsonFormat3(TestPairwiseKeyCreatedRespMsg.apply)


  var agencyAgentDetail: AgentDetail = _

  var myAgencyAgentPairwiseDetail: AgencyAgentPairwiseKeyDetail = _

  var myUserAgentDetail: AgentDetail = _

  var myUserAgentPairwiseDetails: Map[String, AgentPairwiseKeyDetail] = Map.empty

  def getVerKeyForAuthCrypt(agentId: String): String = {
      val allRecord =
        Option(agencyAgentDetail).map(r => Map(r.id -> r.verKey)).getOrElse(Map.empty) ++
          Option(myAgencyAgentPairwiseDetail).map(r => Map(r.agentPairwiseId -> r.agentPairwiseVerKey)).getOrElse(Map.empty) ++
            Option(myUserAgentDetail).map(r => Map(r.id -> r.verKey)).getOrElse(Map.empty) ++
              myUserAgentPairwiseDetails.map(r => r._2.agentPairwiseId -> r._2.agentPairwiseVerKey)
    allRecord(agentId)
  }

  def createNewPairwiseKey(): DIDDetail = {
    val newKey = walletAPI.createNewKey(CreateNewKeyParam())(walletInfo)
    val dd = DIDDetail(newKey.DID, newKey.verKey)
    myUserAgentPairwiseDetails += dd.DID -> AgentPairwiseKeyDetail(dd.verKey, null, null)
    dd
  }

  def myAgentVerKey: String = agencyAgentDetail.verKey

  def encryptParamForAgent: EncryptParam = EncryptParam (
    KeyInfo(Right(GetVerKeyByDIDParam(myDID, getKeyFromPool = false))),
    KeyInfo(Left(myAgentVerKey))
  )

  def setAgencyAgentDetail(id: String, verKey: String): Unit = {
    agencyAgentDetail = AgentDetail(id, verKey)
  }

  def setAgencyAgentPairwiseDetail(agentPairwiseId: String, agentPairwiseVerKey: String): Unit = {
    myAgencyAgentPairwiseDetail = AgencyAgentPairwiseKeyDetail(agentPairwiseId, agentPairwiseVerKey)
  }

  def handleAgencyDetailRespMsg(ad: AgentDetail): AgentDetail = {
    setAgencyAgentDetail(ad.id, ad.verKey)
    ad
  }

  def buildAnonCryptedFwdMsgForAgency(fwdTo: String, origMsg: Array[Byte]): Array[Byte] = {
    val authCryptedPackedMsg = defaultA2AAPI.authCrypt(buildAuthCryptParam(getVerKeyForAuthCrypt(fwdTo), origMsg))
    val fwdMsg = TestFwdReqMsg(TestTypeDetail(MSG_TYPE_FWD, version), fwdTo, authCryptedPackedMsg)
    val fwdPackedMsg = defaultA2AAPI.packMsg(fwdMsg)(ImplicitParam[RootJsonFormat[TestFwdReqMsg]](implicitly))
    defaultA2AAPI.anonCrypt(buildAnonCryptParam(agencyAgentDetail.verKey, fwdPackedMsg))
  }

  def buildConnectReq(): Array[Byte] = {
    val nativeMsg = TestConnectReqMsg(
      TestTypeDetail(MSG_TYPE_CONNECT, version), userDIDDetail.DID, userDIDDetail.verKey)
    val packedMsg = defaultA2AAPI.packMsg(nativeMsg)(ImplicitParam[RootJsonFormat[TestConnectReqMsg]](implicitly))
    buildAnonCryptedFwdMsgForAgency(agencyAgentDetail.id, packedMsg)
  }

  def handleConnectedRespMsg(rm: Array[Byte]): TestConnectedRespMsg = {
    val msg = authDecryptAndUnpackRespMsg[TestConnectedRespMsg](rm)
    println("### pairwise key created: " + msg)
    setAgencyAgentPairwiseDetail(msg.agencyAgentPairwiseId, msg.agencyAgentPairwiseVerKey)
    msg
  }

  def buildGetOwnerAgentDetailReq(agencyPairwiseAgentId: String): Array[Byte] = {
    val nativeMsg = TestGetOwnerAgentDetailReqMsg(TestTypeDetail(MSG_TYPE_GET_OWNER_AGENT_DETAIL, version))
    val packedMsg = defaultA2AAPI.packMsg(nativeMsg)(ImplicitParam[RootJsonFormat[TestGetOwnerAgentDetailReqMsg]](implicitly))
    buildAnonCryptedFwdMsgForAgency(agencyPairwiseAgentId, packedMsg)
  }

  def handleOwnerAgentDetailRespMsg(rm: Array[Byte]): TestOwnerAgentDetailRespMsg = {
    val msg = authDecryptAndUnpackRespMsg[TestOwnerAgentDetailRespMsg](rm)
    println("### owner detail: " + msg)
    msg
  }

  def buildCreateUserAgentReq(): Array[Byte] = {
    val nativeMsg = TestCreateAgentReqMsg(TestTypeDetail(MSG_TYPE_CREATE_AGENT, version), userDIDDetail.DID, userDIDDetail.verKey)
    val packedMsg = defaultA2AAPI.packMsg(nativeMsg)(ImplicitParam[RootJsonFormat[TestCreateAgentReqMsg]](implicitly))
    buildAnonCryptedFwdMsgForAgency(myAgencyAgentPairwiseDetail.agentPairwiseId, packedMsg)
  }

  def handleAgentCratedRespMsg(rm: Array[Byte]): TestAgentCreatedRespMsg = {
    val msg = authDecryptAndUnpackRespMsg[TestAgentCreatedRespMsg](rm)
    println("### agent created: " + msg)
    myUserAgentDetail = AgentDetail(msg.agentId, msg.agentVerKey)
    msg
  }

  def buildCreatePairwiseKeyReq(): (DIDDetail, Array[Byte]) = {
    val DIDDetail = createNewPairwiseKey()
    val nativeMsg = TestCreatePairwiseKeyReqMsg(
      TestTypeDetail(MSG_TYPE_CREATE_PAIRWISE_KEY, version), DIDDetail.DID, DIDDetail.verKey)
    val packedMsg = defaultA2AAPI.packMsg(nativeMsg)(ImplicitParam[RootJsonFormat[TestCreatePairwiseKeyReqMsg]](implicitly))
    val req = buildAnonCryptedFwdMsgForAgency(myUserAgentDetail.id, packedMsg)
    (DIDDetail, req)
  }

  def setPairwiseAgentDetail(forMyDID: String, agentPairwiseId: String, agentPairwiseVerKey: String): Unit = {
    myUserAgentPairwiseDetails.get(forMyDID).foreach { r =>
      myUserAgentPairwiseDetails += forMyDID -> AgentPairwiseKeyDetail(r.myPairwiseVerKey, agentPairwiseId, agentPairwiseVerKey)
    }
  }

  def handlePairwiseKeyCreatedRespMsg(forMyDID: String, rm: Array[Byte]): TestPairwiseKeyCreatedRespMsg = {
    val msg = authDecryptAndUnpackRespMsg[TestPairwiseKeyCreatedRespMsg](rm)
    setPairwiseAgentDetail(forMyDID, msg.agentPairwiseId, msg.agentPairwiseVerKey)
    msg
  }

}
