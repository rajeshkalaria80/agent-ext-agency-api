package com.evernym.extension.agency.agent.spec

import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import com.evernym.agent.api.{CommonParam, ConfigProvider, GenericMsg, TransportMsg}
import com.evernym.agent.common.a2a.{AnonCryptedMsg, AuthCryptedMsg}
import com.evernym.agent.common.actor.{AgentDetail, InitAgent}
import com.evernym.agent.common.config.DefaultConfigProvider
import com.evernym.agent.common.CommonConstants._
import com.evernym.extension.agency.agent.akka.AkkaTestBasic
import com.evernym.extension.agency.agent.client.TestAgencyClient
import com.evernym.extension.agency.common.Constants._
import com.evernym.extension.agency.protocol.agent.AgencyAgentWrapper
import org.scalatest.{Assertion, AsyncFlatSpec}

import scala.concurrent.Future


object TestAgencyAgentExtension extends TestKit(AkkaTestBasic.system) {
  lazy val configProvider: ConfigProvider = DefaultConfigProvider
  implicit lazy val materializer: Materializer = ActorMaterializer()

  implicit lazy val commonParam: CommonParam = CommonParam(configProvider, system, materializer)

  val agencyAgentExtension = new AgencyAgentWrapper(commonParam)
  agencyAgentExtension.start()
}


class AgencyBusinessProtocolSpec extends AsyncFlatSpec with SpecCommon with TestAgencyClient {

  def sendAgencyInitMsgToAgencyAgent(msg: Any, f: AgentDetail => Assertion): Future[Assertion] = {
    val futResp = TestAgencyAgentExtension.agencyAgentExtension.handleMsg(TransportMsg("akka-http-transport", GenericMsg(msg)))
    futResp map { resp =>
      val acm = resp.asInstanceOf[AgentDetail]
      f(acm)
    }
  }

  def sendAnyMsgToAgencyAgent(msg: Any, f: Array[Byte] => Assertion): Future[Assertion] = {
    val futResp = TestAgencyAgentExtension.agencyAgentExtension.handleMsg(TransportMsg("akka-http-transport", GenericMsg(msg)))
    futResp map { resp =>
      val acm = resp.asInstanceOf[AuthCryptedMsg]
      f(acm.payload)
    }
  }

  def sendAnonCryptedMsgToCoreAgent(msg: Array[Byte], f: Array[Byte] => Assertion): Future[Assertion] = {
    sendAnyMsgToAgencyAgent(AnonCryptedMsg(msg), f)
  }

  it should "respond to init agent api call" in {
    val req = InitAgent(agencyOwnerDIDDetail.DID, agencyOwnerDIDDetail.verKey)
    sendAgencyInitMsgToAgencyAgent(req, { respPayload =>
      val respMsg = handleAgencyDetailRespMsg(respPayload)
      respMsg.id.isEmpty shouldBe false
    })
  }

  it should "respond to connect api call" in {
    val req = buildConnectReq()
    sendAnonCryptedMsgToCoreAgent(req, { respPayload =>
      val respMsg = handleConnectedRespMsg(respPayload)
      respMsg.`@type`.name shouldBe MSG_TYPE_CONNECTED
    })
  }

  it should "respond to get user's pairwise agency agent owner detail api call" in {
    val req = buildGetOwnerAgentDetailReq(myAgencyAgentPairwiseDetail.agentPairwiseId)
    sendAnonCryptedMsgToCoreAgent(req, { respPayload =>
      val respMsg = handleOwnerAgentDetailRespMsg(respPayload)
      respMsg.`@type`.name shouldBe MSG_TYPE_OWNER_AGENT_DETAIL
    })
  }

  it should "respond to create user agent api call" in {
    val req = buildCreateUserAgentReq()
    sendAnonCryptedMsgToCoreAgent(req, { respPayload =>
      val respMsg = handleAgentCratedRespMsg(respPayload)
      respMsg.`@type`.name shouldBe MSG_TYPE_AGENT_CREATED
    })
  }

  it should "respond to create user pairwise key api call" in {
    val (didDetail, req) = buildCreatePairwiseKeyReq()
    sendAnonCryptedMsgToCoreAgent(req, { respPayload =>
      val respMsg = handlePairwiseKeyCreatedRespMsg(didDetail.DID, respPayload)
      respMsg.`@type`.name shouldBe MSG_TYPE_PAIRWISE_KEY_CREATED
    })
  }

}
