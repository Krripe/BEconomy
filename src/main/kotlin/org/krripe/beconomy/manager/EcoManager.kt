package org.krripe.beconomy.manager

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.krripe.beconomy.config.BarConfigManager
import org.krripe.beconomy.config.ConfigManager
import org.krripe.beconomy.config.ConfigManager.config
import org.krripe.beconomy.config.DepositConfigManager
import org.krripe.beconomy.config.MenuConfigManager
import org.krripe.beconomy.config.MessageManager
import org.krripe.beconomy.database.DatabaseManager
import org.krripe.beconomy.utils.CustomColorParser.processAllFormats
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID

object EcoManager {

    data class DepositResult(
        val items: Int,
        val value: BigDecimal
    )

    internal fun showBalance(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val economies = config.economy.filter {
            it.balance && it.showInGlobalBalance
        }
        displayBalance(player, player.uuid, context, economies)
        return 1
    }

    internal fun showTargetBalance(context: CommandContext<ServerCommandSource>): Int {
        val targetName = StringArgumentType.getString(context, "player")
        val targetPlayer = context.source.server.playerManager.getPlayer(targetName)

        if (targetPlayer == null) {
            context.source.sendMessage(MessageManager.get("playerNotFound", "player" to targetName))
            return 0
        }

        val economies = config.economy.filter {
            it.balance && it.showInGlobalBalance
        }
        displayBalance(context.source.player, targetPlayer.uuid, context, economies)
        return 1
    }

    private fun displayBalance(
        viewer: ServerPlayerEntity?,
        targetUUID: UUID,
        context: CommandContext<ServerCommandSource>,
        economies: List<ConfigManager.EconomyConfig>
    ) {
        val message = Text.literal("")
            .append(MessageManager.get("balanceHeader"))
            .append("\n")

        economies.forEach { economy ->
            val balance = ConfigManager.getBalance(targetUUID, economy.currencyType)

            message.append(
                MessageManager.get(
                    "currencyLineFormat",
                    "currency" to economy.currencyType,
                    "amount" to ConfigManager.NumberFormatter.format(balance),
                    "symbol" to economy.symbol
                )
            ).append("\n")
        }

        viewer?.sendMessage(message, false) ?: context.source.sendMessage(message)
    }

    internal fun pay(context: CommandContext<ServerCommandSource>): Int {
        val amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "amount"))
        val playerName = StringArgumentType.getString(context, "player")
        val currencyType = StringArgumentType.getString(context, "currencyType")
        val sender = context.source.player ?: return 0

        if (amount <= BigDecimal.ZERO) {
            sender.sendMessage(MessageManager.get("noValid"))
            return 0
        }

        val receiver = context.source.server.playerManager.getPlayer(playerName)
        if (receiver == null) {
            sender.sendMessage(MessageManager.get("playerNotFound", "player" to playerName))
            return 0
        }

        val currency = config.economy.find {
            it.currencyType.equals(currencyType, ignoreCase = true)
        }

        if (currency == null) {
            sender.sendMessage(MessageManager.get("currencyNotFound", "currencyType" to currencyType))
            return 0
        }

        if (!currency.transfer) {
            sender.sendMessage(MessageManager.get("transferNotAllowed", "currencyType" to currency.currencyType))
            return 0
        }

        val minimum = currency.pay.minimumAmount.max(BigDecimal.ZERO)
        val maximum = currency.pay.maximumAmount

        if (minimum > BigDecimal.ZERO && amount < minimum) {
            sender.sendMessage(
                MessageManager.get(
                    "payBelowMinimum",
                    "currencyType" to currency.currencyType,
                    "minimum" to ConfigManager.NumberFormatter.format(minimum)
                )
            )
            return 0
        }

        if (maximum >= BigDecimal.ZERO && amount > maximum) {
            sender.sendMessage(
                MessageManager.get(
                    "payAboveMaximum",
                    "currencyType" to currency.currencyType,
                    "maximum" to ConfigManager.NumberFormatter.format(maximum)
                )
            )
            return 0
        }

        val senderBalance = ConfigManager.getBalance(sender.uuid, currency.currencyType)

        if (senderBalance < amount) {
            sender.sendMessage(MessageManager.get("insufficientFunds", "currencyType" to currency.currencyType))
            return 0
        }

        ConfigManager.setBalance(
            sender.uuid,
            senderBalance.subtract(amount),
            currency.currencyType
        )

        val receiverBalance = ConfigManager.getBalance(receiver.uuid, currency.currencyType)

        ConfigManager.setBalance(
            receiver.uuid,
            receiverBalance.add(amount),
            currency.currencyType
        )

        val formattedAmount = ConfigManager.NumberFormatter.format(amount)

        sender.sendMessage(
            MessageManager.get(
                "paymentSuccess",
                "amount" to formattedAmount,
                "player" to receiver.name.string,
                "currencyType" to currency.currencyType
            ),
            false
        )

        receiver.sendMessage(
            MessageManager.get(
                "paymentReceived",
                "amount" to formattedAmount,
                "player" to sender.name.string,
                "currencyType" to currency.currencyType
            ),
            false
        )

        return 1
    }

    internal fun add(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val amount = DoubleArgumentType.getDouble(context, "amount")
        val amountBigDecimal = BigDecimal.valueOf(amount)

        if (amountBigDecimal <= BigDecimal.ZERO) {
            source.sendMessage(MessageManager.get("noValid"))
            return 0
        }

        val currencyType = StringArgumentType.getString(context, "currencyType")

        val targets = try {
            EntityArgumentType.getPlayers(context, "player")
        } catch (_: CommandSyntaxException) {
            source.sendMessage(MessageManager.get("invalidTarget", "target" to context.input))
            return 0
        }

        for (player in targets) {
            if (player is ServerPlayerEntity) {
                val balance = ConfigManager.getBalance(player.uuid, currencyType)
                ConfigManager.setBalance(player.uuid, balance.add(amountBigDecimal), currencyType)

                val message = MessageManager.get(
                    "addBalance",
                    "amount" to ConfigManager.NumberFormatter.format(amountBigDecimal),
                    "player" to player.name.string,
                    "currencyType" to currencyType
                )

                source.sendMessage(message)
            }
        }

        return 1
    }

    internal fun remove(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val amount = DoubleArgumentType.getDouble(context, "amount")
        val amountBigDecimal = BigDecimal.valueOf(amount)

        if (amountBigDecimal <= BigDecimal.ZERO) {
            source.sendMessage(MessageManager.get("noValid"))
            return 0
        }

        val currencyType = StringArgumentType.getString(context, "currencyType")

        val targets = try {
            EntityArgumentType.getPlayers(context, "player")
        } catch (_: CommandSyntaxException) {
            source.sendMessage(MessageManager.get("invalidTarget", "target" to context.input))
            return 0
        }

        for (player in targets) {
            if (player is ServerPlayerEntity) {
                val balance = ConfigManager.getBalance(player.uuid, currencyType)

                if (balance >= amountBigDecimal) {
                    ConfigManager.setBalance(
                        player.uuid,
                        balance.subtract(amountBigDecimal),
                        currencyType
                    )

                    val message = MessageManager.get(
                        "removeBalance",
                        "amount" to ConfigManager.NumberFormatter.format(amountBigDecimal),
                        "player" to player.name.string,
                        "currencyType" to currencyType
                    )

                    source.sendMessage(message)
                } else {
                    val insufficientMessage = MessageManager.get(
                        "insufficientFunds",
                        "currencyType" to currencyType
                    )

                    source.sendMessage(insufficientMessage)
                }
            }
        }

        return 1
    }

    private data class RuntimeDenomination(
        val value: BigDecimal,
        val materialId: String,
        val customModelData: Int,
        val name: String,
        val lore: List<String>,
        val matchOnlyMaterial: Boolean
    )

    private fun createWithdrawalStacks(
        amount: BigDecimal,
        currencyCfg: ConfigManager.EconomyConfig
    ): List<ItemStack>? {
        if (amount <= BigDecimal.ZERO) return null

        val denoms = mutableListOf<RuntimeDenomination>()

        currencyCfg.denominations?.forEach { denom ->
            denoms += RuntimeDenomination(
                value = denom.value,
                materialId = denom.material,
                customModelData = denom.custommodeldata,
                name = denom.name,
                lore = denom.lore,
                matchOnlyMaterial = denom.matchOnlyMaterial
            )
        }

        denoms += RuntimeDenomination(
            value = BigDecimal.ONE,
            materialId = currencyCfg.material,
            customModelData = currencyCfg.custommodeldata,
            name = currencyCfg.name,
            lore = currencyCfg.lore,
            matchOnlyMaterial = currencyCfg.matchOnlyMaterial
        )

        val filtered = denoms.filter { it.value > BigDecimal.ZERO }

        if (filtered.isEmpty()) return null

        val sortedDenoms = filtered.sortedByDescending { it.value }
        var remaining = amount
        val stacks = mutableListOf<ItemStack>()

        for (denom in sortedDenoms) {
            if (remaining <= BigDecimal.ZERO) break

            val divRem = remaining.divideAndRemainder(denom.value)
            val count = divRem[0].toInt()
            val remainder = divRem[1]

            if (count <= 0) {
                remaining = remainder
                continue
            }

            val item = Registries.ITEM.get(Identifier.of(denom.materialId))
            val maxStack = item.maxCount
            var left = count

            while (left > 0) {
                val stackCount = if (left > maxStack) maxStack else left
                left -= stackCount

                val stack = ItemStack(item, stackCount)

                if (denom.customModelData > 0) {
                    stack.set(
                        DataComponentTypes.CUSTOM_MODEL_DATA,
                        CustomModelDataComponent(denom.customModelData)
                    )
                }

                stack.set(
                    DataComponentTypes.CUSTOM_NAME,
                    processAllFormats(denom.name)
                )

                if (denom.lore.isNotEmpty()) {
                    val loreTexts = denom.lore.map { line ->
                        processAllFormats(line)
                    }

                    stack.set(
                        DataComponentTypes.LORE,
                        LoreComponent(loreTexts)
                    )
                }

                stacks += stack
            }

            remaining = remainder
        }

        return if (
            remaining.compareTo(BigDecimal.ZERO) == 0
        ) {
            stacks
        } else {
            null
        }
    }

    private fun createStacksForDenom(
        denom: ConfigManager.DenominationConfig,
        count: Int
    ): List<ItemStack> {
        val item = Registries.ITEM.get(Identifier.of(denom.material))
        val stacks = mutableListOf<ItemStack>()
        var remaining = count
        val maxStack = item.maxCount

        while (remaining > 0) {
            val stackCount =
                if (remaining > maxStack) maxStack
                else remaining

            remaining -= stackCount

            val stack = ItemStack(
                item,
                stackCount
            )

            if (denom.custommodeldata > 0) {
                stack.set(
                    DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelDataComponent(
                        denom.custommodeldata
                    )
                )
            }

            stack.set(
                DataComponentTypes.CUSTOM_NAME,
                processAllFormats(denom.name)
            )

            if (denom.lore.isNotEmpty()) {
                val loreTexts =
                    denom.lore.map { line ->
                        processAllFormats(line)
                    }

                stack.set(
                    DataComponentTypes.LORE,
                    LoreComponent(loreTexts)
                )
            }

            stacks += stack
        }

        return stacks
    }

    internal fun withdraw(
        context: CommandContext<ServerCommandSource>
    ): Int {
        val amountDouble =
            DoubleArgumentType.getDouble(
                context,
                "amount"
            )

        val player =
            context.source.player
                ?: return 0

        val currencyType =
            StringArgumentType.getString(
                context,
                "currencyType"
            )

        val amount =
            BigDecimal.valueOf(
                amountDouble
            )

        if (amount <= BigDecimal.ZERO) {
            player.sendMessage(
                MessageManager.get(
                    "noValid"
                )
            )

            return 0
        }

        val currencyConfig =
            config.economy.find {
                it.currencyType == currencyType
            }

        if (currencyConfig == null) {
            player.sendMessage(
                MessageManager.get(
                    "currencyNotFound",
                    "currencyType" to currencyType
                )
            )

            return 0
        }

        if (!currencyConfig.withdraw) {
            player.sendMessage(
                MessageManager.get(
                    "withdrawNotAllowed",
                    "currencyType" to currencyType
                )
            )

            return 0
        }

        val balance =
            ConfigManager.getBalance(
                player.uuid,
                currencyType
            )

        if (balance < amount) {
            player.sendMessage(
                MessageManager.get(
                    "withdrawFail",
                    "currencyType" to currencyType
                )
            )

            return 0
        }

        val stacks =
            createWithdrawalStacks(
                amount,
                currencyConfig
            )

        if (stacks.isNullOrEmpty()) {
            player.sendMessage(
                MessageManager.get(
                    "noValid"
                )
            )

            return 0
        }

        ConfigManager.setBalance(
            player.uuid,
            balance.subtract(amount),
            currencyType
        )

        stacks.forEach { stack ->
            player.inventory.offerOrDrop(
                stack
            )
        }

        player.sendMessage(
            MessageManager.get(
                "withdrawSuccess",
                "amount" to
                        ConfigManager.NumberFormatter.format(
                            amount
                        ),
                "currencyType" to currencyType
            )
        )

        return 1
    }

    internal fun withdrawByType(
        context: CommandContext<ServerCommandSource>
    ): Int {
        val player =
            context.source.player
                ?: return 0

        val currencyType =
            StringArgumentType.getString(
                context,
                "currencyType"
            )

        val denomKey =
            StringArgumentType.getString(
                context,
                "denomination"
            )

        val count =
            IntegerArgumentType.getInteger(
                context,
                "count"
            )

        if (count <= 0) {
            player.sendMessage(
                MessageManager.get(
                    "noValid"
                )
            )

            return 0
        }

        val currencyCfg =
            config.economy.find {
                it.currencyType == currencyType
            }

        if (currencyCfg == null) {
            player.sendMessage(
                MessageManager.get(
                    "currencyNotFound",
                    "currencyType" to currencyType
                )
            )

            return 0
        }

        if (!currencyCfg.withdraw) {
            player.sendMessage(
                MessageManager.get(
                    "withdrawNotAllowed",
                    "currencyType" to currencyType
                )
            )

            return 0
        }

        val denoms =
            currencyCfg.denominations

        if (denoms.isNullOrEmpty()) {
            player.sendMessage(
                MessageManager.get(
                    "noValid"
                )
            )

            return 0
        }

        val denom =
            denoms.firstOrNull { d ->
                (
                        d.id != null &&
                                d.id.equals(
                                    denomKey,
                                    ignoreCase = true
                                )
                        ) ||
                        d.value
                            .toPlainString()
                            .equals(
                                denomKey,
                                ignoreCase = true
                            )
            } ?: run {
                player.sendMessage(
                    MessageManager.get(
                        "noValid"
                    )
                )

                return 0
            }

        val total =
            denom.value.multiply(
                BigDecimal(count)
            )

        val balance =
            ConfigManager.getBalance(
                player.uuid,
                currencyType
            )

        if (balance < total) {
            player.sendMessage(
                MessageManager.get(
                    "insufficientFunds",
                    "currencyType" to currencyType
                )
            )

            return 0
        }

        val stacks =
            createStacksForDenom(
                denom,
                count
            )

        ConfigManager.setBalance(
            player.uuid,
            balance.subtract(total),
            currencyType
        )

        stacks.forEach { stack ->
            player.inventory.offerOrDrop(
                stack
            )
        }

        player.sendMessage(
            MessageManager.get(
                "withdrawSuccess",
                "amount" to
                        ConfigManager.NumberFormatter.format(
                            total
                        ),
                "currencyType" to currencyType
            )
        )

        return 1
    }

    internal fun deposit(
        context: CommandContext<ServerCommandSource>
    ): Int {
        val player =
            context.source.player
                ?: return 0

        val currencyType =
            StringArgumentType.getString(
                context,
                "currencyType"
            )

        val currency =
            config.economy.find {
                it.currencyType.equals(
                    currencyType,
                    ignoreCase = true
                )
            }

        if (currency == null) {
            player.sendMessage(
                MessageManager.get(
                    "currencyNotFound",
                    "currencyType" to currencyType
                )
            )

            return 0
        }

        var totalAmount =
            BigDecimal.ZERO

        player.inventory.main.forEach { stack ->
            if (stack.isEmpty) {
                return@forEach
            }

            val result =
                depositStack(
                    player,
                    currency.currencyType,
                    stack,
                    stack.count
                ) ?: return@forEach

            totalAmount =
                totalAmount.add(
                    result.value
                )
        }

        if (totalAmount <= BigDecimal.ZERO) {
            player.sendMessage(
                MessageManager.get(
                    "noValid"
                )
            )

            return 0
        }

        player.sendMessage(
            MessageManager.get(
                "depositSuccess",
                "amount" to
                        ConfigManager.NumberFormatter.format(
                            totalAmount
                        ),
                "currencyType" to
                        currency.currencyType
            )
        )

        return 1
    }

    internal fun setBalanceOffline(
        ctx: CommandContext<ServerCommandSource>
    ): Int {
        val name =
            StringArgumentType.getString(
                ctx,
                "player"
            )

        val amount =
            BigDecimal.valueOf(
                DoubleArgumentType.getDouble(
                    ctx,
                    "amount"
                )
            )

        val currency =
            StringArgumentType.getString(
                ctx,
                "currencyType"
            )

        val profile =
            ctx.source.server.userCache
                ?.findByName(name)
                ?.orElse(null)

        val uuid =
            profile?.id

        if (uuid == null) {
            ctx.source.sendMessage(
                MessageManager.get(
                    "playerNotFound",
                    "player" to name
                )
            )

            return 0
        }

        ConfigManager.setBalance(
            uuid,
            amount,
            currency
        )

        ctx.source.sendMessage(
            MessageManager.get(
                "addBalance",
                "amount" to
                        ConfigManager.NumberFormatter.format(
                            amount
                        ),
                "player" to name,
                "currencyType" to currency
            )
        )

        return 1
    }

    internal fun setBalance(
        ctx: CommandContext<ServerCommandSource>
    ): Int {
        val player =
            EntityArgumentType.getPlayer(
                ctx,
                "player"
            )

        val amount =
            BigDecimal.valueOf(
                DoubleArgumentType.getDouble(
                    ctx,
                    "amount"
                )
            )

        val currency =
            StringArgumentType.getString(
                ctx,
                "currencyType"
            )

        ConfigManager.setBalance(
            player.uuid,
            amount,
            currency
        )

        ctx.source.sendMessage(
            MessageManager.get(
                "addBalance",
                "amount" to
                        ConfigManager.NumberFormatter.format(
                            amount
                        ),
                "player" to player.name.string,
                "currencyType" to currency
            )
        )

        return 1
    }

    internal fun showOfflineBalance(
        ctx: CommandContext<ServerCommandSource>
    ): Int {
        val name =
            StringArgumentType.getString(
                ctx,
                "player"
            )

        val currency =
            StringArgumentType.getString(
                ctx,
                "currencyType"
            )

        val profile =
            ctx.source.server.userCache
                ?.findByName(name)
                ?.orElse(null)

        val uuid =
            profile?.id

        if (uuid == null) {
            ctx.source.sendMessage(
                MessageManager.get(
                    "playerNotFound",
                    "player" to name
                )
            )

            return 0
        }

        val balance =
            ConfigManager.getBalance(
                uuid,
                currency
            )

        ctx.source.sendMessage(
            MessageManager.get(
                "currencyLineFormat",
                "currency" to currency,
                "amount" to
                        ConfigManager.NumberFormatter.format(
                            balance
                        ),
                "symbol" to
                        ConfigManager.getCurrencySymbol(
                            currency
                        )
            )
        )

        return 1
    }

    internal fun addOfflineBalance(
        ctx: CommandContext<ServerCommandSource>
    ): Int {
        val name =
            StringArgumentType.getString(
                ctx,
                "player"
            )

        val amount =
            BigDecimal.valueOf(
                DoubleArgumentType.getDouble(
                    ctx,
                    "amount"
                )
            )

        val currency =
            StringArgumentType.getString(
                ctx,
                "currencyType"
            )

        val profile =
            ctx.source.server.userCache
                ?.findByName(name)
                ?.orElse(null)

        val uuid =
            profile?.id

        if (uuid == null) {
            ctx.source.sendMessage(
                MessageManager.get(
                    "playerNotFound",
                    "player" to name
                )
            )

            return 0
        }

        val oldBalance =
            ConfigManager.getBalance(
                uuid,
                currency
            )

        ConfigManager.setBalance(
            uuid,
            oldBalance + amount,
            currency
        )

        ctx.source.sendMessage(
            MessageManager.get(
                "addBalance",
                "amount" to
                        ConfigManager.NumberFormatter.format(
                            amount
                        ),
                "player" to name,
                "currencyType" to currency
            )
        )

        return 1
    }

    internal fun removeOfflineBalance(
        ctx: CommandContext<ServerCommandSource>
    ): Int {
        val name =
            StringArgumentType.getString(
                ctx,
                "player"
            )

        val amount =
            BigDecimal.valueOf(
                DoubleArgumentType.getDouble(
                    ctx,
                    "amount"
                )
            )

        val currency =
            StringArgumentType.getString(
                ctx,
                "currencyType"
            )

        val profile =
            ctx.source.server.userCache
                ?.findByName(name)
                ?.orElse(null)

        val uuid =
            profile?.id

        if (uuid == null) {
            ctx.source.sendMessage(
                MessageManager.get(
                    "playerNotFound",
                    "player" to name
                )
            )

            return 0
        }

        val current =
            ConfigManager.getBalance(
                uuid,
                currency
            )

        if (current < amount) {
            ctx.source.sendMessage(
                MessageManager.get(
                    "insufficientFunds",
                    "currencyType" to currency
                )
            )

            return 0
        }

        ConfigManager.setBalance(
            uuid,
            current - amount,
            currency
        )

        ctx.source.sendMessage(
            MessageManager.get(
                "removeBalance",
                "amount" to
                        ConfigManager.NumberFormatter.format(
                            amount
                        ),
                "player" to name,
                "currencyType" to currency
            )
        )

        return 1
    }

    internal fun renameCurrency(
        context: CommandContext<ServerCommandSource>
    ): Int {
        val source =
            context.source

        val oldType =
            StringArgumentType.getString(
                context,
                "oldCurrency"
            )

        val newType =
            StringArgumentType.getString(
                context,
                "newCurrency"
            )

        if (
            oldType.equals(
                newType,
                ignoreCase = true
            )
        ) {
            source.sendMessage(
                MessageManager.get(
                    "renameSameCurrencyType"
                )
            )

            return 0
        }

        val list =
            config.economy

        val index =
            list.indexOfFirst {
                it.currencyType.equals(
                    oldType,
                    ignoreCase = true
                )
            }

        if (index == -1) {
            source.sendMessage(
                MessageManager.get(
                    "currencyNotFound",
                    "currencyType" to oldType
                )
            )

            return 0
        }

        if (
            list.any {
                it.currencyType.equals(
                    newType,
                    ignoreCase = true
                )
            }
        ) {
            source.sendMessage(
                MessageManager.get(
                    "currencyAlreadyExists",
                    "currencyType" to newType
                )
            )

            return 0
        }

        val oldConfig =
            list[index]

        list[index] =
            oldConfig.copy(
                currencyType = newType
            )

        ConfigManager.saveConfig()

        val migrated =
            ConfigManager.renameCurrencyType(
                oldType,
                newType
            )

        MenuConfigManager.renameCurrency(
            oldType,
            newType
        )

        DepositConfigManager.renameCurrency(
            oldType,
            newType
        )

        DepositBlockManager.renameCurrency(
            oldType,
            newType
        )

        CustomManager
            .renamedCurrencyTypes
            .entries
            .forEach { entry ->
                if (
                    entry.value.equals(
                        oldType,
                        ignoreCase = true
                    )
                ) {
                    entry.setValue(
                        newType
                    )
                }
            }

        CustomManager.renamedCurrencyTypes[
            oldType.lowercase(
                Locale.ROOT
            )
        ] = newType

        source.sendMessage(
            MessageManager.get(
                "renameSuccess",
                "migrated" to migrated.toString(),
                "old" to oldType,
                "new" to newType
            )
        )

        return 1
    }

    internal fun getDenominationValue(
        stack: ItemStack,
        currencyType: String
    ): BigDecimal? {
        val currencyCfg =
            config.economy.find {
                it.currencyType.equals(
                    currencyType,
                    ignoreCase = true
                )
            } ?: return null

        val denoms =
            currencyCfg.denominations
                .orEmpty()

        for (denom in denoms) {
            val denomItem =
                Registries.ITEM.get(
                    Identifier.of(
                        denom.material
                    )
                )

            if (stack.item != denomItem) {
                continue
            }

            if (denom.matchOnlyMaterial) {
                return denom.value
            }

            val data =
                stack.get(
                    DataComponentTypes.CUSTOM_MODEL_DATA
                )?.value

            if (
                denom.custommodeldata == 0 ||
                data == denom.custommodeldata
            ) {
                return denom.value
            }
        }

        val baseItem =
            Registries.ITEM.get(
                Identifier.of(
                    currencyCfg.material
                )
            )

        if (stack.item != baseItem) {
            return null
        }

        if (currencyCfg.matchOnlyMaterial) {
            return BigDecimal.ONE
        }

        val baseData =
            stack.get(
                DataComponentTypes.CUSTOM_MODEL_DATA
            )?.value

        return if (
            baseData ==
            currencyCfg.custommodeldata
        ) {
            BigDecimal.ONE
        } else {
            null
        }
    }

    internal fun depositStack(
        player: ServerPlayerEntity,
        currencyType: String,
        stack: ItemStack,
        count: Int
    ): DepositResult? {
        if (
            stack.isEmpty ||
            count <= 0 ||
            count > stack.count
        ) {
            return null
        }

        val currency =
            config.economy.find {
                it.currencyType.equals(
                    currencyType,
                    ignoreCase = true
                )
            } ?: return null

        val perItemValue =
            getDenominationValue(
                stack,
                currency.currencyType
            ) ?: return null

        if (perItemValue <= BigDecimal.ZERO) {
            return null
        }

        val totalValue =
            perItemValue.multiply(
                BigDecimal.valueOf(
                    count.toLong()
                )
            )

        if (totalValue <= BigDecimal.ZERO) {
            return null
        }

        val balance =
            ConfigManager.getBalance(
                player.uuid,
                currency.currencyType
            )

        stack.decrement(count)

        ConfigManager.setBalance(
            player.uuid,
            balance.add(totalValue),
            currency.currencyType
        )

        return DepositResult(
            items = count,
            value = totalValue
        )
    }

    internal fun initializePlayerData(
        player: ServerPlayerEntity
    ) {
        val playerUUID =
            player.uuid

        config.economy.forEach { currency ->
            val currencyType =
                currency.currencyType

            if (config.localSave) {
                if (
                    !ConfigManager.hasUserData(
                        playerUUID,
                        currencyType
                    )
                ) {
                    ConfigManager.initializeUserData(
                        playerUUID
                    )
                }
            }

            if (config.databaseSave) {
                val adapter =
                    DatabaseManager.getAdapter()

                if (adapter != null) {
                    if (
                        !adapter.hasUserData(
                            playerUUID,
                            currencyType
                        )
                    ) {
                        adapter.initializeUserData(
                            playerUUID,
                            currencyType
                        )

                        val startingBalance =
                            currency.balanceStart

                        adapter.setBalance(
                            playerUUID,
                            startingBalance,
                            currencyType
                        )
                    }
                } else {
                    println(
                        "[BEconomy] Database adapter not initialized. Skipping database initialization for $playerUUID."
                    )
                }
            }
        }
    }

    internal fun reloadConfig(
        context: CommandContext<ServerCommandSource>
    ): Int {
        ConfigManager.reloadConfig()
        MessageManager.load()
        BarConfigManager.reload()

        DepositConfigManager.reloadAll(
            config.economy
        )

        DepositBlockManager.reload()

        context.source.server.playerManager.playerList.forEach {
            BarManager.updatePlayer(it)
        }

        context.source.sendMessage(
            MessageManager.get(
                "configReloadSuccess"
            )
        )

        return 1
    }
}