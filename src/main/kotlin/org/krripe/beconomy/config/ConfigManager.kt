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
        val numberFormat: NumberFormatConfig = NumberFormatConfig(),
        val economy: MutableList<EconomyConfig> = mutableListOf(),
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

    data class NumberFormatConfig(
        val groupingSeparator: String = ".",
        val decimalSeparator: String = ",",
        val useGrouping: Boolean = true,
        val maxDecimalPlaces: Int = 2
    )

    data class EconomySubcommandConfig(
        val balance: List<String> = listOf("balance"),
        val top: List<String> = listOf("top")
    )

    data class EconomyCommandConfig(
        val enabled: Boolean = false,
        val aliases: List<String> = listOf(),
        val defaultAction: String = "balance",
        val balance: Boolean = true,
        val top: Boolean = true,
        val subcommands: EconomySubcommandConfig = EconomySubcommandConfig(),
        val permissions: EconomyCommandPermissionConfig = EconomyCommandPermissionConfig()
    )

    data class EconomyCommandPermissionConfig(
        val command: String = "beconomy.custom.%currencyType%",
        val balance: String = "beconomy.custom.%currencyType%.balance",
        val balanceOthers: String = "beconomy.custom.%currencyType%.balance.others",
        val top: String = "beconomy.custom.%currencyType%.top"
    )

    data class PayConfig(
        val minimumAmount: BigDecimal = BigDecimal.ZERO,
        val maximumAmount: BigDecimal = BigDecimal("-1")
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
        val balance: Boolean = true,
        val top: Boolean = true,
        val showInGlobalBalance: Boolean = true,
        val showInGlobalTop: Boolean = true,
        val customCommands: EconomyCommandConfig = EconomyCommandConfig(),
        val pay: PayConfig = PayConfig(),
        val matchOnlyMaterial: Boolean = false,
        val denominations: List<DenominationConfig>? = null
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

            configFile.writeText(
                gson.toJson(defaultConfig)
            )

            GuiConfigManager.load()

            config = defaultConfig
            MenuConfigManager.loadAll(defaultConfig.economy)

            return defaultConfig
        }

        val json = Files.readString(configFile.toPath())

        val root = JsonParser
            .parseString(json)
            .asJsonObject

        val legacyGui = root
            .get("gui")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject

        GuiConfigManager.load(legacyGui)

        var configChanged =
            root.remove("gui") != null

        if (applyConfigMigrations(root)) {
            configChanged = true
        }

        val existingConfig = gson.fromJson(
            root,
            Config::class.java
        )

        val defaultConfig = createDefaultConfig()

        val updatedConfig = mergeConfigs(
            existingConfig,
            defaultConfig
        )

        config = updatedConfig
        MenuConfigManager.loadAll(updatedConfig.economy)

        if (updatedConfig.databaseSave) {

            val dbConfig = updatedConfig.database

            if (!DatabaseManager.initialize(dbConfig)) {

                println(
                    "Failed to initialize database. Falling back to local save."
                )

                updatedConfig.databaseSave = false
                updatedConfig.localSave = true

            } else if (
                existingConfig.localSave &&
                updatedConfig.databaseSave
            ) {

                migrateBalancesToDatabase()
            }

        } else if (
            updatedConfig.localSave &&
            existingConfig.databaseSave
        ) {

            migrateBalancesToLocal()
        }

        if (
            configChanged ||
            updatedConfig != existingConfig
        ) {

            saveUpdatedConfig(updatedConfig)
        }

        return config
    }

    private fun applyConfigMigrations(root: JsonObject): Boolean {
        var changed = false

        val numberFormat = if (root.has("numberFormat") && root.get("numberFormat").isJsonObject) {
            root.getAsJsonObject("numberFormat")
        } else {
            JsonObject().also {
                root.add("numberFormat", it)
                changed = true
            }
        }

        if (!numberFormat.has("groupingSeparator")) {
            numberFormat.addProperty("groupingSeparator", ".")
            changed = true
        }
        if (!numberFormat.has("decimalSeparator")) {
            numberFormat.addProperty("decimalSeparator", ",")
            changed = true
        }
        if (!numberFormat.has("useGrouping")) {
            numberFormat.addProperty("useGrouping", true)
            changed = true
        }
        if (!numberFormat.has("maxDecimalPlaces")) {
            numberFormat.addProperty("maxDecimalPlaces", 2)
            changed = true
        }

        root.get("economy")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.forEach { element ->
                if (!element.isJsonObject) return@forEach

                val economy = element.asJsonObject

                if (!economy.has("balance")) {
                    economy.addProperty("balance", true)
                    changed = true
                }

                if (!economy.has("top")) {
                    economy.addProperty("top", true)
                    changed = true
                }

                if (!economy.has("showInGlobalBalance")) {
                    economy.addProperty("showInGlobalBalance", true)
                    changed = true
                }

                if (!economy.has("showInGlobalTop")) {
                    economy.addProperty(
                        "showInGlobalTop",
                        economy.get("top").asBoolean
                    )
                    changed = true
                }

                val customCommands = if (
                    economy.has("customCommands") &&
                    economy.get("customCommands").isJsonObject
                ) {
                    economy.getAsJsonObject("customCommands")
                } else {
                    JsonObject().also {
                        economy.add("customCommands", it)
                        changed = true
                    }
                }

                if (!customCommands.has("enabled")) {
                    customCommands.addProperty("enabled", false)
                    changed = true
                }
                if (!customCommands.has("aliases")) {
                    customCommands.add("aliases", gson.toJsonTree(emptyList<String>()))
                    changed = true
                }
                if (!customCommands.has("defaultAction")) {
                    customCommands.addProperty("defaultAction", "balance")
                    changed = true
                }
                if (!customCommands.has("balance")) {
                    customCommands.addProperty("balance", true)
                    changed = true
                }
                if (!customCommands.has("top")) {
                    customCommands.addProperty("top", true)
                    changed = true
                }
                val subcommands = if (
                    customCommands.has("subcommands") &&
                    customCommands.get("subcommands").isJsonObject
                ) {
                    customCommands.getAsJsonObject("subcommands")
                } else {
                    JsonObject().also {
                        customCommands.add("subcommands", it)
                        changed = true
                    }
                }

                if (!subcommands.has("balance")) {
                    subcommands.add("balance", gson.toJsonTree(listOf("balance")))
                    changed = true
                }

                if (!subcommands.has("top")) {
                    subcommands.add("top", gson.toJsonTree(listOf("top")))
                    changed = true
                }
                val permissions = if (
                    customCommands.has("permissions") &&
                    customCommands.get("permissions").isJsonObject
                ) {
                    customCommands.getAsJsonObject("permissions")
                } else {
                    JsonObject().also {
                        customCommands.add("permissions", it)
                        changed = true
                    }
                }
                if (!permissions.has("command")) {
                    permissions.addProperty("command", "beconomy.custom.%currencyType%")
                    changed = true
                }
                if (!permissions.has("balance")) {
                    permissions.addProperty("balance", "beconomy.custom.%currencyType%.balance")
                    changed = true
                }
                if (!permissions.has("balanceOthers")) {
                    permissions.addProperty("balanceOthers", "beconomy.custom.%currencyType%.balance.others")
                    changed = true
                }
                if (!permissions.has("top")) {
                    permissions.addProperty("top", "beconomy.custom.%currencyType%.top")
                    changed = true
                }
                val pay = if (
                    economy.has("pay") &&
                    economy.get("pay").isJsonObject
                ) {
                    economy.getAsJsonObject("pay")
                } else {
                    JsonObject().also {
                        economy.add("pay", it)
                        changed = true
                    }
                }
                if (!pay.has("minimumAmount")) {
                    pay.addProperty("minimumAmount", 0)
                    changed = true
                }
                if (!pay.has("maximumAmount")) {
                    pay.addProperty("maximumAmount", -1)
                    changed = true
                }
            }

        return changed
    }

    private fun createDefaultConfig(): Config {
        return Config(
            commands = listOf("beco", "eco"),
            balanceCommands = listOf("bal", "balance"),
            numberFormat = NumberFormatConfig(
                groupingSeparator = ".",
                decimalSeparator = ",",
                useGrouping = true,
                maxDecimalPlaces = 2
            ),
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

    private fun mergeConfigs(
        existingConfig: Config,
        defaultConfig: Config
    ): Config {
        return defaultConfig.copy(
            commands = existingConfig.commands.ifEmpty {
                defaultConfig.commands
            },
            balanceCommands = existingConfig.balanceCommands.ifEmpty {
                defaultConfig.balanceCommands
            },
            numberFormat = existingConfig.numberFormat,
            economy = existingConfig.economy.ifEmpty {
                defaultConfig.economy
            },
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
        fun format(value: BigDecimal): String {
            val cfg = config.numberFormat
            val groupingSeparator = cfg.groupingSeparator.firstOrNull() ?: '.'
            val decimalSeparator = cfg.decimalSeparator.firstOrNull() ?: ','
            val maxDecimals = cfg.maxDecimalPlaces.coerceIn(0, 10)

            val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
                this.groupingSeparator = groupingSeparator
                this.decimalSeparator = decimalSeparator
            }

            val pattern = buildString {
                append(if (cfg.useGrouping) "#,##0" else "0")
                if (maxDecimals > 0) {
                    append(".")
                    repeat(maxDecimals) { append("#") }
                }
            }

            return DecimalFormat(pattern, symbols).apply {
                isGroupingUsed = cfg.useGrouping
                maximumFractionDigits = maxDecimals
            }.format(value)
        }
    }
}