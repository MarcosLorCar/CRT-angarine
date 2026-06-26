package me.orange.crtangarine.network

import net.neoforged.neoforge.common.ModConfigSpec

class ModConfiguration(builder: ModConfigSpec.Builder) {
    val backendUri: ModConfigSpec.ConfigValue<String>

    init {
        builder.push("general")
        backendUri = builder
            .comment("The address and port of the CRT Ktor backend server.")
            .define("backendUri", "localhost:8080")
        builder.pop()
    }

    companion object {
        val CONFIG: ModConfiguration
        val SPEC: ModConfigSpec

        init {
            val pair = ModConfigSpec.Builder().configure(::ModConfiguration)
            CONFIG = pair.left
            SPEC = pair.right
        }
    }
}
