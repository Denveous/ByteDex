package org.openmmo.bytedex.app.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.exists
import kotlin.io.path.notExists

@Serializable
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

class TokenStore(
    private val path: Path = defaultPath(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {

    fun load(): Tokens? {
        if (path.notExists()) return null
        return runCatching { json.decodeFromString(Tokens.serializer(), Files.readString(path)) }
            .getOrNull()
    }

    fun save(tokens: Tokens) {
        Files.createDirectories(path.parent)
        Files.writeString(
            path,
            json.encodeToString(Tokens.serializer(), tokens),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        tightenPermissions(path)
    }

    fun clear() {
        if (path.exists()) Files.delete(path)
    }

    private fun tightenPermissions(p: Path) {
        runCatching {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------"))
        }
    }

    companion object {
        fun defaultPath(): Path = Path.of(System.getProperty("user.home"), ".bytedex", "tokens.json")
    }
}
