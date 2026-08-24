package org.krripe.beconomy.integration

import org.krripe.beconomy.api.BEconomy
import org.pokesplash.gts.api.economy.GtsEconomy
import java.util.UUID
import java.math.BigDecimal

class BEconomyGTSBridge : GtsEconomy {

    override fun add(player: UUID, amount: Double): Boolean {
        return try {
            val api = BEconomy.getAPI()
            val currencyType = api.getPrimaryCurrency()?.currencyType ?: return false
            val playerBalance = api.getBalance(player, currencyType)
            api.setBalance(player, playerBalance + BigDecimal.valueOf(amount), currencyType)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun remove(player: UUID, amount: Double): Boolean {
        return try {
            val api = BEconomy.getAPI()
            val currencyType = api.getPrimaryCurrency()?.currencyType ?: return false
            if (!api.hasEnoughFunds(player, BigDecimal.valueOf(amount), currencyType)) return false
            api.subtractBalance(player, BigDecimal.valueOf(amount), currencyType)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun balance(player: UUID): Double {
        return try {
            val api = BEconomy.getAPI()
            val currencyType = api.getPrimaryCurrency()?.currencyType ?: return 0.0
            api.getBalance(player, currencyType).toDouble()
        } catch (e: Exception) {
            e.printStackTrace()
            0.0
        }
    }
}
