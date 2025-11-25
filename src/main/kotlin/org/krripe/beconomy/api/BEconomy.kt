package org.krripe.beconomy.api

import net.minecraft.server.MinecraftServer
import org.krripe.beconomy.config.ConfigManager
import java.math.BigDecimal
import java.util.UUID

object BEconomy {
    private var api: BlanketEconomyAPI? = null
    private var serverInstance: MinecraftServer? = null

    /**
     * Initializes the API with the provided Minecraft server instance.
     * This is done automatically when `getAPI` is called for the first time.
     */
    fun initialize(server: MinecraftServer) {
        if (api == null) {
            api = BlanketEconomyAPI(server)
            serverInstance = server

            api?.addEventListener(object : EconomyEventListener {
                override fun onCurrencyAdded(currencyConfig: ConfigManager.EconomyConfig) {
                    println("[BEconomy] Currency added: ${currencyConfig.currencyType}")
                }

                override fun onCurrencyRemoved(currencyType: String) {
                    println("[BEconomy] Currency removed: $currencyType")
                }

                override fun onCurrencyModified(currencyConfig: ConfigManager.EconomyConfig) {
                    println("[BEconomy] Currency modified: ${currencyConfig.currencyType}")
                }

                override fun onBalanceChanged(playerId: UUID, currencyType: String, newBalance: BigDecimal) {
                    println("[BEconomy] Balance changed for player $playerId in currency $currencyType: New balance is $newBalance")
                }
            })

            println("[BEconomy] API initialized successfully!")
        }
    }

    /**
     * Checks if the API is initialized.
     * @return True if initialized, false otherwise.
     */
    fun isInitialized(): Boolean = api != null

    /**
     * Provides the API instance, initializing it if necessary.
     * @return The initialized EconomyAPI instance.
     */
    fun getAPI(): EconomyAPI {
        if (api == null) {
            val server = serverInstance
                ?: throw IllegalStateException("[BEconomy] Server instance is not set. Ensure the server is initialized before accessing the API.")
            initialize(server)
        }
        return api ?: throw IllegalStateException("[BEconomy] Failed to initialize BEconomy API!")
    }

    /**
     * Sets the server instance. This should be called during server startup.
     * @param server The Minecraft server instance.
     */
    fun setServer(server: MinecraftServer) {
        if (serverInstance == null) {
            serverInstance = server
        }
    }
}
