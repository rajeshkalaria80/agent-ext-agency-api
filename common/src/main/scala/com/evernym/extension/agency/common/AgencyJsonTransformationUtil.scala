package com.evernym.extension.agency.common

import com.evernym.agent.common.actor._
import com.evernym.extension.agency.common.Constants._
import spray.json.RootJsonFormat

trait AgencyJsonTransformationUtil extends AgentJsonTransformationUtil {

  case class ConnectReqMsg(`@type`: TypeDetail, fromDID: String, fromDIDVerKey: String) extends ReqMsgBase
  case class CreateAgentReqMsg(`@type`: TypeDetail, forDID: String, forDIDVerKey: String) extends ReqMsgBase
  case class ConnectedRespMsg(`@type`: TypeDetail, agencyAgentPairwiseId: String, agencyAgentPairwiseVerKey: String) extends RespMsgBase

  private def buildConnectedTypeDetail(ver: String): TypeDetail = TypeDetail(MSG_TYPE_CONNECTED, ver)

  def buildConnectedRespMsg(agencyAgentID: String, agencyAgentVerKey: String)(implicit ver: String): ConnectedRespMsg = {
    ConnectedRespMsg(buildConnectedTypeDetail(ver), agencyAgentID, agencyAgentVerKey)
  }

  implicit val agentDetailMsg: RootJsonFormat[AgentDetail] = jsonFormat2(AgentDetail.apply)

  implicit val connectReqMsg: RootJsonFormat[ConnectReqMsg] = jsonFormat3(ConnectReqMsg.apply)
  implicit val connectRespMsg: RootJsonFormat[ConnectedRespMsg] = jsonFormat3(ConnectedRespMsg.apply)
  implicit val createAgentReqMsg: RootJsonFormat[CreateAgentReqMsg] = jsonFormat3(CreateAgentReqMsg.apply)

}
