### clean existing persistence data (only if needed)
delete /tmp/agent directory (that is the default base directory path configured for actor and wallet data)

### how to start agency server
sbt "project agency" run

### how to test api
Start 'Scala Console' in IntelliJ and enter below command (to execute any command/block, press CTRL+ENTER)

```
import com.evernym.extension.agency.agent.client.TestAgencyAgentApiClient
```

#### agency admin: send 'init/create agency agent' api call
```
TestAgencyAgentApiClient.sendCreateAgencyAgent()
```

#### user: send 'connect with agency' api call

```
TestAgencyAgentApiClient.sendConnect()
```

#### user: send 'create user agent' api call

```
TestAgencyAgentApiClient.sendCreateUserAgent()
```

#### user: send 'create pairwise key (for new connection)' api call

```
TestAgencyAgentApiClient.sendCreateUserPairwiseKey()
```
