package org.krripe.beconomy.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Paths

object BarConfigManager {

    private val configFile = Paths.get(
        "config",
        "beconomy",
        "actionbar.json"
    ).toFile()

    data class BarConfig(
        val enabled: Boolean = false,
        val updateIntervalTicks: Int = 20,
        val layout: String = "%economies%",
        val separator: String = "   ",
        val economies: List<BarEconomyConfig> = listOf()
    )

    data class BarEconomyConfig(
        val currencyType: String,
        val enabled: Boolean = true,
        val format: String = "&f%balance% %symbol%",
        val prefix: String = "",
        val suffix: String = "",
        val hideWhenZero: Boolean = false
    )

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    lateinit var config: BarConfig
        private set

    fun load(): BarConfig {
        configFile.parentFile.mkdirs()

        if (!configFile.exists()) {
            config = createDefaultConfig()
            save()
            return config
        }

        val existing = try {
            JsonParser.parseString(
                Files.readString(configFile.toPath())
            ).asJsonObject
        } catch (e: Exception) {
            println(
                "[BEconomy] Failed to load actionbar.json: ${e.message}"
            )

            config = createDefaultConfig()
            return config
        }

        val loaded = try {
            val defaults = gson
                .toJsonTree(createDefaultConfig())
                .asJsonObject

            val merged = mergeJson(
                defaults,
                existing
            )

            gson.fromJson(
                merged,
                BarConfig::class.java
            )
        } catch (e: Exception) {
            println(
                "[BEconomy] Failed to parse actionbar.json: ${e.message}"
            )

            config = createDefaultConfig()
            return config
        }

        config = loaded
        save()

        return config
    }

    fun reload(): BarConfig {
        return load()
    }

    fun save() {
        configFile.parentFile.mkdirs()
        configFile.writeText(
            gson.toJson(config)
        )
    }

    private fun mergeJson(
        defaults: JsonObject,
        existing: JsonObject
    ): JsonObject {
        val result = defaults.deepCopy()

        existing.entrySet().forEach { (key, value) ->
            if (value.isJsonNull) return@forEach

            val defaultValue = result.get(key)

            if (
                value.isJsonObject &&
                defaultValue != null &&
                defaultValue.isJsonObject
            ) {
                result.add(
                    key,
                    mergeJson(
                        defaultValue.asJsonObject,
                        value.asJsonObject
                    )
                )
            } else {
                result.add(
                    key,
                    value.deepCopy()
                )
            }
        }

        return result
    }

    private fun createDefaultConfig(): BarConfig {
        return BarConfig(
            enabled = false,
            updateIntervalTicks = 20,
            separator = "   ",
            economies = listOf()
        )
    }
}