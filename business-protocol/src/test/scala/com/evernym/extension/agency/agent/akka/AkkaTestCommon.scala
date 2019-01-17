package com.evernym.extension.agency.agent.akka


import java.net.ServerSocket

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

trait AkkaTestCommon {

  def singleNodeClusterSharded(systemName: String, port: Int, tdir: String): Config =
    ConfigFactory.parseString(
    s"""
    akka {

      loglevel = "debug"
      
      test {
        single-expect-default = 5s
      }

      loggers = ["akka.testkit.TestEventListener"]

      debug {
        receive = on
      }

      actor {
        provider = "akka.cluster.ClusterActorRefProvider"
      }

      remote {
        log-remote-lifecycle-events = off
        netty.tcp {
          hostname = "127.0.0.1"
          port = $port
        }
      }

      persistence {
        journal {
          plugin = "akka.persistence.journal.leveldb"
          leveldb {
            dir = "$tdir/journal"
            native = false
          }
        }
        snapshot-store {
          plugin = "akka.persistence.snapshot-store.local"
          local.dir = "$tdir/snapshots"
        }
      }

      cluster {
        auto-down-unreachable-after = 0s
        seed-nodes = [
          "akka.tcp://$systemName@127.0.0.1:$port"
        ]
        roles = ["backend"]
      }

      actor {

        serializers {
          protoser = "com.evernym.agent.common.actor.ProtoBufSerializer"
        }
        serialization-bindings {
          "com.evernym.agent.common.actor.TransformedEvent" = protoser
        }
      }
    }


    """)

  def tmpdir(systemName: String) = s"target/actorspecs/$systemName"

  def getConfigByPort(port: Int): Config = {
    val systemName = "actorSpecSystem" + port
    val tdir = tmpdir(systemName)
    singleNodeClusterSharded(systemName, port, tdir)
  }

  def getConfig: Config = {
    getConfigByPort(getNextAvailablePort)
  }

  def system: ActorSystem = {
    val port = getNextAvailablePort
    val systemName = "actorSpecSystem" + port
    val config = getConfigByPort(port)
    ActorSystem(systemName, config)
  }

  def getNextAvailablePort: Int = {
    val ss = new ServerSocket(0)
    ss.setReuseAddress(true)
    val port = ss.getLocalPort
    ss.close()
    port
  }

}

object AkkaTestBasic extends AkkaTestCommon