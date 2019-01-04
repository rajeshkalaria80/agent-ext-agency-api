package com.evernym.extension.agency.platform.business

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.pattern.ask
import com.evernym.agent.api._
import com.evernym.agent.common.a2a.{AgentToAgentAPI, DefaultAgentToAgentAPI}
import com.evernym.agent.common.actor._
import com.evernym.agent.common.libindy.LedgerPoolConnManager
import com.evernym.agent.common.util.Util._
import com.evernym.agent.common.wallet._
import com.evernym.extension.agency.common.Constants._
import com.evernym.extension.agency.platform.business.actor.{AgencyActorRefResolver, AgencyAgent, AgencyAgentPairwise, ForId}

import scala.concurrent.Future


class AgencyBusinessPlatform(val commonParam: CommonParam) extends BusinessPlatform with AgencyActorRefResolver {

  val keyValuerStore: ActorRef = actorSystem.actorOf(KeyValueStore.props(configProvider), "key-value-store")

  val agencyAgentId: String = UUID.randomUUID().toString

  def initActors(): Unit = {
    implicit val numberOfShards: Int = 100

    def system: ActorSystem = commonParam.actorSystem

    def getShardId(entityId: String)(implicit numberOfShards: Int) : String =
      (math.abs(entityId.hashCode) % numberOfShards).toString

    def buildPropWithOptionalDispatcherName(prop: Props, dispatcherNameOpt: Option[String]): Props = {
      dispatcherNameOpt.map { dn =>
        val cdnOpt = commonParam.configProvider.getConfigOption(dn)
        cdnOpt.map { _ =>
          system.dispatchers.lookup(dn)
          prop.withDispatcher(dn)
        }.getOrElse(prop)
      }.getOrElse(prop)
    }

    def buildProp(prop: Props, dispatcherName: String): Props =
      buildPropWithOptionalDispatcherName(prop, Option(dispatcherName))

    val poolConnManager: LedgerPoolConnManager = new LedgerPoolConnManager(configProvider)

    val walletAPI: WalletAPI = new DefaultWalletAPI(new LibIndyWalletProvider(configProvider), poolConnManager)

    val walletConfig: WalletConfig = createWalletConfig(configProvider)

    val defaultA2AAPI: AgentToAgentAPI = new DefaultAgentToAgentAPI(walletAPI)

    val agentActorCommonParam: AgentActorCommonParam =
      AgentActorCommonParam(commonParam, new DefaultRoutingAgent(commonParam), walletConfig, walletAPI, defaultA2AAPI)

    ClusterSharding(system).start(
      typeName = AGENCY_AGENT_REGION_ACTOR_NAME,
      entityProps = buildProp(Props(new AgencyAgent(agentActorCommonParam)),
        ACTOR_DISPATCHER_NAME_AGENCY_AGENT),
      settings = ClusterShardingSettings(system),
      extractEntityId = {
        case ForId(id, cmd) ⇒ (id, cmd)
      },
      extractShardId = {
        case ForId(id, _) ⇒ getShardId(id)
      })

    ClusterSharding(system).start(
      typeName = AGENCY_AGENT_PAIRWISE_REGION_ACTOR_NAME,
      entityProps = buildProp(Props(new AgencyAgentPairwise(agentActorCommonParam)),
        ACTOR_DISPATCHER_NAME_AGENCY_AGENT_PAIRWISE),
      settings = ClusterShardingSettings(system),
      extractEntityId = {
        case ForId(id, cmd) ⇒ (id, cmd)
      },
      extractShardId = {
        case ForId(id, _) ⇒ getShardId(id)
      })

    ClusterSharding(system).start(
      typeName = USER_AGENT_REGION_ACTOR_NAME,
      entityProps = buildProp(Props(new UserAgent(agentActorCommonParam)),
        ACTOR_DISPATCHER_NAME_USER_AGENT),
      settings = ClusterShardingSettings(system),
      extractEntityId = {
        case ForId(id, cmd) ⇒ (id, cmd)
      },
      extractShardId = {
        case ForId(id, _) ⇒ getShardId(id)
      })

    ClusterSharding(system).start(
      typeName = USER_AGENT_PAIRWISE_REGION_ACTOR_NAME,
      entityProps = buildProp(Props(new UserAgentPairwise(agentActorCommonParam)),
        ACTOR_DISPATCHER_NAME_USER_AGENT_PAIRWISE),
      settings = ClusterShardingSettings(system),
      extractEntityId = {
        case ForId(id, cmd) ⇒ (id, cmd)
      },
      extractShardId = {
        case ForId(id, _) ⇒ getShardId(id)
      })
  }

  override def handleMsg: PartialFunction[Any, Future[Any]] = {
    case tam: TransportMsg => agencyAgentActorRef ? ForId(agencyAgentId, tam.genericMsg.payload)
    case x => Future.failed(throw new NotImplementedError(s"messages not supported: $x"))
  }

  override def start(inputParam: Option[Any]=None): Unit = {
    initActors()
  }
}

class AgencyBusinessPlatformExtension extends Extension {
  override val name: String = "extension-agency-business-platform"
  override val category: String = "business-platform"

  var agencyAgentPlatform: AgencyBusinessPlatform = _

  override def getSupportedMsgTypes: Set[MsgType] = Set.empty

  override def start(inputParam: Option[Any]=None): Unit = {
//    val commonParam: CommonParam = inputParam.map(_.asInstanceOf[CommonParam]).getOrElse(
//      throw new RuntimeException("invalid input parameter"))
    val commonParam: CommonParam = buildDefaultCommonParam("test")
    val cv = commonParam.actorSystem.settings.config.getValue("akka")
    println("### path 1: " + cv.origin().description())
    agencyAgentPlatform = new AgencyBusinessPlatform(commonParam)
    agencyAgentPlatform.start()
  }

  override def handleMsg: PartialFunction[Any, Future[Any]] = {
    case m => agencyAgentPlatform.handleMsg(m)
  }

  override def stop(): Unit = {
    agencyAgentPlatform.stop()
  }
}