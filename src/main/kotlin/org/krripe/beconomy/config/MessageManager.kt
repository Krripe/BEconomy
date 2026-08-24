package org.krripe.beconomy.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.minecraft.text.MutableText
import org.krripe.beconomy.utils.CustomColorParser.processAllFormats
import java.io.File

object MessageManager {
    private val file = File("config/beconomy/messages.json")
    private const val PRUNE_UNUSED_KEYS = true

    data class Messages(
        val prefix: String = "&8[<#f2ff59>BEconomy</#>&8] ",
        val paymentSuccess: String = "%prefix% <#95f151>Successfully paid</#> <#f9fdcb>%amount%</#> <#95f151>to</#> <#cbfdef>%player%!</#>",
        val paymentReceived: String = "%prefix% <#95f151>You have received</#> <#f9fdcb>%amount%</#> <#95f151>from</#> <#cbfdef>%player%!</#>",
        val insufficientFunds: String = "%prefix% <#a75948>Insufficient funds!</#>",
        val addBalance: String = "%prefix% <#95f151>Added</#> <#f9fdcb>%amount%</#> <#95f151>to</#> <#cbfdef>%player%!</#>",
        val withdrawSuccess: String = "%prefix% <#95f151>Successfully withdrew</#> <#f9fdcb>%amount%</#> <#d1d1d1>%currencyType%!</#>",
        val withdrawFail: String = "%prefix% <#a75948>Insufficient funds!</#>",
        val removeBalance: String = "%prefix% <#a75948>Removed</#> <#f9fdcb>%amount%</#> <#a75948>from</#> <#cbfdef>%player%!</#>",
        val depositSuccess: String = "%prefix% <#95f151>Successfully deposited</#> <#f9fdcb>%amount%</#> <#d1d1d1>%currencyType%!</#>",
        val depositBlockSet: String = "%prefix% <#95f151>Deposit block set for</#> <#d1d1d1>%currencyType%</#><#95f151>!</#>",
        val depositBlockRemoved: String = "%prefix% <#95f151>Deposit block removed successfully!</#>",
        val depositBlockNotSet: String = "%prefix% <#a75948>The selected block is not a deposit block!</#>",
        val depositBlockNoTarget: String = "%prefix% <#a75948>You must look at a block within 5 blocks!</#>",
        val depositGuiDisabled: String = "%prefix% <#a75948>The Deposit GUI is not available for</#> <#d1d1d1>%currencyType%</#><#a75948>!</#>",
        val noValid: String = "%prefix% <#a75948>Invalid funds!</#>",
        val playerNotFound: String = "%prefix% <#a75948>Player</#> <#cbfdef>%player%</#> <#a75948>not found.</#>",
        val currencyNotFound: String = "%prefix% <#a75948>Currency configuration not found for</#> <#d1d1d1>%currencyType%!</#>",
        val configReloadSuccess: String = "%prefix% <#95f151>Configuration reloaded successfully!</#>",
        val balanceHeader: String = "%prefix% &6Balance&8:",
        val currencyLineFormat: String = "&8-> <#d1d1d1>%currency%:</#> <#f9fdcb>%amount%</#> <#f2ff59>%symbol%</#>",
        val transferNotAllowed: String = "%prefix% <#a75948>Transfers are not allowed for the</#> <#d1d1d1>%currencyType%</#> <#a75948>currency!</#>",
        val payBelowMinimum: String = "%prefix% <#a75948>The minimum payment for</#> <#d1d1d1>%currencyType%</#> <#a75948>is</#> <#f9fdcb>%minimum%</#><#a75948>!</#>",
        val payAboveMaximum: String = "%prefix% <#a75948>The maximum payment for</#> <#d1d1d1>%currencyType%</#> <#a75948>is</#> <#f9fdcb>%maximum%</#><#a75948>!</#>",
        val invalidTarget: String = "%prefix% <#a75948>The selected target</#> <#f9fdcb>%target%</#> <#a75948>is not valid for this command!</#>",
        val withdrawNotAllowed: String = "%prefix% <#a75948>Withdrawals are not allowed for the</#> <#d1d1d1>%currencyType%</#> <#a75948>currency!</#>",
        val renameSameCurrencyType: String = "%prefix% <#a75948>Old and new currency type cannot be the same!</#>",
        val renameSuccess: String = "%prefix% <#95f151>Migrated balances for</#> <#f9fdcb>%migrated%</#> <#95f151>players from</#> <#d1d1d1>%old%</#> <#95f151>to</#> <#d1d1d1>%new%</#>!",
        val currencyAlreadyExists: String = "%prefix% <#a75948>Currency type</#> <#d1d1d1>%currencyType%</#> <#a75948>already exists in config!</#>",
        )

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private var messages: Messages = Messages()

    init { load() }

    fun load() {
        val defaults = gson.toJsonTree(Messages()).asJsonObject

        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.writeText(gson.toJson(defaults))
            messages = gson.fromJson(defaults, Messages::class.java)
            return
        }

        val existing = try {
            gson.fromJson(file.readText(), JsonObject::class.java) ?: JsonObject()
        } catch (_: Exception) {
            JsonObject()
        }

        for ((k, v) in defaults.entrySet()) {
            if (!existing.has(k) || existing.get(k).isJsonNull) {
                existing.add(k, v)
            }
        }

        if (PRUNE_UNUSED_KEYS) {
            val toRemove = existing.entrySet().map { it.key }.filter { !defaults.has(it) }
            toRemove.forEach(existing::remove)
        }

        file.writeText(gson.toJson(existing))
        messages = gson.fromJson(existing, Messages::class.java)
    }

    private fun raw(key: String): String = when (key) {
        "prefix" -> messages.prefix
        "paymentSuccess" -> messages.paymentSuccess
        "paymentReceived" -> messages.paymentReceived
        "insufficientFunds" -> messages.insufficientFunds
        "addBalance" -> messages.addBalance
        "withdrawSuccess" -> messages.withdrawSuccess
        "withdrawFail" -> messages.withdrawFail
        "removeBalance" -> messages.removeBalance
        "depositSuccess" -> messages.depositSuccess
        "depositBlockSet" -> messages.depositBlockSet
        "depositBlockRemoved" -> messages.depositBlockRemoved
        "depositBlockNotSet" -> messages.depositBlockNotSet
        "depositBlockNoTarget" -> messages.depositBlockNoTarget
        "depositGuiDisabled" -> messages.depositGuiDisabled
        "noValid" -> messages.noValid
        "playerNotFound" -> messages.playerNotFound
        "currencyNotFound" -> messages.currencyNotFound
        "configReloadSuccess" -> messages.configReloadSuccess
        "balanceHeader" -> messages.balanceHeader
        "currencyLineFormat" -> messages.currencyLineFormat
        "transferNotAllowed" -> messages.transferNotAllowed
        "payBelowMinimum" -> messages.payBelowMinimum
        "payAboveMaximum" -> messages.payAboveMaximum
        "invalidTarget" -> messages.invalidTarget
        "withdrawNotAllowed" -> messages.withdrawNotAllowed
        "renameSameCurrencyType" -> messages.renameSameCurrencyType
        "renameSuccess" -> messages.renameSuccess
        "currencyAlreadyExists" -> messages.currencyAlreadyExists
        else -> "&cUnknown message key: $key"
    }

    fun getRaw(key: String, vararg args: Pair<String, String>): String {
        var s = raw(key)
        s = s.replace("%prefix%", raw("prefix"))
        args.forEach { (ph, v) -> s = s.replace("%$ph%", v) }
        return s
    }

    fun get(key: String, vararg args: Pair<String, String>): MutableText {
        return processAllFormats(getRaw(key, *args))
    }
}