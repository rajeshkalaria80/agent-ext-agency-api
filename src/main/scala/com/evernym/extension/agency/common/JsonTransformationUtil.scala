package com.evernym.extension.agency.common

import com.evernym.agent.common.util.TransformationUtilBase
import com.evernym.extension.agency.common.Constants._
import com.evernym.agent.common.exception.Exceptions._
import com.evernym.agent.common.CommonConstants._
import com.evernym.agent.common.a2a.ImplicitParam
import spray.json.RootJsonFormat


case class InitAgent(ownerDID: String, ownerDIDVerKey: String)

case class InitAgentForPairwiseKey(ownerDID: String, agentId: String, ownerPairwiseDID: String, ownerPairwiseDIDVerKey: String)

case class RouteDetail(actorTypeId: Int)


//--------

trait MsgBase {

  def throwMissingReqFieldException(fieldName: String): Unit = {
    throw new MissingReqField(TBR, s"required attribute not found (missing/empty/null): '$fieldName'")
  }

  def throwOptionalFieldValueAsEmptyException(fieldName: String): Unit = {
    throw new EmptyValueForOptionalField(TBR, s"empty value given for optional field: '$fieldName'")
  }

  def checkRequired(fieldName: String, fieldValue: Any): Unit = {
    if (Option(fieldValue).isEmpty) throwMissingReqFieldException(fieldName)
  }

  def checkRequired(fieldName: String, fieldValue: List[Any]): Unit = {
    if (Option(fieldValue).isEmpty || fieldValue.isEmpty)
      throwMissingReqFieldException(fieldName)
  }

  def checkRequired(fieldName: String, fieldValue: String): Unit = {
    val fv = Option(fieldValue)
    if (fv.isEmpty || fv.exists(_.trim.length == 0)) throwMissingReqFieldException(fieldName)
  }

  def checkOptionalNotEmpty(fieldName: String, fieldValue: Option[Any]): Unit = {
    fieldValue match {
      case Some(s: String) => if (s.trim.length ==0) throwOptionalFieldValueAsEmptyException(fieldName)
      case Some(_: Any) => //
      case _ => //
    }
  }
}

trait ReqMsgBase extends MsgBase

trait RespMsgBase extends MsgBase

case class TypeDetail(name: String, ver: String, fmt: Option[String]=None) extends MsgBase {
  checkOptionalNotEmpty("fmt", fmt)
}

case class FwdMsg(`@type`: TypeDetail, fwd: String, msg: Array[Byte]) extends ReqMsgBase

case class AgentTypedMsg(`@type`: TypeDetail) extends ReqMsgBase

case class AgentCreatedRespMsg(`@type`: TypeDetail, agentId: String, agentVerKey: String) extends RespMsgBase

case class CreateAgentPairwiseKeyReqMsg(`@type`: TypeDetail, fromDID: String, fromDIDVerKey: String) extends ReqMsgBase

case class PairwiseKeyCreatedRespMsg(`@type`: TypeDetail, agentPairwiseId: String, agentPairwiseVerKey: String) extends RespMsgBase

case class GetOwnerAgentDetailReqMsg(`@type`: TypeDetail) extends ReqMsgBase

case class OwnerAgentDetailRespMsg(`@type`: TypeDetail, ownerDID: String, agentId: String) extends RespMsgBase


trait JsonTransformationUtil extends TransformationUtilBase {

  def implParam[T](implicit rjf: RootJsonFormat[T]): ImplicitParam[RootJsonFormat[T]] = ImplicitParam(rjf)

  implicit val version: String = VERSION_1_0

  private def buildAgentCreatedTypeDetail(ver: String): TypeDetail = TypeDetail(MSG_TYPE_AGENT_CREATED, ver)

  private def buildPairwiseKeyCreatedTypeDetail(ver: String): TypeDetail = TypeDetail(MSG_TYPE_PAIRWISE_KEY_CREATED, ver)

  private def buildOwnerAgentDetail(ver: String): TypeDetail = TypeDetail(MSG_TYPE_OWNER_AGENT_DETAIL, ver)

  def buildAgentCreatedRespMsg(toDID: String, toDIDVerKey: String)(implicit ver: String): AgentCreatedRespMsg = {
    AgentCreatedRespMsg(buildAgentCreatedTypeDetail(ver), toDID, toDIDVerKey)
  }

  def buildPairwiseKeyCreatedRespMsg(agentPairwiseId: String, agentPairwiseVerKey: String)(implicit ver: String): PairwiseKeyCreatedRespMsg = {
    PairwiseKeyCreatedRespMsg(buildPairwiseKeyCreatedTypeDetail(ver), agentPairwiseId, agentPairwiseVerKey)
  }

  def buildOwnerAgentDetailRespMsg(ownerDID: String, agentId: String)(implicit ver: String): OwnerAgentDetailRespMsg = {
    OwnerAgentDetailRespMsg(buildOwnerAgentDetail(ver), ownerDID, agentId)
  }

  implicit val rd: RootJsonFormat[RouteDetail] = jsonFormat1(RouteDetail.apply)
  implicit val initAgent: RootJsonFormat[InitAgent] = jsonFormat2(InitAgent.apply)

  implicit val typeDetail: RootJsonFormat[TypeDetail] = jsonFormat3(TypeDetail.apply)
  implicit val fwdMsgDetail: RootJsonFormat[FwdMsg] = jsonFormat3(FwdMsg.apply)

  implicit val msgType: RootJsonFormat[AgentTypedMsg] = jsonFormat1(AgentTypedMsg.apply)
  implicit val agentCreatedRespMsg: RootJsonFormat[AgentCreatedRespMsg] = jsonFormat3(AgentCreatedRespMsg.apply)

  implicit val createPairwiseKeyReqMsg: RootJsonFormat[CreateAgentPairwiseKeyReqMsg] = jsonFormat3(CreateAgentPairwiseKeyReqMsg.apply)
  implicit val pairwiseKeyCreatedRespMsg: RootJsonFormat[PairwiseKeyCreatedRespMsg] = jsonFormat3(PairwiseKeyCreatedRespMsg.apply)

  implicit val getOwnerAgentDetailReqMsg: RootJsonFormat[GetOwnerAgentDetailReqMsg] = jsonFormat1(GetOwnerAgentDetailReqMsg.apply)
  implicit val ownerAgentDetailRespMsg: RootJsonFormat[OwnerAgentDetailRespMsg] = jsonFormat3(OwnerAgentDetailRespMsg.apply)
}
