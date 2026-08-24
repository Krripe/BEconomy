package org.krripe.beconomy.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Locale

object MenuConfigManager {
    private val folder = Paths.get("config", "beconomy", "gui", "economies").toFile()

    data class EconomyMenuConfig(
        val schemaVersion: Int = 1,
        val currencyType: String,
        val balanceMessage: BalanceMessageConfig = BalanceMessageConfig(),
        val menu: MenuConfig = MenuConfig()
    )

    data class BalanceMessageConfig(
        val enabled: Boolean = true,
        val lines: List<String> = listOf(
            "&7Your &f%currencyType% &7balance is &e%balance% %symbol%"
        )
    )

    data class MenuConfig(
        val enabled: Boolean = true,
        val title: String = "&6%currencyType% Menu",
        val size: Int = 54,
        val fillersEnabled: Boolean = true,
        val fillers: List<FillerConfig> = listOf(),
        val leaderboard: LeaderboardConfig = LeaderboardConfig(),
        val items: List<MenuItemConfig> = listOf()
    )

    data class FillerConfig(
        val material: String = "minecraft:black_stained_glass_pane",
        val name: String = " ",
        val lore: List<String> = listOf(),
        val slots: List<String> = listOf(),
        val customModelData: Int? = null,
        val glow: Boolean = false,
        val hideAdditionalTooltip: Boolean = true,
        val hideTooltip: Boolean = true
    )

    data class LeaderboardConfig(
        val enabled: Boolean = true,
        val slots: List<Int> = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        ),
        val defaultSort: String = "PRICE_HIGH",
        val sortModes: List<String> = listOf(
            "PRICE_HIGH",
            "PRICE_LOW",
            "A_Z"
        ),
        val head: LeaderboardHeadConfig = LeaderboardHeadConfig()
    )

    data class LeaderboardHeadConfig(
        val name: String = "&e#%position% &f%top_player%",
        val lore: List<String> = listOf(
            "&7Balance: &e%top_balance% %symbol%"
        ),
        val customModelData: Int? = null,
        val glow: Boolean = false,
        val hideAdditionalTooltip: Boolean = true,
        val hideTooltip: Boolean = false,
        val actions: ClickActionsConfig = ClickActionsConfig()
    )

    data class MenuItemConfig(
        val slots: List<String> = listOf(),
        val type: String = "item",
        val material: String = "minecraft:stone",
        val name: String = "&fItem",
        val lore: List<String> = listOf(),
        val customModelData: Int? = null,
        val glow: Boolean = false,
        val hideAdditionalTooltip: Boolean = true,
        val hideTooltip: Boolean = false,
        val actions: ClickActionsConfig = ClickActionsConfig()
    )

    data class ClickActionsConfig(
        val leftClick: ActionConfig = ActionConfig(),
        val rightClick: ActionConfig = ActionConfig()
    )

    data class ActionConfig(
        val type: String = "none",
        val value: String = "",
        val closeAfterAction: Boolean = false,
        val sound: SoundConfig? = null
    )

    data class SoundConfig(
        val enabled: Boolean = false,
        val id: String = "minecraft:ui.button.click",
        val volume: Float = 1.0f,
        val pitch: Float = 1.0f
    )

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private val configs = mutableMapOf<String, EconomyMenuConfig>()

    fun loadAll(economies: List<ConfigManager.EconomyConfig>) {
        folder.mkdirs()
        configs.clear()

        economies.forEach { economy ->
            load(economy.currencyType)
        }
    }

    fun reloadAll(economies: List<ConfigManager.EconomyConfig>) {
        loadAll(economies)
    }

    fun get(currencyType: String): EconomyMenuConfig? {
        return configs[currencyType.lowercase(Locale.ROOT)]
    }

    fun load(currencyType: String): EconomyMenuConfig {
        folder.mkdirs()

        val key = currencyType.lowercase(Locale.ROOT)
        val file = getFile(currencyType)
        val defaultConfig = createDefaultConfig(currencyType)

        if (!file.exists()) {
            runCatching {
                file.writeText(gson.toJson(defaultConfig))
            }.onFailure {
                println("[BEconomy] Failed to create economy menu '${file.name}': ${it.message}")
            }

            configs[key] = defaultConfig
            return defaultConfig
        }

        val existingJson = try {
            JsonParser.parseString(Files.readString(file.toPath())).asJsonObject
        } catch (e: Exception) {
            println("[BEconomy] Failed to parse economy menu '${file.name}': ${e.message}")
            configs[key] = defaultConfig
            return defaultConfig
        }

        val loaded = try {
            val defaultJson = gson.toJsonTree(defaultConfig).asJsonObject
            val mergedJson = mergeJson(defaultJson, existingJson)

            mergedJson.addProperty("schemaVersion", 1)
            mergedJson.addProperty("currencyType", currencyType)

            gson.fromJson(mergedJson, EconomyMenuConfig::class.java)
        } catch (e: Exception) {
            println("[BEconomy] Failed to load economy menu '${file.name}': ${e.message}")
            configs[key] = defaultConfig
            return defaultConfig
        }

        runCatching {
            file.writeText(gson.toJson(loaded))
        }.onFailure {
            println("[BEconomy] Failed to update economy menu '${file.name}': ${it.message}")
        }

        configs[key] = loaded
        return loaded
    }

    fun save(currencyType: String) {
        val config = get(currencyType) ?: return
        val file = getFile(currencyType)
        file.parentFile.mkdirs()
        file.writeText(gson.toJson(config))
    }

    fun renameCurrency(oldCurrencyType: String, newCurrencyType: String): Boolean {
        folder.mkdirs()

        val oldKey = oldCurrencyType.lowercase(Locale.ROOT)
        val newKey = newCurrencyType.lowercase(Locale.ROOT)
        val oldFile = getFile(oldCurrencyType)
        val newFile = getFile(newCurrencyType)

        configs.remove(oldKey)
        configs.remove(newKey)

        val sameFile = oldFile.toPath().toAbsolutePath().normalize() ==
                newFile.toPath().toAbsolutePath().normalize()

        if (sameFile) {
            load(newCurrencyType)
            return true
        }

        if (!oldFile.exists()) {
            load(newCurrencyType)
            return false
        }

        var backupFile: java.io.File? = null

        return try {
            newFile.parentFile.mkdirs()

            if (newFile.exists()) {
                backupFile = java.io.File(
                    newFile.parentFile,
                    "${newFile.nameWithoutExtension}.backup-${System.currentTimeMillis()}.json"
                )

                Files.move(
                    newFile.toPath(),
                    backupFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )

                println(
                    "[BEconomy] Existing economy menu '${newFile.name}' was backed up as '${backupFile.name}'."
                )
            }

            Files.move(
                oldFile.toPath(),
                newFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )

            load(newCurrencyType)
            true
        } catch (e: Exception) {
            println(
                "[BEconomy] Failed to rename economy menu '$oldCurrencyType' to '$newCurrencyType': ${e.message}"
            )

            if (!newFile.exists() && backupFile?.exists() == true) {
                runCatching {
                    Files.move(
                        backupFile!!.toPath(),
                        newFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }

            if (oldFile.exists()) {
                runCatching {
                    load(oldCurrencyType)
                }
            }

            false
        }
    }

    fun getFile(currencyType: String) = Paths.get(
        "config",
        "beconomy",
        "gui",
        "economies",
        "${fileName(currencyType)}.json"
    ).toFile()

    fun fileName(currencyType: String): String {
        return currencyType
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "economy" }
    }

    private fun mergeJson(defaults: JsonObject, existing: JsonObject): JsonObject {
        val result = defaults.deepCopy()

        existing.entrySet().forEach { (key, existingValue) ->
            if (existingValue.isJsonNull) return@forEach

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
                result.add(key, existingValue.deepCopy())
            }
        }

        return result
    }

    private fun createDefaultConfig(currencyType: String): EconomyMenuConfig {
        return EconomyMenuConfig(
            currencyType = currencyType,
            balanceMessage = BalanceMessageConfig(
                enabled = true,
                lines = listOf(
                    "",
                    "&6%currencyType%",
                    "&7Player: &f%player%",
                    "&7Balance: &e%balance% %symbol%",
                    ""
                )
            ),
            menu = MenuConfig(
                enabled = true,
                title = "&6%currencyType% Menu",
                size = 54,
                fillersEnabled = true,
                fillers = listOf(
                    FillerConfig(
                        material = "minecraft:black_stained_glass_pane",
                        name = " ",
                        slots = listOf(
                            "0-8",
                            "9",
                            "17",
                            "18",
                            "26",
                            "27",
                            "35",
                            "36",
                            "44",
                            "45-53"
                        )
                    )
                ),
                leaderboard = LeaderboardConfig(),
                items = listOf(
                    MenuItemConfig(
                        slots = listOf("46"),
                        type = "button",
                        material = "minecraft:arrow",
                        name = "&aPrevious Page",
                        actions = ClickActionsConfig(
                            leftClick = ActionConfig(type = "previous_page")
                        )
                    ),
                    MenuItemConfig(
                        slots = listOf("48"),
                        type = "button",
                        material = "minecraft:hopper",
                        name = "&bSort: &f%sort%",
                        lore = listOf("&7Click to change sorting"),
                        actions = ClickActionsConfig(
                            leftClick = ActionConfig(type = "change_sort")
                        )
                    ),
                    MenuItemConfig(
                        slots = listOf("49"),
                        type = "button",
                        material = "minecraft:barrier",
                        name = "&cClose",
                        actions = ClickActionsConfig(
                            leftClick = ActionConfig(type = "close"),
                            rightClick = ActionConfig(type = "close")
                        )
                    ),
                    MenuItemConfig(
                        slots = listOf("50"),
                        type = "button",
                        material = "minecraft:clock",
                        name = "&eRefresh",
                        actions = ClickActionsConfig(
                            leftClick = ActionConfig(type = "refresh")
                        )
                    ),
                    MenuItemConfig(
                        slots = listOf("52"),
                        type = "button",
                        material = "minecraft:arrow",
                        name = "&aNext Page",
                        actions = ClickActionsConfig(
                            leftClick = ActionConfig(type = "next_page")
                        )
                    )
                )
            )
        )
    }
}