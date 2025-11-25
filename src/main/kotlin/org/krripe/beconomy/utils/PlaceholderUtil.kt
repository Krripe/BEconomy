package org.krripe.beconomy.utils

import eu.pb4.placeholders.api.Placeholders
import eu.pb4.placeholders.api.PlaceholderContext
import eu.pb4.placeholders.api.PlaceholderResult
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.krripe.beconomy.config.ConfigManager
import org.krripe.beconomy.database.DatabaseManager
import java.math.BigDecimal
import java.util.*

object PlaceholderUtil {

    fun registerPlaceholders() {
        val config = ConfigManager.config

        config.economy.forEach { currency ->
            val currencyName = formatCurrencyName(currency.currencyType)

            Placeholders.register(Identifier.of("beco", "${currencyName}_name")) { _, _ ->
                PlaceholderResult.value(Text.literal(stripColorCodes(currency.name)))
            }

            Placeholders.register(Identifier.of("beco", "${currencyName}_symbol")) { _, _ ->
                PlaceholderResult.value(Text.literal(currency.symbol))
            }

            Placeholders.register(Identifier.of("beco", "${currencyName}_balance")) { ctx, _ ->
                getPlayerBalance(ctx, currency.currencyType)
            }
        }

        Placeholders.register(Identifier.of("beco", "primary_currency")) { ctx, _ ->
            getPrimaryCurrencyBalance(ctx)
        }

        Placeholders.register(Identifier.of("beco", "player_name")) { ctx, _ ->
            getPlayerName(ctx)
        }
    }

    private fun getPlayerBalance(ctx: PlaceholderContext, currencyType: String): PlaceholderResult {
        val player = ctx.player ?: return PlaceholderResult.invalid("No player found!")
        val balance = getBalanceForPlayer(player.uuid, currencyType)
        val formatted = ConfigManager.NumberFormatter.format(balance)
        return PlaceholderResult.value(Text.literal(formatted))
    }

    private fun getPrimaryCurrencyBalance(ctx: PlaceholderContext): PlaceholderResult {
        val player = ctx.player ?: return PlaceholderResult.invalid("No player found!")
        val primaryCurrency = ConfigManager.getPrimaryCurrency()
            ?: return PlaceholderResult.invalid("No primary currency configured!")
        val balance = getBalanceForPlayer(player.uuid, primaryCurrency.currencyType)
        val formatted = ConfigManager.NumberFormatter.format(balance)
        return PlaceholderResult.value(Text.literal(formatted))
    }

    private fun getPlayerName(ctx: PlaceholderContext): PlaceholderResult {
        val player = ctx.player ?: return PlaceholderResult.invalid("No player found!")
        return PlaceholderResult.value(Text.literal(player.name.string))
    }

    private fun getBalanceForPlayer(playerId: UUID, currencyType: String): BigDecimal {
        val config = ConfigManager.config
        return when {
            config.localSave -> ConfigManager.getBalance(playerId, currencyType)
            config.databaseSave -> {
                val adapter = DatabaseManager.getAdapter()
                adapter?.getBalance(playerId, currencyType) ?: BigDecimal.ZERO
            }
            else -> BigDecimal.ZERO
        }
    }

    private fun formatCurrencyName(currencyType: String): String {
        return currencyType.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
    }

    private fun stripColorCodes(text: String): String {
        return text.replace(Regex("§[0-9a-fk-or]|&[0-9a-fk-or]"), "")
    }
}
