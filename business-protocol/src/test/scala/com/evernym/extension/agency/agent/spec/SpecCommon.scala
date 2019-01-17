package com.evernym.extension.agency.agent.spec

import org.iq80.leveldb.util.FileUtils
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}


trait SpecCommon extends BeforeAndAfterAll with Matchers { this: Suite =>

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
