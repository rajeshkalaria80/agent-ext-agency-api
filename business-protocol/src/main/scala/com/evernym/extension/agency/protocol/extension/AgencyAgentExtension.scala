package com.evernym.extension.agency.protocol.extension

import com.evernym.agent.api.{CommonParam, Extension, MsgType}
import com.evernym.extension.agency.protocol.agent.AgencyAgentWrapper

import scala.concurrent.Future

class AgencyAgentExtension extends Extension {

  override val name: String = "extension-agency-business-protocol"
  override val category: String = "business-protocol"

  var agencyAgent: AgencyAgentWrapper = _

  override def handleMsg: PartialFunction[Any, Future[Any]] = {
    case x => agencyAgent.handleMsg(x)
  }

  def buildCommonParam: CommonParam = {
    import akka.actor.ActorSystem
    import akka.stream.{ActorMaterializer, Materializer}
    import com.typesafe.config.{Config, ConfigFactory}
    import com.evernym.agent.common.config.ConfigProviderBase
    import com.evernym.agent.api.{CommonParam, ConfigProvider}

    object DefaultConfigProvider extends ConfigProviderBase {
      val config: Config = ConfigFactory.load(getClass.getClassLoader)
    }

    lazy val configProvider: ConfigProvider = DefaultConfigProvider
    lazy val actorSystem: ActorSystem = ActorSystem("agency-agent", configProvider.getConfig)
    lazy val materializer: Materializer = ActorMaterializer()(actorSystem)
    CommonParam(configProvider, actorSystem, materializer)
  }

  override def start(inputParam: Option[Any]): Unit = {

//    val commonParam: CommonParam =
//      inputParam.map(_.asInstanceOf[CommonParam]).getOrElse(
//        throw new RuntimeException("invalid input parameter"))

    agencyAgent = new AgencyAgentWrapper(buildCommonParam)
    agencyAgent.start()
  }

  override def stop(): Unit = {
    agencyAgent.stop()
  }

  override def getSupportedMsgTypes: Set[MsgType] = Set.empty
}

