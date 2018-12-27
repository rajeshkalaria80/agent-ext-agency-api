package com.evernym.extension.agency.common

import akka.util.Timeout
import com.evernym.agent.api.ConfigProvider
import com.evernym.agent.common.util.Util.buildTimeout


trait GeneralTimeout {

  def config: ConfigProvider
  implicit lazy val timeout: Timeout = buildTimeout(config,
    "agent.timeouts.akka-actor-msg-reply-timeout", 5)
}
