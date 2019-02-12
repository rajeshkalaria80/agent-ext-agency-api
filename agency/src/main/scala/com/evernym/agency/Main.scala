package com.evernym.agency

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import com.evernym.agent.api.{CommonParam, ConfigProvider}
import com.evernym.agent.common.config.DefaultConfigProvider

object Main extends App {

  lazy val configProvider: ConfigProvider = DefaultConfigProvider
  lazy val actorSystem: ActorSystem = ActorSystem("agency-agent", configProvider.getConfig)
  lazy val materializer: Materializer = ActorMaterializer()(actorSystem)

  lazy val commonParam: CommonParam = CommonParam(configProvider, actorSystem, materializer)

  lazy val agencyAgentApp = new AgencyAgentApp(commonParam)
  agencyAgentApp.start()

}
