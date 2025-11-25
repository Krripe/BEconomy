package org.krripe.beconomy.api

import com.google.gson.JsonParser
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.math.BigDecimal
import java.util.*
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.text.Text
import org.krripe.beconomy.config.ConfigManager
import org.krripe.beconomy.database.DatabaseManager
import java.io.File

data class Transaction(val playerId: UUID, val currencyType: String, val amount: BigDecimal, val type: String, val timestamp: Long)

interface EconomyEventListener {
    fun onBalanceChanged(playerId: UUID, currencyType: String, newBalance: BigDecimal)
    fun onCurrencyAdded(currencyConfig: ConfigManager.EconomyConfig)
    fun onCurrencyRemoved(currencyType: String)
    fun onCurrencyModified(currencyConfig: ConfigManager.EconomyConfig)
}

class BlanketEconomyAPI(override val server: MinecraftServer) : EconomyAPI {
    private val listeners = mutableListOf<EconomyEventListener>()
    private val transactionHistory = mutableMapOf<UUID, MutableList<Transaction>>()

    override fun getBalance(playerId: UUID, currencyType: String): BigDecimal {
        val actualCurrencyType = if (currencyType.isBlank() || !currencyExists(currencyType)) {
            ConfigManager.getPrimaryCurrency()?.currencyType ?: "default_currency"
        } else {
            currencyType
        }

        return ConfigManager.getBalance(playerId, actualCurrencyType)
    }

    override fun getCurrencyList(): List<ConfigManager.EconomyConfig> {
        return ConfigManager.config.economy
    }

    override fun currencyExists(currencyType: String): Boolean {
        return BEconomy.getAPI().getCurrencyList().any { it.currencyType == currencyType }
    }

    override fun setBalance(playerId: UUID, balance: BigDecimal, currencyType: String) {
        val actualCurrencyType = if (currencyType.isBlank() || !currencyExists(currencyType)) {
            ConfigManager.getPrimaryCurrency()?.currencyType ?: "default_currency"
        } else {
            currencyType
        }

        ConfigManager.setBalance(playerId, balance, actualCurrencyType)

        listeners.forEach { it.onBalanceChanged(playerId, actualCurrencyType, balance) }
    }

    override fun addBalance(playerId: UUID, amount: BigDecimal, currencyType: String) {
        val currentBalance = getBalance(playerId, currencyType)
        setBalance(playerId, currentBalance.add(amount), currencyType)
        logTransaction(playerId, currencyType, amount, "credit")
    }

    override fun subtractBalance(playerId: UUID, amount: BigDecimal, currencyType: String): Boolean {
        val currentBalance = getBalance(playerId, currencyType)
        return if (currentBalance >= amount) {
            setBalance(playerId, currentBalance.subtract(amount), currencyType)
            logTransaction(playerId, currencyType, amount, "debit")
            true
        } else {
            false
        }
    }

    override fun transfer(fromPlayerId: UUID, toPlayerId: UUID, amount: BigDecimal, currencyType: String): Boolean {
        return if (subtractBalance(fromPlayerId, amount, currencyType)) {
            addBalance(toPlayerId, amount, currencyType)
            logTransaction(fromPlayerId, currencyType, amount, "transfer_out")
            logTransaction(toPlayerId, currencyType, amount, "transfer_in")
            true
        } else {
            false
        }
    }

    override fun addEventListener(listener: EconomyEventListener) {
        listeners.add(listener)
    }

    override fun removeEventListener(listener: EconomyEventListener) {
        listeners.remove(listener)
    }

    override fun conditionalTransaction(playerId: UUID, amount: BigDecimal, currencyType: String, condition: (BigDecimal) -> Boolean): Boolean {
        val balance = getBalance(playerId, currencyType)
        return if (condition(balance)) {
            subtractBalance(playerId, amount, currencyType)
        } else {
            false
        }
    }

    override fun logTransaction(playerId: UUID, currencyType: String, amount: BigDecimal, type: String) {
        val transactions = transactionHistory.getOrPut(playerId) { mutableListOf() }
        transactions.add(Transaction(playerId, currencyType, amount, type, System.currentTimeMillis()))
    }

    override fun getTransactionHistory(playerId: UUID): List<Transaction> {
        return transactionHistory[playerId] ?: emptyList()
    }

    override fun createCurrency(
        name: String,
        lore: String,
        material: String,
        customModelData: Int,
        balanceStart: BigDecimal,
        symbol: String,
        isPrimary: Boolean
    ): Boolean {
        val loreList = lore.split("\n")

        val newCurrency = ConfigManager.EconomyConfig(
            name = name,
            lore = loreList,
            material = material,
            custommodeldata = customModelData,
            currencyType = name.lowercase(Locale.getDefault()).replace(" ", "_"),
            balanceStart = balanceStart,
            symbol = symbol,
            isPrimary = isPrimary
        )
        ConfigManager.config.economy.add(newCurrency)

        if (isPrimary) {
            ConfigManager.config.economy.forEach {
                if (it.currencyType != newCurrency.currencyType) {
                    it.isPrimary = false
                }
            }
        }

        ConfigManager.saveConfig()
        return true
    }

    override fun hasEnoughFunds(playerId: UUID, amount: BigDecimal, currencyType: String): Boolean {
        return getBalance(playerId, currencyType) >= amount
    }

    override fun manageInventory(playerId: UUID, itemStack: ItemStack, action: InventoryAction): Boolean {
        val player = getPlayerById(playerId)
        if (player != null) {
            when (action) {
                InventoryAction.ADD -> player.inventory.offerOrDrop(itemStack)
                InventoryAction.REMOVE -> player.inventory.removeStack(player.inventory.getSlotWithStack(itemStack))
            }
            return true
        }
        return false
    }

    override fun processBatchTransactions(transactions: List<Transaction>): Boolean {
        val successfulTransactions = mutableListOf<Transaction>()
        try {
            transactions.forEach { transaction ->
                if (!subtractBalance(transaction.playerId, transaction.amount, transaction.currencyType)) {
                    throw IllegalStateException("Failed to process transaction for ${transaction.playerId}")
                }
                successfulTransactions.add(transaction)
            }
            return true
        } catch (_: Exception) {
            successfulTransactions.forEach { transaction ->
                addBalance(transaction.playerId, transaction.amount, transaction.currencyType)
            }
            return false
        }
    }

    override fun addCurrencyItem(playerId: UUID, itemStack: ItemStack, amount: Int): Boolean {
        val player = getPlayerById(playerId) ?: return false
        val currencyItem = itemStack.copy()
        currencyItem.count = amount

        return manageInventory(playerId, currencyItem, InventoryAction.ADD)
    }

    override fun removeCurrencyItem(playerId: UUID, itemStack: ItemStack, amount: Int): Boolean {
        val player = getPlayerById(playerId) ?: return false
        val currentBalance = getCurrencyItemBalance(playerId, itemStack)

        if (currentBalance < amount) {
            return false
        }

        val currencyItem = itemStack.copy()
        currencyItem.count = amount

        return manageInventory(playerId, currencyItem, InventoryAction.REMOVE)
    }

    override fun transferCurrencyItem(fromPlayerId: UUID, toPlayerId: UUID, itemStack: ItemStack, amount: Int): Boolean {
        val sender = getPlayerById(fromPlayerId) ?: return false
        val receiver = getPlayerById(toPlayerId) ?: return false

        if (removeCurrencyItem(fromPlayerId, itemStack, amount)) {
            addCurrencyItem(toPlayerId, itemStack, amount)
            return true
        }
        return false
    }

    override fun getCurrencyItemBalance(playerId: UUID, itemStack: ItemStack): Int {
        val player = getPlayerById(playerId) ?: return 0
        var totalCount = 0

        player.inventory.main.forEach { stack ->
            if (stack.item == itemStack.item && itemStackHasNbt(stack) && getItemStackNbt(stack) == getItemStackNbt(itemStack)) {
                totalCount += stack.count
            }
        }
        return totalCount
    }

    override fun hasEnoughCurrencyItems(playerId: UUID, itemStack: ItemStack, amount: Int): Boolean {
        val currentBalance = getCurrencyItemBalance(playerId, itemStack)
        return currentBalance >= amount
    }

    private fun getPlayerById(playerId: UUID): ServerPlayerEntity? {
        return server.playerManager.getPlayer(playerId)
    }

    override fun getCurrencyItemType(itemStack: ItemStack): String? {
        ConfigManager.config.economy.forEach { currencyConfig ->
            val item = Registries.ITEM.get(Identifier.of(currencyConfig.material))
            if (itemStack.item == item && itemStackHasNbt(itemStack)) {
                if (itemStack.get(DataComponentTypes.CUSTOM_MODEL_DATA)?.value == currencyConfig.custommodeldata) {
                    return currencyConfig.currencyType
                }
            }
        }
        return null
    }

    override fun getPrimaryCurrency(): ConfigManager.EconomyConfig? {
        return ConfigManager.getPrimaryCurrency()
    }

    override fun getCurrencyTypeOrFallback(currencyType: String): String {
        return ConfigManager.getCurrencyTypeOrFallback(currencyType)
    }

    override fun getCurrencySymbol(currencyType: String): String {
        return ConfigManager.getCurrencySymbol(currencyType)
    }

    private fun getItemStackNbt(itemStack: ItemStack): Pair<Text?, LoreComponent?> {
        return Pair(itemStack.get(DataComponentTypes.CUSTOM_NAME), itemStack.get(DataComponentTypes.LORE))
    }

    private fun itemStackHasNbt(itemStack: ItemStack): Boolean {
        val customName = itemStack.get(DataComponentTypes.CUSTOM_NAME)
        val lore = itemStack.get(DataComponentTypes.LORE)

        return customName != null || lore != LoreComponent(listOf())
    }

    override fun modifyCurrency(currencyType: String, updatedConfig: ConfigManager.EconomyConfig): Boolean {
        val existingCurrency = ConfigManager.config.economy.find { it.currencyType == currencyType }
        if (existingCurrency != null) {
            ConfigManager.config.economy.remove(existingCurrency)
            ConfigManager.config.economy.add(updatedConfig)
            ConfigManager.saveConfig()

            listeners.forEach { it.onCurrencyModified(updatedConfig) }
            return true
        }
        return false
    }

    override fun deleteCurrency(currencyType: String): Boolean {
        val currency = ConfigManager.config.economy.find { it.currencyType == currencyType }
        if (currency != null) {
            ConfigManager.config.economy.remove(currency)
            ConfigManager.saveConfig()

            listeners.forEach { it.onCurrencyRemoved(currencyType) }
            return true
        }
        return false
    }

    override fun migrateData(toDatabase: Boolean): Boolean {
        return try {
            if (toDatabase) {
                ConfigManager.migrateBalancesToDatabase()
            } else {
                ConfigManager.migrateBalancesToLocal()
            }
            true
        } catch (e: Exception) {
            println("Data migration failed: ${e.message}")
            false
        }
    }

    override fun resetBalance(playerId: UUID, currencyType: String): Boolean {
        val currencyConfig = ConfigManager.config.economy.find { it.currencyType == currencyType } ?: return false
        setBalance(playerId, currencyConfig.balanceStart, currencyType)
        return true
    }

    override fun updateBalance(playerId: UUID, currencyType: String, newBalance: BigDecimal) {
        setBalance(playerId, newBalance, currencyType)
    }

    override fun increaseBalance(playerId: UUID, currencyType: String, amount: BigDecimal) {
        addBalance(playerId, amount, currencyType)
    }

    override fun decreaseBalance(playerId: UUID, currencyType: String, amount: BigDecimal): Boolean {
        return subtractBalance(playerId, amount, currencyType)
    }

    override fun syncBalanceToPlayer(playerId: UUID, currencyType: String) {
        val player = getPlayerById(playerId) ?: return
        val balance = getBalance(playerId, currencyType)
        listeners.forEach { it.onBalanceChanged(playerId, currencyType, balance) }
    }

    override fun modifyBalanceSafely(playerId: UUID, currencyType: String, modifier: (BigDecimal) -> BigDecimal) {
        val current = getBalance(playerId, currencyType)
        val updated = modifier(current)
        setBalance(playerId, updated, currencyType)
    }

    override fun resetBalanceToDefault(playerId: UUID, currencyType: String) {
        val default = ConfigManager.config.economy.find { it.currencyType == currencyType }?.balanceStart ?: BigDecimal.ZERO
        setBalance(playerId, default, currencyType)
    }

    override fun getAllPlayerBalances(playerId: UUID): Map<String, BigDecimal> {
        val result = mutableMapOf<String, BigDecimal>()
        getCurrencyList().forEach {
            result[it.currencyType] = getBalance(playerId, it.currencyType)
        }
        return result
    }

    override fun getTopBalances(currencyType: String, limit: Int): List<EconomyAPI.PlayerBalance> {
        return if (ConfigManager.config.databaseSave) {
            DatabaseManager.getAdapter()?.getAllBalances(currencyType)
                ?.map { (uuid, balance) ->
                    val name = server.userCache?.getByUuid(uuid)?.orElse(null)?.name ?: uuid.toString()
                    EconomyAPI.PlayerBalance(uuid, name, balance)
                }?.sortedByDescending { it.balance }
                ?.take(limit) ?: emptyList()
        } else {
            val folder = File("config/beconomy/user")
            if (!folder.exists() || !folder.isDirectory) return emptyList()

            folder.listFiles { file -> file.extension == "json" }?.mapNotNull { file ->
                val uuid = try {
                    UUID.fromString(file.nameWithoutExtension)
                } catch (_: Exception) {
                    return@mapNotNull null
                }

                val json = JsonParser.parseString(file.readText()).asJsonObject
                val balance = json.get(currencyType)?.asBigDecimal ?: BigDecimal.ZERO
                val name = server.userCache?.getByUuid(uuid)?.orElse(null)?.name ?: uuid.toString()
                EconomyAPI.PlayerBalance(uuid, name, balance)
            }?.sortedByDescending { it.balance }
                ?.take(limit) ?: emptyList()
        }
    }
}