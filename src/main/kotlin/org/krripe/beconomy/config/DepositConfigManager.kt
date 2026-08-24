package org.krripe.beconomy.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.krripe.beconomy.config.GuiConfigManager.FillerConfig
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Locale

object DepositConfigManager {

    private val folder = Paths.get(
        "config",
        "beconomy",
        "gui",
        "deposit"
    ).toFile()

    data class DepositConfig(
        val currencyType: String,
        val enabled: Boolean = true,
        val title: String = "&f%balance% %symbol%",
        val size: Int = 27,
        val depositSlots: List<String> = listOf("2-6"),
        val behavior: BehaviorConfig = BehaviorConfig(),
        val feedback: FeedbackConfig = FeedbackConfig(),
        val commands: CommandConfig = CommandConfig(),
        val fillersEnabled: Boolean = true,
        val fillers: List<FillerConfig> = listOf(),
        val items: List<ItemConfig> = listOf()
    )

    data class FeedbackConfig(
        val successMessage: String = "",
        val invalidMessage: String = "",
        val successSound: SoundConfig = SoundConfig(
            enabled = true,
            id = "minecraft:entity.experience_orb.pickup",
            volume = 0.5f,
            pitch = 1.5f
        ),
        val invalidSound: SoundConfig = SoundConfig(
            enabled = true,
            id = "minecraft:block.note_block.bass",
            volume = 0.5f,
            pitch = 0.7f
        )
    )

    data class BehaviorConfig(
        val leftClick: String = "STACK",
        val rightClick: String = "ONE",
        val shiftClick: Boolean = true,
        val hotbarKeys: Boolean = true,
        val depositAllIncludesHotbar: Boolean = true
    )

    data class CommandConfig(
        val enabled: Boolean = false,
        val aliases: List<String> = listOf(),
        val permission: String = "beconomy.depositgui.%currencyType%"
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

    data class ItemConfig(
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

    private val configs =
        mutableMapOf<String, DepositConfig>()

    fun loadAll(
        economies: List<ConfigManager.EconomyConfig>
    ) {
        folder.mkdirs()
        configs.clear()

        economies.forEach {
            load(it.currencyType)
        }
    }

    fun reloadAll(
        economies: List<ConfigManager.EconomyConfig>
    ) {
        loadAll(economies)
    }

    fun get(
        currencyType: String
    ): DepositConfig? {
        return configs[
            currencyType.lowercase(
                Locale.ROOT
            )
        ]
    }

    fun load(
        currencyType: String
    ): DepositConfig {
        folder.mkdirs()

        val key =
            currencyType.lowercase(
                Locale.ROOT
            )

        val file =
            getFile(currencyType)

        val defaultConfig =
            createDefaultConfig(
                currencyType
            )

        if (!file.exists()) {
            runCatching {
                file.writeText(
                    gson.toJson(
                        defaultConfig
                    )
                )
            }.onFailure {
                println(
                    "[BEconomy] Failed to create deposit GUI '${file.name}': ${it.message}"
                )
            }

            configs[key] =
                defaultConfig

            return defaultConfig
        }

        val existing =
            try {
                JsonParser.parseString(
                    Files.readString(
                        file.toPath()
                    )
                ).asJsonObject
            } catch (e: Exception) {
                println(
                    "[BEconomy] Failed to parse deposit GUI '${file.name}': ${e.message}"
                )

                configs[key] =
                    defaultConfig

                return defaultConfig
            }

        val loaded =
            try {
                val defaults =
                    gson.toJsonTree(
                        defaultConfig
                    ).asJsonObject

                val merged =
                    mergeJson(
                        defaults,
                        existing
                    )

                merged.addProperty(
                    "currencyType",
                    currencyType
                )

                gson.fromJson(
                    merged,
                    DepositConfig::class.java
                )
            } catch (e: Exception) {
                println(
                    "[BEconomy] Failed to load deposit GUI '${file.name}': ${e.message}"
                )

                configs[key] =
                    defaultConfig

                return defaultConfig
            }

        runCatching {
            file.writeText(
                gson.toJson(
                    loaded
                )
            )
        }.onFailure {
            println(
                "[BEconomy] Failed to update deposit GUI '${file.name}': ${it.message}"
            )
        }

        configs[key] =
            loaded

        return loaded
    }

    fun save(
        currencyType: String
    ) {
        val config =
            get(currencyType)
                ?: return

        val file =
            getFile(currencyType)

        file.parentFile.mkdirs()

        file.writeText(
            gson.toJson(
                config
            )
        )
    }

    fun renameCurrency(
        oldCurrencyType: String,
        newCurrencyType: String
    ): Boolean {
        folder.mkdirs()

        val oldKey =
            oldCurrencyType.lowercase(
                Locale.ROOT
            )

        val newKey =
            newCurrencyType.lowercase(
                Locale.ROOT
            )

        val oldFile =
            getFile(oldCurrencyType)

        val newFile =
            getFile(newCurrencyType)

        configs.remove(oldKey)
        configs.remove(newKey)

        val sameFile =
            oldFile.toPath()
                .toAbsolutePath()
                .normalize() ==
                    newFile.toPath()
                        .toAbsolutePath()
                        .normalize()

        if (sameFile) {
            load(newCurrencyType)
            return true
        }

        if (!oldFile.exists()) {
            load(newCurrencyType)
            return false
        }

        var backupFile:
                java.io.File? = null

        return try {
            newFile.parentFile.mkdirs()

            if (newFile.exists()) {
                backupFile =
                    java.io.File(
                        newFile.parentFile,
                        "${newFile.nameWithoutExtension}.backup-${System.currentTimeMillis()}.json"
                    )

                Files.move(
                    newFile.toPath(),
                    backupFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
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
                "[BEconomy] Failed to rename deposit GUI '$oldCurrencyType' to '$newCurrencyType': ${e.message}"
            )

            if (
                !newFile.exists() &&
                backupFile?.exists() == true
            ) {
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

    fun getFile(
        currencyType: String
    ) = Paths.get(
        "config",
        "beconomy",
        "gui",
        "deposit",
        "${fileName(currencyType)}.json"
    ).toFile()

    private fun fileName(
        currencyType: String
    ): String {
        return currencyType
            .trim()
            .lowercase(
                Locale.ROOT
            )
            .replace(
                Regex("[^a-z0-9._-]+"),
                "_"
            )
            .trim('_')
            .ifBlank {
                "economy"
            }
    }

    private fun mergeJson(
        defaults: JsonObject,
        existing: JsonObject
    ): JsonObject {
        val result =
            defaults.deepCopy()

        existing.entrySet()
            .forEach {
                    (key, value) ->

                if (value.isJsonNull) {
                    return@forEach
                }

                val defaultValue =
                    result.get(key)

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

    private fun createDefaultConfig(
        currencyType: String
    ): DepositConfig {
        return DepositConfig(
            currencyType = currencyType,
            enabled = true,
            title = "&f%balance% %symbol%",
            size = 36,
            depositSlots = listOf(
                "11-15"
            ),
            behavior = BehaviorConfig(
                leftClick = "STACK",
                rightClick = "ONE",
                shiftClick = true,
                hotbarKeys = true,
                depositAllIncludesHotbar = true
            ),
            commands = CommandConfig(
                enabled = false,
                aliases = listOf(),
                permission = "beconomy.depositgui.%currencyType%"
            ),
            fillersEnabled = true,
            fillers = listOf(
                FillerConfig(
                    material = "minecraft:black_stained_glass_pane",
                    name = " ",
                    lore = listOf(),
                    slots = listOf(
                        "0-3",
                        "5-10",
                        "16-30",
                        "32-35"
                    ),
                    customModelData = null,
                    glow = false,
                    hideAdditionalTooltip = true,
                    hideTooltip = true
                )
            ),
            items = listOf(
                ItemConfig(
                    slots = listOf(
                        "4"
                    ),
                    type = "button",
                    material = "minecraft:emerald",
                    name = "&aDeposit All",
                    lore = listOf(
                        "&7Deposit every valid",
                        "&7%currencyType% banknote.",
                        "",
                        "&fBalance: &e%balance% %symbol%"
                    ),
                    actions =
                        ClickActionsConfig(
                            leftClick =
                                ActionConfig(
                                    type = "deposit_all"
                                ),
                            rightClick =
                                ActionConfig(
                                    type = "deposit_all"
                                )
                        )
                ),
                ItemConfig(
                    slots = listOf(
                        "31"
                    ),
                    type = "button",
                    material = "minecraft:barrier",
                    name = "&cClose",
                    actions =
                        ClickActionsConfig(
                            leftClick =
                                ActionConfig(
                                    type = "close"
                                ),
                            rightClick =
                                ActionConfig(
                                    type = "close"
                                )
                        )
                )
            )
        )
    }
}