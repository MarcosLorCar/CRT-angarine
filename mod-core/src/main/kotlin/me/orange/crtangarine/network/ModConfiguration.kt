package me.orange.crtangarine.network

import net.neoforged.neoforge.common.ModConfigSpec

object ModConfiguration {
    enum class ModMode {
        CLIENT, STANDALONE
    }

    var activeMode: ModMode = ModMode.CLIENT

    class ClientConfig(builder: ModConfigSpec.Builder) {
        val backendUri: ModConfigSpec.ConfigValue<String>

        init {
            builder.push("general")
            backendUri = builder
                .comment("The address and port of the CRT Ktor backend server.")
                .define("backendUri", "localhost:8080")
            builder.pop()
        }
    }

    class StandaloneConfig(builder: ModConfigSpec.Builder) {
        val backendUri: ModConfigSpec.ConfigValue<String>
        val embeddedServerPort: ModConfigSpec.ConfigValue<Int>

        init {
            builder.push("general")
            backendUri = builder
                .comment("The fallback address of the CRT Ktor backend server if external connection is needed.")
                .define("backendUri", "localhost:8080")
            embeddedServerPort = builder
                .comment("The port for the embedded standalone Ktor server.")
                .define("embeddedServerPort", 8080)
            builder.pop()
        }
    }

    val CLIENT_CONFIG: ClientConfig
    val CLIENT_SPEC: ModConfigSpec

    val STANDALONE_CONFIG: StandaloneConfig
    val STANDALONE_SPEC: ModConfigSpec

    init {
        val clientPair = ModConfigSpec.Builder().configure(::ClientConfig)
        CLIENT_CONFIG = clientPair.left
        CLIENT_SPEC = clientPair.right

        val standalonePair = ModConfigSpec.Builder().configure(::StandaloneConfig)
        STANDALONE_CONFIG = standalonePair.left
        STANDALONE_SPEC = standalonePair.right
    }

    fun getEffectiveBackendUri(): String {
        return if (activeMode == ModMode.STANDALONE) {
            val port = STANDALONE_CONFIG.embeddedServerPort.get()
            "localhost:$port"
        } else {
            CLIENT_CONFIG.backendUri.get()
        }
    }
}
