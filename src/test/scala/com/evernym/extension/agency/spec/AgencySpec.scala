package com.evernym.extension.agency.spec

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes._
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import com.evernym.agent.api.{CommonParam, ConfigProvider}
import com.evernym.agent.common.actor.{InitAgent, JsonTransformationUtil}
import com.evernym.agent.common.config.DefaultConfigProvider
import com.evernym.extension.agency.akka.AkkaTestBasic
import com.evernym.extension.agency.client.TestUserClient
import com.evernym.extension.agency.transport.http.akka.Platform


object TestPlatform extends TestKit(AkkaTestBasic.system) {
  lazy val configProvider: ConfigProvider = DefaultConfigProvider
  implicit lazy val materializer: Materializer = ActorMaterializer()

  implicit lazy val commonParam: CommonParam = CommonParam(configProvider, system, materializer)

  val platform = new Platform

  def route: Route = platform.transport.route
}


class AgencySpec extends RouteSpecCommon with JsonTransformationUtil {
  lazy val route: Route = TestPlatform.route

  lazy val testUserClient = new TestUserClient()

  it should "respond to connect agent api call" in {
    val req = InitAgent(testUserClient.myDIDDetail.DID, testUserClient.myDIDDetail.verKey)
    testUserClient.buildPostReq("/agency/init", req) ~> route ~> check {
      status shouldBe OK
      testUserClient.handleAgentCreatedRespMsg(responseAs[Array[Byte]])
    }
  }

  it should "respond to create pairwise key api call" in {
    val (didDetail, req) = testUserClient.buildCreatePairwiseKeyReq()
    testUserClient.buildPostAgentMsgReq(req) ~> route ~> check {
      status shouldBe OK
      testUserClient.handlePairwiseKeyCreatedRespMsg(didDetail.DID, responseAs[Array[Byte]])
    }
  }

  it should "respond to get owner agent detail api call" in {
    val req = testUserClient.buildGetOwnerAgentDetailReq(testUserClient.myAgencyAgentDetail.id)
    testUserClient.buildPostAgentMsgReq(req) ~> route ~> check {
      status shouldBe OK
      testUserClient.handleOwnerAgentDetailRespMsg(responseAs[Array[Byte]])
    }
  }

  it should "respond to get owner agent detail for pairwise key api call" in {
    val req = testUserClient.buildGetOwnerAgentDetailReq(testUserClient.pairwiseDIDDetails.head._2.agentPairwiseId)
    testUserClient.buildPostAgentMsgReq(req) ~> route ~> check {
      status shouldBe OK
      testUserClient.handleOwnerAgentDetailRespMsg(responseAs[Array[Byte]])
    }
  }

  it should "respond create agent" in {
    val req = testUserClient.buildGetOwnerAgentDetailReq(testUserClient.pairwiseDIDDetails.head._2.agentPairwiseId)
    testUserClient.buildPostAgentMsgReq(req) ~> route ~> check {
      status shouldBe OK
      testUserClient.handleOwnerAgentDetailRespMsg(responseAs[Array[Byte]])
    }
  }

}
