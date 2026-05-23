package org.openmmo.bytedex.app.agent

import com.sun.tools.attach.VirtualMachine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object AgentAttacher {

    private const val AGENT_RESOURCE = "bytedex-agent.jar"

    data class Result(val gameVersion: Long)

    @Serializable
    private data class AttachReport(val gameVersion: Long = 0L)

    suspend fun attach(pid: String): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            val agentPath = extractAgentToTemp()
            val reportPath = Files.createTempFile("bytedex-attach-", ".json")
            reportPath.toFile().deleteOnExit()
            val vm = VirtualMachine.attach(pid)
            try {
                vm.loadAgent(agentPath.toAbsolutePath().toString(), reportPath.toAbsolutePath().toString())
            } finally {
                vm.detach()
            }
            val report = runCatching {
                val text = Files.readString(reportPath)
                Json { ignoreUnknownKeys = true }.decodeFromString(AttachReport.serializer(), text)
            }.getOrDefault(AttachReport())
            Result(gameVersion = report.gameVersion)
        }
    }

    private fun extractAgentToTemp(): Path {
        val stream = AgentAttacher::class.java.classLoader.getResourceAsStream(AGENT_RESOURCE)
            ?: error("bundled $AGENT_RESOURCE missing from classpath - did :agent:shadowJar run?")
        val temp = Files.createTempFile("bytedex-agent-", ".jar")
        temp.toFile().deleteOnExit()
        stream.use { Files.copy(it, temp, StandardCopyOption.REPLACE_EXISTING) }
        return temp
    }
}
