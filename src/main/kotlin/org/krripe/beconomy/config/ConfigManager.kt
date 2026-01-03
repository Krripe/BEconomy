package org.krripe.beconomy.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.krripe.beconomy.database.DatabaseManager
import java.math.BigDecimal
import java.util.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object ConfigManager {

    private val configFile = Paths.get("config", "beconomy", "config.json").toFile()

    data class Config(
        val commands: List<String> = listOf("beco", "eco"),
        val balanceCommands: List<String> = listOf("bal", "balance"),
        val economy: MutableList<EconomyConfig> = mutableListOf(),
        val gui: GuiConfig,
        var localSave: Boolean = true,
        var databaseSave: Boolean = false,
        val database: DatabaseConfig
    )

    data class DatabaseConfig(
        val type: String,
        val host: String,
        val port: Int,
        val databaseName: String,
        val username: String? = null,
        val password: String? = null
    )

    data class DenominationConfig(
        val value: BigDecimal,
        val material: String,
        val custommodeldata: Int = 0,
        val name: String,
        val lore: List<String> = listOf(),
        val matchOnlyMaterial: Boolean = false,
        val id: String? = null
    )

    data class EconomyConfig(
        val name: String,
        val lore: List<String>,
        val material: String,
        val custommodeldata: Int,
        val currencyType: String,
        val balanceStart: BigDecimal,
        val symbol: String,
        var isPrimary: Boolean = false,
        val transfer: Boolean = true,
        val withdraw: Boolean = true,
        val matchOnlyMaterial: Boolean = false,
        val denominations: List<DenominationConfig>? = null
    )

    data class GuiConfig(
        val title: String = "&6Top Player Balances",
        val size: Int = 54,
        val fillersEnabled: Boolean = true,
        val fillers: MutableList<FillerConfig> = mutableListOf(),
        val headSlots: List<Int> = listOf(),
        val nextPageButton: ButtonConfig,
        val prevPageButton: ButtonConfig,
        val closeButton: ButtonConfig,
        val headLore: List<String> = listOf(),
        val sortButton: ButtonConfig? = null
    )

    data class GuiItem(
        val material: String,
        val name: String,
        val customModelData: Int = 0,
        val lore: List<String> = listOf(),
        val glow: Boolean = false,
        val hideAdditionalTooltip: Boolean = true,
        val hideTooltip: Boolean = false
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
        val icon: String = "minecraft:light_blue_stained_glass_pane",
        val slots: List<String> = listOf(),
        val customModelData: Int? = null,
        val glow: Boolean = false,
        val hideAdditionalTooltip: Boolean = true,
        val hideTooltip: Boolean = false
    )

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    lateinit var config: Config

    fun saveConfig() {
        configFile.parentFile.mkdirs()
        configFile.writeText(gson.toJson(config))
    }

    fun loadConfig(): Config {
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            val defaultConfig = createDefaultConfig()
            configFile.writeText(gson.toJson(defaultConfig))
            config = defaultConfig
            return defaultConfig
        }

        val json = Files.readString(configFile.toPath())
        val existingConfig = gson.fromJson(json, Config::class.java)

        val defaultConfig = createDefaultConfig()
        val updatedConfig = mergeConfigs(existingConfig, defaultConfig)

        if (updatedConfig.databaseSave) {
            val dbConfig = updatedConfig.database
            if (!DatabaseManager.initialize(dbConfig)) {
                println("Failed to initialize database. Falling back to local save.")
                updatedConfig.databaseSave = false
                updatedConfig.localSave = true
            } else if (existingConfig.localSave && updatedConfig.databaseSave) {
                migrateBalancesToDatabase()
            }
        } else if (updatedConfig.localSave && existingConfig.databaseSave) {
            migrateBalancesToLocal()
        }

        if (updatedConfig != existingConfig) {
            saveUpdatedConfig(updatedConfig)
        }

        config = updatedConfig
        return config
    }

    private fun createDefaultConfig(): Config {
        return Config(
            commands = listOf("beco", "eco"),
            balanceCommands = listOf("bal", "balance"),
            economy = mutableListOf(
                EconomyConfig(
                    name = "&eCobble Coin",
                    lore = listOf("&8This is a &bcurrency", "&8Lore 2"),
                    material = "minecraft:paper",
                    custommodeldata = 7381,
                    currencyType = "CobbleCoins",
                    balanceStart = BigDecimal(500),
                    symbol = "$",
                    isPrimary = true,
                    transfer = true,
                    withdraw = true,
                    matchOnlyMaterial = false,
                    denominations = listOf(
                        DenominationConfig(
                            id = "cobble_0",
                            value = BigDecimal("0.5"),
                            material = "minecraft:paper",
                            custommodeldata = 7382,
                            name = "&e0.5 CobbleCoins",
                            lore = listOf("&7Value: 0.5 CobbleCoins"),
                            matchOnlyMaterial = false
                        ),
                        DenominationConfig(
                            id = "cobble_1",
                            value = BigDecimal("1.0"),
                            material = "minecraft:paper",
                            custommodeldata = 7383,
                            name = "&e1 CobbleCoin",
                            lore = listOf("&7Value: 1 CobbleCoin"),
                            matchOnlyMaterial = false
                        ),
                        DenominationConfig(
                            id = "cobble_5",
                            value = BigDecimal("5.0"),
                            material = "minecraft:paper",
                            custommodeldata = 7384,
                            name = "&e5 CobbleCoins",
                            lore = listOf("&7Value: 5 CobbleCoins"),
                            matchOnlyMaterial = false
                        ),
                        DenominationConfig(
                            id = "cobble_10",
                            value = BigDecimal("10"),
                            material = "minecraft:paper",
                            custommodeldata = 7385,
                            name = "&e10 CobbleCoins",
                            lore = listOf("&7Value: 10 CobbleCoins"),
                            matchOnlyMaterial = false
                        )
                    )
                ),
                EconomyConfig(
                    name = "&eGold Coin",
                    lore = listOf("&8This is a &bcurrency", "&8Lore 2"),
                    material = "minecraft:paper",
                    custommodeldata = 8381,
                    currencyType = "GoldCoins",
                    balanceStart = BigDecimal(200),
                    symbol = "$",
                    isPrimary = false,
                    transfer = false,
                    withdraw = true,
                    matchOnlyMaterial = false,
                    denominations = listOf(
                        DenominationConfig(
                            id = "gold_0",
                            value = BigDecimal("0.5"),
                            material = "minecraft:paper",
                            custommodeldata = 8382,
                            name = "&e0.5 GoldCoins",
                            lore = listOf("&7Value: 0.5 GoldCoins"),
                            matchOnlyMaterial = false
                        ),
                        DenominationConfig(
                            id = "gold_1",
                            value = BigDecimal("1.0"),
                            material = "minecraft:paper",
                            custommodeldata = 8383,
                            name = "&e1 GoldCoin",
                            lore = listOf("&7Value: 1 GoldCoin"),
                            matchOnlyMaterial = false
                        ),
                        DenominationConfig(
                            id = "gold_5",
                            value = BigDecimal("5.0"),
                            material = "minecraft:paper",
                            custommodeldata = 8384,
                            name = "&e5 GoldCoins",
                            lore = listOf("&7Value: 5 GoldCoins"),
                            matchOnlyMaterial = false
                        ),
                        DenominationConfig(
                            id = "gold_10",
                            value = BigDecimal("10"),
                            material = "minecraft:paper",
                            custommodeldata = 8385,
                            name = "&e10 GoldCoins",
                            lore = listOf("&7Value: 10 GoldCoins"),
                            matchOnlyMaterial = false
                        )
                    )
                )
            ),
            gui = GuiConfig(
                title = "&6Top Player Balances",
                size = 54,
                fillersEnabled = true,
                fillers = mutableListOf(
                    FillerConfig(
                        name = " ",
                        lore = listOf(),
                        icon = "minecraft:black_stained_glass_pane",
                        slots = listOf("0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53"),
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
                    slot = 50,
                    material = "minecraft:arrow",
                    name = "&aNext Page"
                ),
                prevPageButton = ButtonConfig(
                    slot = 48,
                    material = "minecraft:arrow",
                    name = "&aPrevious Page"
                ),
                closeButton = ButtonConfig(
                    slot = 49,
                    material = "minecraft:barrier",
                    name = "&cClose"
                ),
                headLore = listOf(
                    "&7Name: %playerName%",
                    "&7Balance: &e%balance%",
                    "&7Status: %status%"
                ),
                sortButton = ButtonConfig(
                    slot = 52,
                    material = "minecraft:compass",
                    name = "&bCurrency: &f%currency%",
                    customModelData = 0,
                    lore = listOf("&7Left-click: change currency")
                )
            ),
            localSave = true,
            databaseSave = false,
            database = DatabaseConfig(
                type = "mysql",
                host = "localhost",
                port = 3306,
                databaseName = "economy",
                username = "root",
                password = "password"
            )
        )
    }

    fun getBalance(playerId: UUID, currencyType: String): BigDecimal {
        return if (config.localSave) {
            val playerFile = File("config/beconomy/user/${playerId}.json")
            if (!playerFile.exists()) {
                val startBalance = config.economy.find { it.currencyType == currencyType }?.balanceStart ?: BigDecimal.ZERO
                setBalance(playerId, startBalance, currencyType)
                return startBalance
            }
            val json = Files.readString(playerFile.toPath())
            val jsonObject = JsonParser.parseString(json).asJsonObject
            jsonObject.get(currencyType)?.asBigDecimal ?: BigDecimal.ZERO
        } else if (config.databaseSave) {
            DatabaseManager.getAdapter()?.getBalance(playerId, currencyType) ?: BigDecimal.ZERO
        } else {
            BigDecimal.ZERO
        }
    }

    fun setBalance(playerId: UUID, balance: BigDecimal, currencyType: String) {
        if (config.localSave) {
            val playerFile = File("config/beconomy/user/${playerId}.json")
            val jsonObject = if (playerFile.exists()) {
                JsonParser.parseString(Files.readString(playerFile.toPath())).asJsonObject
            } else {
                JsonObject()
            }

            jsonObject.addProperty(currencyType, balance)

            playerFile.parentFile.mkdirs()
            playerFile.writeText(gson.toJson(jsonObject))
        } else if (config.databaseSave) {
            DatabaseManager.getAdapter()?.setBalance(playerId, balance, currencyType)
        }
    }

    fun renameCurrencyType(oldCurrencyType: String, newCurrencyType: String): Int {
        var affectedPlayers = 0
        if (config.localSave) {
            val userFolder = File("config/beconomy/user")
            if (userFolder.exists() && userFolder.isDirectory) {
                userFolder.listFiles { file -> file.extension == "json" }?.forEach { file ->
                    val jsonObject = JsonParser.parseString(file.readText()).asJsonObject

                    val oldElement = jsonObject.get(oldCurrencyType)
                    if (oldElement != null) {
                        val oldBal = oldElement.asBigDecimal
                        val newBal = jsonObject.get(newCurrencyType)?.asBigDecimal ?: BigDecimal.ZERO

                        jsonObject.addProperty(newCurrencyType, newBal + oldBal)
                        jsonObject.remove(oldCurrencyType)

                        file.writeText(gson.toJson(jsonObject))
                        affectedPlayers++
                    }
                }
            }
        }
        if (config.databaseSave) {
            val adapter = DatabaseManager.getAdapter()
            if (adapter != null) {
                val oldBalances = adapter.getAllBalances(oldCurrencyType)
                val newBalances = adapter.getAllBalances(newCurrencyType)

                oldBalances.forEach { (uuid, oldBal) ->
                    val merged = oldBal + (newBalances[uuid] ?: BigDecimal.ZERO)

                    if (!adapter.hasUserData(uuid, newCurrencyType)) {
                        adapter.initializeUserData(uuid, newCurrencyType)
                    }
                    adapter.setBalance(uuid, merged, newCurrencyType)
                }

                affectedPlayers += oldBalances.size
            }
        }
        return affectedPlayers
    }

    fun initializeUserData(playerId: UUID) {
        config.economy.forEach { currency ->
            val currencyType = currency.currencyType

            val playerFile = File("config/beconomy/user/${playerId}.json")
            val jsonObject = if (playerFile.exists()) {
                JsonParser.parseString(Files.readString(playerFile.toPath())).asJsonObject
            } else {
                JsonObject()
            }

            val adapter = DatabaseManager.getAdapter()
            if (config.databaseSave && adapter != null) {
                val balance = adapter.getBalance(playerId, currencyType)
                jsonObject.addProperty(currencyType, balance)
            } else if (!jsonObject.has(currencyType)) {
                jsonObject.addProperty(currencyType, currency.balanceStart)
            }

            playerFile.parentFile.mkdirs()
            playerFile.writeText(gson.toJson(jsonObject))
        }
    }

    fun hasUserData(uuid: UUID, currencyType: String): Boolean {
        val playerFile = File("config/beconomy/user/${uuid}.json")
        if (!playerFile.exists()) {
            return false
        }
        val jsonObject = JsonParser.parseString(Files.readString(playerFile.toPath())).asJsonObject
        return jsonObject.has(currencyType)
    }

    fun reloadConfig(): Config {
        return loadConfig()
    }

    fun getPrimaryCurrency(): EconomyConfig? {
        return config.economy.find { it.isPrimary }
    }

    fun getCurrencyTypeOrFallback(currencyType: String): String {
        val currency = config.economy.find { it.currencyType == currencyType }
        return currency?.currencyType ?: getPrimaryCurrency()?.currencyType ?: "default_currency"
    }

    fun getCurrencySymbol(currencyType: String): String {
        val currency = config.economy.find { it.currencyType == currencyType }
        return currency?.symbol ?: ""
    }

    private fun mergeConfigs(existingConfig: Config, defaultConfig: Config): Config {
        return defaultConfig.copy(
            commands = existingConfig.commands.ifEmpty { defaultConfig.commands },
            economy = existingConfig.economy.ifEmpty { defaultConfig.economy },
            gui = existingConfig.gui,
            localSave = existingConfig.localSave,
            databaseSave = existingConfig.databaseSave,
            database = existingConfig.database
        )
    }

    private fun saveUpdatedConfig(updatedConfig: Config) {
        configFile.parentFile.mkdirs()
        configFile.writeText(gson.toJson(updatedConfig))
    }

    fun migrateBalancesToDatabase() {
        if (!config.localSave || !config.databaseSave) return

        val adapter = DatabaseManager.getAdapter()
        if (adapter == null) {
            println("Database adapter not initialized. Migration aborted.")
            return
        }

        val userFolder = File("config/beconomy/user")
        if (!userFolder.exists() || !userFolder.isDirectory) {
            println("No local user data found to migrate.")
            return
        }

        userFolder.listFiles { file -> file.extension == "json" }?.forEach { file ->
            val jsonObject = JsonParser.parseString(file.readText()).asJsonObject
            val playerId = UUID.fromString(file.nameWithoutExtension)

            config.economy.forEach { currency ->
                val currencyType = currency.currencyType
                val balance = jsonObject.get(currencyType)?.asBigDecimal ?: BigDecimal.ZERO

                if (!adapter.hasUserData(playerId, currencyType)) {
                    adapter.initializeUserData(playerId, currencyType)
                    adapter.setBalance(playerId, balance, currencyType)
                    println("Migrated balance for player $playerId, currency $currencyType: $balance")
                }
            }
        }
    }

    fun migrateBalancesToLocal() {
        if (!config.databaseSave || !config.localSave) return

        val adapter = DatabaseManager.getAdapter()
        if (adapter == null) {
            println("Database adapter not initialized. Migration aborted.")
            return
        }

        val userFolder = File("config/beconomy/user")
        if (!userFolder.exists()) {
            userFolder.mkdirs()
        }

        val allPlayers = mutableSetOf<UUID>()

        config.economy.forEach { currency ->
            val currencyType = currency.currencyType
            val balances = adapter.getAllBalances(currencyType)
            balances.forEach { (playerId, balance) ->
                allPlayers.add(playerId)

                val playerFile = File(userFolder, "$playerId.json")
                val jsonObject = if (playerFile.exists()) {
                    JsonParser.parseString(Files.readString(playerFile.toPath())).asJsonObject
                } else {
                    JsonObject()
                }

                jsonObject.addProperty(currencyType, balance)
                playerFile.writeText(gson.toJson(jsonObject))
            }
        }

        println("Migration to local complete. Migrated data for ${allPlayers.size} players.")
    }

    object NumberFormatter {
        private val symbols = DecimalFormatSymbols(Locale.getDefault()).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }

        private val decimalFormat = DecimalFormat("#,###.##", symbols)

        fun format(value: BigDecimal): String {
            return decimalFormat.format(value)
        }
    }
}