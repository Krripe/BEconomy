package org.krripe.beconomy.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Paths

object GuiConfigManager {

    private val configFile = Paths.get("config", "beconomy", "gui", "menu_top.json").toFile()

    data class GuiConfig(
        val title: String = "&6ᴛᴏᴘ ᴘʟᴀʏᴇʀ ʙᴀʟᴀɴᴄᴇs",
        val size: Int = 54,
        val fillersEnabled: Boolean = true,
        val fillers: MutableList<FillerConfig> = mutableListOf(),
        val headSlots: List<Int> = listOf(),
        val nextPageButton: ButtonConfig,
        val prevPageButton: ButtonConfig,
        val closeButton: ButtonConfig,
        val headLore: List<String> = listOf(),
        val currencyButton: ButtonConfig,
        val sortButton: ButtonConfig
    )

    data class ButtonConfig(
        val slot: Int,
        val material: String,
        val name: String,
        val customModelData: Int = 0,
        val lore: List<String> = listOf(),
        val glow: Boolean = false,
        val hideAdditionalTooltip: Boolean = true,
        val hideTooltip: Boolean = false
    )

    data class FillerConfig(
        val name: String = " ",
        val lore: List<String> = listOf(),
        val icon: String = "minecraft:black_stained_glass_pane",
        val slots: List<String> = listOf(),
        val customModelData: Int? = null,
        val glow: Boolean = false,
        val hideAdditionalTooltip: Boolean = true,
        val hideTooltip: Boolean = true
    )

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    lateinit var config: GuiConfig
        private set

    fun load(legacyGui: JsonObject? = null): GuiConfig {
        configFile.parentFile.mkdirs()
        val migratingFromLegacyConfig =
            !configFile.exists() && legacyGui != null
        val sourceJson = when {
            configFile.exists() -> {
                JsonParser.parseString(
                    Files.readString(configFile.toPath())
                ).asJsonObject
            }
            legacyGui != null -> {
                legacyGui.deepCopy()
            }
            else -> {
                JsonObject()
            }
        }

        migrateLegacySortButton(sourceJson, migratingFromLegacyConfig)

        val defaultJson = gson.toJsonTree(createDefaultConfig()).asJsonObject
        val mergedJson = mergeJson(defaultJson, sourceJson)
        config = gson.fromJson(mergedJson, GuiConfig::class.java)

        save()
        return config
    }

    fun reload(): GuiConfig {
        return load()
    }

    fun save() {
        configFile.parentFile.mkdirs()
        configFile.writeText(
            gson.toJson(config)
        )
    }

    private fun migrateLegacySortButton(
        json: JsonObject,
        forceLegacy: Boolean
    ) {
        if (json.has("currencyButton")) {
            return
        }

        val legacySort = json.get("sortButton")

        if (
            legacySort != null &&
            legacySort.isJsonObject
        ) {
            val sortObject = legacySort.asJsonObject

            val oldName =
                sortObject
                    .get("name")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    .orEmpty()

            val oldLore =
                sortObject
                    .get("lore")
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.joinToString(" ") { it.asString }
                    .orEmpty()

            val looksLikeOldCurrencyButton =
                oldName.contains("%currency%") ||
                        oldLore.contains("%currency%") ||
                        oldName.contains(
                            "Currency",
                            ignoreCase = true
                        )

            if (
                forceLegacy ||
                looksLikeOldCurrencyButton
            ) {
                json.add(
                    "currencyButton",
                    sortObject.deepCopy()
                )

                json.remove("sortButton")
            }
        }
    }

    private fun mergeJson(
        defaults: JsonObject,
        existing: JsonObject
    ): JsonObject {

        val result = defaults.deepCopy()

        existing.entrySet().forEach { (key, existingValue) ->

            if (existingValue.isJsonNull) {
                return@forEach
            }

            val defaultValue = result.get(key)

            if (
                existingValue.isJsonObject &&
                defaultValue != null &&
                defaultValue.isJsonObject
            ) {
                result.add(
                    key,
                    mergeJson(
                        defaultValue.asJsonObject,
                        existingValue.asJsonObject
                    )
                )
            } else {
                result.add(
                    key,
                    existingValue.deepCopy()
                )
            }
        }

        return result
    }

    private fun createDefaultConfig(): GuiConfig {

        return GuiConfig(
            title = "&6ᴛᴏᴘ ᴘʟᴀʏᴇʀ ʙᴀʟᴀɴᴄᴇs",
            size = 54,
            fillersEnabled = true,
            fillers = mutableListOf(
                FillerConfig(
                    name = " ",
                    lore = listOf(),
                    icon = "minecraft:black_stained_glass_pane",
                    slots = listOf(
                        "0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53"
                    ),
                    customModelData = null,
                    glow = false,
                    hideAdditionalTooltip = true,
                    hideTooltip = true
                )
            ),

            headSlots = listOf(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
            ),

            nextPageButton = ButtonConfig(
                slot = 53,
                material = "minecraft:arrow",
                name = "&aɴᴇxᴛ ᴘᴀɢᴇ"
            ),

            prevPageButton = ButtonConfig(
                slot = 45,
                material = "minecraft:arrow",
                name = "&aᴘʀᴇᴠɪᴏᴜs ᴘᴀɢᴇ"
            ),

            closeButton = ButtonConfig(
                slot = 49,
                material = "minecraft:barrier",
                name = "&cᴄʟᴏsᴇ"
            ),

            headLore = listOf(
                "&7ɴᴀᴍᴇ: &8%playerName%",
                "&7ʙᴀʟᴀɴᴄᴇ: &8&e%balance%",
                "&7sᴛᴀᴛᴜs: %status%"
            ),

            currencyButton = ButtonConfig(
                slot = 51,
                material = "minecraft:emerald",
                name = "&bCurrency: &f%currencyType%",
                lore = listOf(
                    "&7ʟᴇꜰᴛ-ᴄʟɪᴄᴋ: ᴄʜᴀɴɢᴇ ᴄᴜʀʀᴇɴᴄʏ"
                )
            ),

            sortButton = ButtonConfig(
                slot = 47,
                material = "minecraft:compass",
                name = "&bSort: &f%sort%",
                lore = listOf(
                    "&7ʟᴇꜰᴛ-ᴄʟɪᴄᴋ: ᴄʜᴀɴɢᴇ sᴏʀᴛɪɴɢ"
                )
            )
        )
    }
}