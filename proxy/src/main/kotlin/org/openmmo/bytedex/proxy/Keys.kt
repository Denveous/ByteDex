package org.openmmo.bytedex.proxy

import com.google.gson.JsonParser
import org.openmmo.bytedex.proxy.tls.decodePkcs8PrivateKey
import org.openmmo.bytedex.proxy.tls.decodeSpkiPublicKey
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey

data class ProxyKey(
    val name: String,
    val publicKey: ECPublicKey,
    val privateKey: ECPrivateKey,
)

object ProxyKeys {

    private const val RESOURCE = "/bytedex-keys.json"

    val all: Map<String, ProxyKey> by lazy { load() }

    val ls: ProxyKey get() = requireKey("ls")
    val gs: ProxyKey get() = requireKey("gs")
    val cs: ProxyKey get() = requireKey("cs")

    private fun requireKey(name: String): ProxyKey =
        all[name] ?: error("$RESOURCE is missing entry '$name'")

    private fun load(): Map<String, ProxyKey> {
        val stream = ProxyKeys::class.java.getResourceAsStream(RESOURCE)
            ?: error("bundled $RESOURCE not on classpath.")
        val root = InputStreamReader(stream, StandardCharsets.UTF_8).use {
            JsonParser.parseReader(it).asJsonObject
        }
        val out = LinkedHashMap<String, ProxyKey>(root.size())
        for ((name, value) in root.entrySet()) {
            val obj = value.asJsonObject
            out[name] = ProxyKey(
                name = name,
                publicKey = decodeSpkiPublicKey(obj["newPublicKey"].asString),
                privateKey = decodePkcs8PrivateKey(obj["newPrivateKey"].asString),
            )
        }
        return out
    }
}
