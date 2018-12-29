package com.evernym.extension.agency.platform

import akka.actor.{ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import com.evernym.agent.api.{AgentMsgHandler, CommonParam, ConfigProvider, TransportMsgRouter}
import com.evernym.agent.common.a2a.{AgentToAgentAPI, DefaultAgentToAgentAPI}
import com.evernym.agent.common.actor.{AgentActorCommonParam, UserAgent, UserAgentPairwise}
import com.evernym.agent.common.libindy.LedgerPoolConnManager
import com.evernym.agent.common.util.Util.buildWalletConfig
import com.evernym.agent.common.wallet.{LibIndyWalletProvider, WalletAPI, WalletConfig, WalletProvider}
import com.evernym.extension.agency.common.Constants._
import com.evernym.extension.agency.msg_handler.{AgencyAgentMsgHandler, DefaultRoutingAgent}
import com.evernym.extension.agency.msg_handler.actor.{AgencyAgent, AgencyAgentPairwise, ForId}
import com.evernym.extension.agency.router.DefaultMsgRouter
import com.evernym.extension.agency.transport.http.akka.AgencyAPI


class DefaultWalletAPI(val walletProvider: WalletProvider, val ledgerPoolManager: LedgerPoolConnManager) extends WalletAPI

trait PlatformBase {

  implicit val numberOfShards: Int = 100

  implicit def commonParam: CommonParam
  def system: ActorSystem = commonParam.actorSystem

  def getShardId(entityId: String)(implicit numberOfShards: Int) : String =
    (math.abs(entityId.hashCode) % numberOfShards).toString

  def buildProp(prop: Props, dispatcherNameOpt: Option[String]=None): Props = {
    dispatcherNameOpt.map { dn =>
      val cdnOpt = commonParam.configProvider.getConfigOption(dn)
      cdnOpt.map { _ =>
        system.dispatchers.lookup(dn)
        prop.withDispatcher(dn)
      }.getOrElse(prop)
    }.getOrElse(prop)
  }

  lazy val configProvider: ConfigProvider = commonParam.configProvider

  lazy val poolConnManager: LedgerPoolConnManager = new LedgerPoolConnManager(configProvider)

  lazy val walletAPI: WalletAPI = new DefaultWalletAPI(new LibIndyWalletProvider(configProvider), poolConnManager)

  lazy val walletConfig: WalletConfig = buildWalletConfig(configProvider)

  val defaultA2AAPI: AgentToAgentAPI = new DefaultAgentToAgentAPI(walletAPI)

  lazy val agentCommonParam: AgentActorCommonParam =
    AgentActorCommonParam(commonParam, new DefaultRoutingAgent, walletConfig, walletAPI, defaultA2AAPI)

  ClusterSharding(system).start(
    typeName = AGENCY_AGENT_REGION_ACTOR_NAME,
    entityProps = buildProp(Props(new AgencyAgent(agentCommonParam)), Option(ACTOR_DISPATCHER_NAME_AGENCY_AGENT)),
    settings = ClusterShardingSettings(system),
    extractEntityId = {
      case ForId(id, cmd) ⇒ (id, cmd)
    },
    extractShardId = {
      case ForId(id, _) ⇒ getShardId(id)
    })

  ClusterSharding(system).start(
    typeName = AGENCY_AGENT_PAIRWISE_REGION_ACTOR_NAME,
    entityProps = buildProp(Props(new AgencyAgentPairwise(agentCommonParam)), Option(ACTOR_DISPATCHER_NAME_AGENCY_AGENT_PAIRWISE)),
    settings = ClusterShardingSettings(system),
    extractEntityId = {
      case ForId(id, cmd) ⇒ (id, cmd)
    },
    extractShardId = {
      case ForId(id, _) ⇒ getShardId(id)
    })

  ClusterSharding(system).start(
    typeName = USER_AGENT_REGION_ACTOR_NAME,
    entityProps = buildProp(Props(new UserAgent(agentCommonParam)), Option(ACTOR_DISPATCHER_NAME_USER_AGENT)),
    settings = ClusterShardingSettings(system),
    extractEntityId = {
      case ForId(id, cmd) ⇒ (id, cmd)
    },
    extractShardId = {
      case ForId(id, _) ⇒ getShardId(id)
    })

  ClusterSharding(system).start(
    typeName = USER_AGENT_PAIRWISE_REGION_ACTOR_NAME,
    entityProps = buildProp(Props(new UserAgentPairwise(agentCommonParam)), Option(ACTOR_DISPATCHER_NAME_USER_AGENT_PAIRWISE)),
    settings = ClusterShardingSettings(system),
    extractEntityId = {
      case ForId(id, cmd) ⇒ (id, cmd)
    },
    extractShardId = {
      case ForId(id, _) ⇒ getShardId(id)
    })

  lazy val agentMsgHandler: AgentMsgHandler = new AgencyAgentMsgHandler(agentCommonParam)

  lazy val defaultMsgRouter: TransportMsgRouter = new DefaultMsgRouter(configProvider, agentMsgHandler)

  val transport = new AgencyAPI(commonParam, defaultMsgRouter)

  transport.start()

}