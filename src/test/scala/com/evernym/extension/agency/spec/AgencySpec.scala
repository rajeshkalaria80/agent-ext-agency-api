package com.evernym.extension.agency.spec

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import com.evernym.agent.api.{CommonParam, ConfigProvider}
import com.evernym.agent.common.actor.{AgentDetail, InitAgent}
import com.evernym.agent.common.config.DefaultConfigProvider
import com.evernym.extension.agency.akka.AkkaTestBasic
import com.evernym.extension.agency.client.TestUserClient
import com.evernym.extension.agency.common.AgencyJsonTransformationUtil
import com.evernym.extension.agency.transport.http.akka.Platform


object TestPlatform extends TestKit(AkkaTestBasic.system) {
  lazy val configProvider: ConfigProvider = DefaultConfigProvider
  implicit lazy val materializer: Materializer = ActorMaterializer()

  implicit lazy val commonParam: CommonParam = CommonParam(configProvider, system, materializer)

  val platform = new Platform

  def route: Route = platform.transport.route
}


class AgencySpec extends RouteSpecCommon with AgencyJsonTransformationUtil with TestUserClient {
  lazy val route: Route = TestPlatform.route

  import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.byteArrayUnmarshaller

  it should "respond to init agent api call" in {
    val req = InitAgent(agencyOwnerDIDDetail.DID, agencyOwnerDIDDetail.verKey)
    buildPostReq("/agency/init", req) ~> route ~> check {
      status shouldBe OK
      handleAgencyDetailRespMsg(responseAs[AgentDetail])
    }
  }

  it should "respond to connect api call" in {
    val req = buildConnectReq()
    buildPostAgentMsgReq(req) ~> route ~> check {
      status shouldBe OK
      handleConnectedRespMsg(responseAs[Array[Byte]])
    }
  }

  it should "respond to get user's pairwise agency agent owner detail api call" in {
    val req = buildGetOwnerAgentDetailReq(myAgencyAgentPairwiseDetail.agentPairwiseId)
    buildPostAgentMsgReq(req) ~> route ~> check {
      status shouldBe OK
      handleOwnerAgentDetailRespMsg(responseAs[Array[Byte]])
    }
  }

  it should "respond create agent" in {
    val req = buildCreateAgentReq()
    buildPostAgentMsgReq(req) ~> route ~> check {
      status shouldBe OK
      handleAgentCratedRespMsg(responseAs[Array[Byte]])
    }
  }

  it should "respond to create pairwise key api call" in {
    val (didDetail, req) = buildCreatePairwiseKeyReq()
    buildPostAgentMsgReq(req) ~> route ~> check {
      status shouldBe OK
      handlePairwiseKeyCreatedRespMsg(didDetail.DID, responseAs[Array[Byte]])
    }
  }

}
