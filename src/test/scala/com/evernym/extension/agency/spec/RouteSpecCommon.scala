package com.evernym.extension.agency.spec

import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.evernym.agent.common.util.Util.buildDurationInSeconds
import com.evernym.extension.agency.akka.AkkaTestBasic
import com.typesafe.config.Config
import org.iq80.leveldb.util.FileUtils
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration.FiniteDuration


trait RouteSpecCommon extends FlatSpecLike with ScalatestRouteTest with Matchers with BeforeAndAfterAll {
  override def testConfig: Config = AkkaTestBasic.getConfig

  val duration_5_second: FiniteDuration = buildDurationInSeconds(5)

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(duration_5_second)


  override def beforeAll(): Unit = {
    deleteTestStorage()
  }

  def deleteTestStorage(): Unit = {
    try {
      //we are using real lib-indy (not a mock version of it) and hence, each time tests run,
      //we need to clean existing data
      FileUtils.deleteDirectoryContents(new java.io.File("/tmp/agent"))
    } catch {
      case e: Throwable =>
        println("error occurred during deleting indy client directory...: " + e.getMessage)
    }
  }
}
