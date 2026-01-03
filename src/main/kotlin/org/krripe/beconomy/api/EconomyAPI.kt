package org.krripe.beconomy.api

import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import org.krripe.beconomy.config.ConfigManager
import java.math.BigDecimal
import java.util.UUID

interface EconomyAPI {

    data class PlayerBalance(
        val uuid: UUID,
        val name: String,
        val balance: BigDecimal
    )

    /**
     * The instance of the Minecraft server this API is operating on.
     */
    val server: MinecraftServer

    /**
     * Retrieves the balance of a player for a specific currency type.
     * @param playerId The UUID of the player.
     * @param currencyType The type of currency to check.
     * @return The balance as a BigDecimal.
     */
    fun getBalance(playerId: UUID, currencyType: String): BigDecimal

    /**
     * Sets the balance of a player for a specific currency type.
     * @param playerId The UUID of the player.
     * @param balance The new balance to set.
     * @param currencyType The type of currency to set the balance for.
     */
    fun setBalance(playerId: UUID, balance: BigDecimal, currencyType: String)

    /**
     * Adds an amount to a player's balance for a specific currency type.
     * @param playerId The UUID of the player.
     * @param amount The amount to add.
     * @param currencyType The type of currency to add to.
     */
    fun addBalance(playerId: UUID, amount: BigDecimal, currencyType: String)

    /**
     * Subtracts an amount from a player's balance for a specific currency type.
     * @param playerId The UUID of the player.
     * @param amount The amount to subtract.
     * @param currencyType The type of currency to subtract from.
     * @return True if the operation was successful, false if the player has insufficient funds.
     */
    fun subtractBalance(playerId: UUID, amount: BigDecimal, currencyType: String): Boolean

    /**
     * Transfers an amount of currency from one player to another.
     * @param fromPlayerId The UUID of the player sending the currency.
     * @param toPlayerId The UUID of the player receiving the currency.
     * @param amount The amount to transfer.
     * @param currencyType The type of currency to transfer.
     * @return True if the transfer was successful, false otherwise.
     */
    fun transfer(fromPlayerId: UUID, toPlayerId: UUID, amount: BigDecimal, currencyType: String): Boolean

    /**
     * Registers an event listener for economy-related events.
     * @param listener The event listener to add.
     */
    fun addEventListener(listener: EconomyEventListener)

    /**
     * Unregisters an event listener for economy-related events.
     * @param listener The event listener to remove.
     */
    fun removeEventListener(listener: EconomyEventListener)

    /**
     * Performs a transaction only if a specified condition is met.
     * @param playerId The UUID of the player.
     * @param amount The amount for the transaction.
     * @param currencyType The type of currency to transact.
     * @param condition A lambda function that defines the condition.
     * @return True if the transaction is completed, false otherwise.
     */
    fun conditionalTransaction(playerId: UUID, amount: BigDecimal, currencyType: String, condition: (BigDecimal) -> Boolean): Boolean

    /**
     * Logs a transaction for a player in a specific currency type.
     * @param playerId The UUID of the player.
     * @param currencyType The type of currency.
     * @param amount The amount involved in the transaction.
     * @param type The type of transaction (e.g., "credit", "debit").
     */
    fun logTransaction(playerId: UUID, currencyType: String, amount: BigDecimal, type: String)

    /**
     * Retrieves the transaction history for a player.
     * @param playerId The UUID of the player.
     * @return A list of transactions made by the player.
     */
    fun getTransactionHistory(playerId: UUID): List<Transaction>

    /**
     * Creates a new currency with specific properties.
     * @param name The name of the currency.
     * @param lore The lore (description) of the currency.
     * @param material The material type of the currency item.
     * @param customModelData The custom model data ID for the item.
     * @param balanceStart The starting balance for this currency.
     * @return True if the currency was created successfully, false otherwise.
     */
    fun createCurrency(name: String, lore: String, material: String, customModelData: Int, balanceStart: BigDecimal, symbol: String, isPrimary: Boolean): Boolean

    /**
     * Checks if a player has enough funds for a transaction.
     * @param playerId The UUID of the player.
     * @param amount The amount to check.
     * @param currencyType The type of currency.
     * @return True if the player has enough funds, false otherwise.
     */
    fun hasEnoughFunds(playerId: UUID, amount: BigDecimal, currencyType: String): Boolean

    /**
     * Manages a player's inventory by adding or removing a specified item.
     * @param playerId The UUID of the player.
     * @param itemStack The item stack to add or remove.
     * @param action The action to perform (ADD or REMOVE).
     * @return True if the action was successful, false otherwise.
     */
    fun manageInventory(playerId: UUID, itemStack: ItemStack, action: InventoryAction): Boolean

    /**
     * Processes a batch of transactions.
     * @param transactions A list of transactions to process.
     * @return True if all transactions were processed successfully, false otherwise.
     */
    fun processBatchTransactions(transactions: List<Transaction>): Boolean

    /**
     * Adds a specified amount of currency items to a player's inventory.
     * @param playerId The UUID of the player.
     * @param itemStack The currency item stack.
     * @param amount The amount to add.
     * @return True if the currency items were added successfully, false otherwise.
     */
    fun addCurrencyItem(playerId: UUID, itemStack: ItemStack, amount: Int): Boolean

    /**
     * Removes a specified amount of currency items from a player's inventory.
     * @param playerId The UUID of the player.
     * @param itemStack The currency item stack.
     * @param amount The amount to remove.
     * @return True if the currency items were removed successfully, false otherwise.
     */
    fun removeCurrencyItem(playerId: UUID, itemStack: ItemStack, amount: Int): Boolean

    /**
     * Transfers a specified amount of currency items from one player to another.
     * @param fromPlayerId The UUID of the player sending the currency items.
     * @param toPlayerId The UUID of the player receiving the currency items.
     * @param itemStack The currency item stack.
     * @param amount The amount to transfer.
     * @return True if the transfer was successful, false otherwise.
     */
    fun transferCurrencyItem(fromPlayerId: UUID, toPlayerId: UUID, itemStack: ItemStack, amount: Int): Boolean

    /**
     * Gets the balance of a specific currency item in a player's inventory.
     * @param playerId The UUID of the player.
     * @param itemStack The currency item stack to check.
     * @return The total amount of the currency item in the player's inventory.
     */
    fun getCurrencyItemBalance(playerId: UUID, itemStack: ItemStack): Int

    /**
     * Checks if a player has enough of a specific currency item.
     * @param playerId The UUID of the player.
     * @param itemStack The currency item stack to check.
     * @param amount The amount required.
     * @return True if the player has enough currency items, false otherwise.
     */
    fun hasEnoughCurrencyItems(playerId: UUID, itemStack: ItemStack, amount: Int): Boolean

    /**
     * Gets the type of a currency item based on its properties.
     * @param itemStack The currency item stack.
     * @return The currency type as a string, or null if it is not a currency item.
     */
    fun getCurrencyItemType(itemStack: ItemStack): String?

    /**
     * Retrieves the primary currency from the configuration.
     * @return The primary currency as EconomyConfig.
     */
    fun getPrimaryCurrency(): ConfigManager.EconomyConfig?

    /**
     * Returns the valid currency type or falls back to the primary currency if the type is incorrect.
     * @param currencyType The requested currency type.
     * @return The valid currency type or the primary one.
     */
    fun getCurrencyTypeOrFallback(currencyType: String): String

    /**
     * Retrieves the symbol for a specific currency type.
     * @param currencyType The type of currency.
     * @return The symbol of the currency as a string, or empty string if not found.
     */
    fun getCurrencySymbol(currencyType: String): String

    /**
     * Retrieves the list of all configured currencies.
     *
     * @return A list of `EconomyConfig` representing all available currencies.
     */
    fun getCurrencyList(): List<ConfigManager.EconomyConfig>

    /**
     * Checks if a currency with the specified type exists.
     *
     * @param currencyType The type of currency to check.
     * @return True if the currency exists, false otherwise.
     */
    fun currencyExists(currencyType: String): Boolean

    /**
     * Changes the properties of an existing currency.
     * @param currencyType The type of currency to change.
     * @param updatedConfig The new currency config.
     * @return True if the change was successful, otherwise False.
     */
    fun modifyCurrency(currencyType: String, updatedConfig: ConfigManager.EconomyConfig): Boolean

    /**
     * Delete an existing currency.
     * @param currencyType The type of currency to delete.
     * @return True if the deletion was successful, otherwise False.
     */
    fun deleteCurrency(currencyType: String): Boolean

    /**
     * Migrate all user data between local save and database.
     * @param toDatabase True if migration to database is desired, False otherwise.
     * @return True if the migration was successful.
     */
    fun migrateData(toDatabase: Boolean): Boolean

    /**
     * Resets a user's balance to its starting value.
     * @param playerId the UUID of the user.
     * @param currencyType The currency type.
     * @return True if the reset was successful.
     */
    fun resetBalance(playerId: UUID, currencyType: String): Boolean

    /**
     * Sets the player's balance to a specific amount.
     *
     * This method should be used instead of directly calling `BEconfig.setBalance(...)`,
     * as it also triggers the `onBalanceChanged` event and notifies all registered listeners.
     *
     * @param playerId The UUID of the player whose balance is being updated.
     * @param currencyType The currency type to modify.
     * @param newBalance The exact value to set as the new balance.
     */
    fun updateBalance(playerId: UUID, currencyType: String, newBalance: BigDecimal)

    /**
     * Adds a specified amount to the player's balance.
     *
     * This method updates the balance, triggers the `onBalanceChanged` event,
     * and logs the transaction as a credit. Always use this instead of modifying the balance manually.
     *
     * @param playerId The UUID of the player.
     * @param currencyType The currency to increase.
     * @param amount The amount to add to the current balance.
     */
    fun increaseBalance(playerId: UUID, currencyType: String, amount: BigDecimal)

    /**
     * Subtracts a specified amount from the player's balance.
     *
     * This method checks for sufficient funds before subtracting, triggers the `onBalanceChanged` event,
     * and logs the transaction as a debit. Returns false if the player has insufficient funds.
     *
     * @param playerId The UUID of the player.
     * @param currencyType The currency to subtract from.
     * @param amount The amount to subtract.
     * @return True if the subtraction was successful, false otherwise.
     */
    fun decreaseBalance(playerId: UUID, currencyType: String, amount: BigDecimal): Boolean

    /**
     * Forces a balance sync to the player using packets.
     *
     * This is useful when an external system (like a scoreboard or UI) needs
     * to refresh the display without changing the balance.
     *
     * @param playerId The UUID of the player.
     * @param currencyType The currency type to sync.
     */
    fun syncBalanceToPlayer(playerId: UUID, currencyType: String)

    /**
     * Applies a modification to the player's balance using a safe lambda.
     * Automatically handles balance update and events.
     *
     * Example usage:
     * modifyBalanceSafely(uuid, "pokedollars") { it.multiply(BigDecimal("1.05")) }
     *
     * @param playerId The UUID of the player.
     * @param currencyType The currency type.
     * @param modifier A function that receives the current balance and returns the new one.
     */
    fun modifyBalanceSafely(playerId: UUID, currencyType: String, modifier: (BigDecimal) -> BigDecimal)

    /**
     * Resets a player's balance to the default value defined in config for the currency.
     * Triggers the `onBalanceChanged` event.
     *
     * @param playerId The UUID of the player.
     * @param currencyType The currency type to reset.
     */
    fun resetBalanceToDefault(playerId: UUID, currencyType: String)

    /**
     * Retrieves a map of all balances for a specific player across all currencies.
     *
     * @param playerId The UUID of the player.
     * @return Map of currency type to balance.
     */
    fun getAllPlayerBalances(playerId: UUID): Map<String, BigDecimal>

    /**
     * Retrieves the top players based on their balance for a specific currency.
     * This works across all storage types (local file or database).
     *
     * @param currencyType The type of currency to rank by.
     * @param limit The maximum number of top players to return.
     * @return A list of PlayerBalance objects sorted by balance in descending order.
     */
    fun getTopBalances(currencyType: String, limit: Int): List<PlayerBalance>

    /**
     * Calculates the total value of all physical currency items (banknotes/coins)
     * in the player's inventory for the given currency type.
     *
     * The value is computed using the same denomination rules as the `/eco deposit`
     * command (configured denominations + base item).
     *
     * Note: This only works for online players, since it needs access to the
     * live inventory. If the player is offline or not found, this should
     * return `BigDecimal.ZERO`.
     *
     * @param playerId The UUID of the player.
     * @param currencyType The currency type to scan the inventory for.
     * @return The total value of all matching currency items as a BigDecimal.
     */
    fun getInventoryValue(playerId: UUID, currencyType: String): BigDecimal

    /**
     * Returns the "effective" balance of the player for the given currency:
     * the sum of the virtual balance stored in BEconomy and the value of all
     * matching currency items in the player's inventory.
     *
     * This is intended for shop GUIs or other systems that want to display or
     * check against the total amount of money the player actually has available.
     *
     * @param playerId The UUID of the player.
     * @param currencyType The currency type to calculate for.
     * @return Virtual balance + inventory value as a BigDecimal.
     */
    fun getEffectiveBalance(playerId: UUID, currencyType: String): BigDecimal

    /**
     * Checks if the player has enough total funds for a extract when combining
     * both their virtual balance and the value of all currency items in their
     * inventory.
     *
     * @param playerId The UUID of the player.
     * @param amount The required amount.
     * @param currencyType The currency type to check.
     * @return True if the player has enough effective funds, false otherwise.
     */
    fun hasEnoughEffectiveFunds(playerId: UUID, amount: BigDecimal, currencyType: String): Boolean

    /**
     * Attempts to subtract an amount from the player using both their virtual
     * balance and their physical currency items.
     *
     * Behaviour:
     *  - If (virtual balance + inventory value) is LESS than `amount`, the method
     *    returns false and DOES NOT modify the balance or inventory.
     *  - If there are enough total funds:
     *      1. All matching currency items for this currency type are converted
     *         into virtual balance (they are removed from the inventory and their
     *         total value is added to the balance).
     *      2. The final payment is performed using `subtractBalance(...)`.
     *
     * This makes it easy for external systems (shops, etc.) to charge players
     * while automatically consuming banknotes as part of the payment.
     *
     * @param playerId The UUID of the player.
     * @param amount The amount to subtract.
     * @param currencyType The currency type to use.
     * @return True if the operation succeeded, false otherwise.
     */
    fun subtractUsingBalanceAndInventory(playerId: UUID, amount: BigDecimal, currencyType: String): Boolean
}