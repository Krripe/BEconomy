package org.krripe.beconomy.manager

import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import org.krripe.beconomy.api.BEconomy
import org.krripe.beconomy.config.BarConfigManager
import org.krripe.beconomy.config.ConfigManager
import org.krripe.beconomy.config.ConfigManager.config
import org.krripe.beconomy.utils.CustomColorParser.processAllFormats
import java.math.BigDecimal

object BarManager {

    private var ticks = 0

    internal fun tick(server: MinecraftServer) {
        val barConfig = BarConfigManager.config

        if (!barConfig.enabled) {
            ticks = 0
            return
        }

        val interval = barConfig.updateIntervalTicks.coerceAtLeast(1)

        ticks++

        if (ticks < interval) return

        ticks = 0

        server.playerManager.playerList.forEach {
            updatePlayer(it)
        }
    }

    internal fun updatePlayer(player: ServerPlayerEntity) {
        val barConfig = BarConfigManager.config

        if (!barConfig.enabled) {
            clear(player)
            return
        }

        val entries = barConfig.economies.mapNotNull { entry ->
            if (!entry.enabled) return@mapNotNull null

            val economy = config.economy.find {
                it.currencyType.equals(
                    entry.currencyType,
                    ignoreCase = true
                )
            } ?: return@mapNotNull null

            if (!economy.balance) return@mapNotNull null

            val balance = ConfigManager.getBalance(
                player.uuid,
                economy.currencyType
            )

            if (
                entry.hideWhenZero &&
                balance.compareTo(BigDecimal.ZERO) == 0
            ) {
                return@mapNotNull null
            }

            val api = BEconomy.getAPI()

            val inventoryBalance = api.getInventoryValue(
                player.uuid,
                economy.currencyType
            )

            val effectiveBalance = api.getEffectiveBalance(
                player.uuid,
                economy.currencyType
            )

            val replacements = mapOf(
                "%player%" to player.name.string,
                "%uuid%" to player.uuid.toString(),
                "%currency%" to economy.name,
                "%currencyType%" to economy.currencyType,
                "%symbol%" to economy.symbol,
                "%balance%" to ConfigManager.NumberFormatter.format(balance),
                "%raw_balance%" to balance.toPlainString(),
                "%inventory_balance%" to ConfigManager.NumberFormatter.format(
                    inventoryBalance
                ),
                "%raw_inventory_balance%" to inventoryBalance.toPlainString(),
                "%effective_balance%" to ConfigManager.NumberFormatter.format(
                    effectiveBalance
                ),
                "%raw_effective_balance%" to effectiveBalance.toPlainString()
            )

            val formatted = replacePlaceholders(
                entry.format,
                replacements
            )

            val prefix = replacePlaceholders(
                entry.prefix,
                replacements
            )

            val suffix = replacePlaceholders(
                entry.suffix,
                replacements
            )

            prefix + formatted + suffix
        }

        if (entries.isEmpty()) {
            clear(player)
            return
        }

        val economiesText = entries.joinToString(
            separator = barConfig.separator
        )

        val text = barConfig.layout
            .replace("%economies%", economiesText)
            .replace("%player%", player.name.string)
            .replace("%uuid%", player.uuid.toString())

        player.sendMessage(
            processAllFormats(
                text,
                player
            ),
            true
        )
    }

    internal fun clear(player: ServerPlayerEntity) {
        player.sendMessage(
            Text.empty(),
            true
        )
    }

    private fun replacePlaceholders(
        text: String,
        replacements: Map<String, String>
    ): String {
        return replacements.entries.fold(text) {
                result,
                (placeholder, value) ->
            result.replace(
                placeholder,
                value
            )
        }
    }
}