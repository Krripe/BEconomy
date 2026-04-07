package org.krripe.beconomy

import com.mojang.authlib.GameProfile
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.item.ItemStack
import me.lucko.fabric.api.permissions.v0.Permissions
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.command.argument.UuidArgumentType.uuid
import java.math.BigDecimal
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent
import net.minecraft.component.type.LoreComponent
import net.minecraft.component.type.ProfileComponent
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Style
import org.krripe.beconomy.api.BEconomy
import org.krripe.beconomy.config.ConfigManager
import org.krripe.beconomy.config.ConfigManager.config
import org.krripe.beconomy.config.MessageManager
import org.krripe.beconomy.database.DatabaseManager
import org.krripe.beconomy.integration.BEconomyGTSBridge
import org.krripe.beconomy.utils.CustomColorParser.processAllFormats
import org.krripe.beconomy.utils.PlaceholderUtil
import org.krripe.beconomy.utils.GuiItem
import org.krripe.beconomy.utils.GuiInteraction
import org.krripe.beconomy.utils.openGui
import org.krripe.beconomy.utils.updateOpenGui
import org.krripe.beconomy.utils.getGuiScreenHandlerType
import java.util.*
import org.pokesplash.gts.api.economy.GtsEconomyProvider
import org.pokesplash.gts.enumeration.Priority

class Beconomy : ModInitializer {

    override fun onInitialize() {
        println("Initializing BEconomy...")

        if (FabricLoader.getInstance().isModLoaded("gts")) {
            GtsEconomyProvider.putEconomy(Priority.HIGH, BEconomyGTSBridge())
            println("[BEconomy] Successfully integrated with GTS!")
        }

        val config = ConfigManager.loadConfig()
        MessageManager.load()

        if (config.databaseSave) {
            println("[BEconomy] Attempting to initialize database...")
            val databaseInitialized = DatabaseManager.initialize(config.database)
            if (databaseInitialized) {
                println("[BEconomy] Database connection established (${config.database.type}).")
            } else {
                println("[BEconomy] Database connection failed. Falling back to localSave.")
            }
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            BEconomy.initialize(server)
            PlaceholderUtil.registerPlaceholders()
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerCommands(dispatcher)
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            ConfigManager.initializeUserData(player.uuid)
            initializePlayerData(player)
        }

        ServerLifecycleEvents.SERVER_STOPPED.register {
            DatabaseManager.shutdown()
            println("[BEconomy] Database connection closed.")
        }

        println("BEconomy successfully initialized!")
    }

    private fun registerCommands(dispatcher: CommandDispatcher<ServerCommandSource>) {
        val commands = config.commands

        commands.forEach { command ->
            dispatcher.register(
                CommandManager.literal(command)
                    .then(CommandManager.literal("pay")
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg())
                            .then(CommandManager.argument("player", StringArgumentType.string())
                                .suggests { context, builder ->
                                    context.source.server.playerManager.playerList.map { it.name.string }
                                        .forEach { builder.suggest(it) }
                                    builder.buildFuture()
                                }
                                .then(CommandManager.argument("currencyType", StringArgumentType.string())
                                    .suggests { _, builder ->
                                        config.economy.map { it.currencyType }.forEach { builder.suggest(it) }
                                        builder.buildFuture()
                                    }
                                    .requires { source ->
                                        val player = source.player
                                        player != null && hasPermission(player, "beconomy.pay", 2)
                                    }
                                    .executes { context -> pay(context) }
                                )
                            )
                        )
                    )
                    .then(CommandManager.literal("add")
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg())
                            .then(CommandManager.argument("player", EntityArgumentType.entities())
                                .then(CommandManager.argument("currencyType", StringArgumentType.string())
                                    .suggests { _, builder ->
                                        config.economy.map { it.currencyType }.forEach { builder.suggest(it) }
                                        builder.buildFuture()
                                    }
                                    .requires { source ->
                                        (source.entity == null || hasPermission(source.player!!, "beconomy.add", 4))
                                    }
                                    .executes { context -> add(context) }
                                )
                            )
                        )
                    )
                    .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg())
                            .then(CommandManager.argument("player", EntityArgumentType.entities())
                                .then(CommandManager.argument("currencyType", StringArgumentType.string())
                                    .suggests { _, builder ->
                                        config.economy.map { it.currencyType }.forEach { builder.suggest(it) }
                                        builder.buildFuture()
                                    }
                                    .requires { source ->
                                        (source.entity == null || hasPermission(source.player!!, "beconomy.remove", 4))
                                    }
                                    .executes { context -> remove(context) }
                                )
                            )
                        )
                    )
                    .then(CommandManager.literal("deposit")
                        .then(CommandManager.argument("currencyType", StringArgumentType.string())
                            .suggests { _, builder ->
                                config.economy.map { it.currencyType }.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }
                            .requires { source ->
                                val player = source.player
                                player != null && hasPermission(player, "beconomy.deposit", 2)
                            }
                            .executes { context -> deposit(context) }
                        )
                    )
                    .then(CommandManager.literal("withdraw")
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .then(CommandManager.argument("currencyType", StringArgumentType.string())
                                .suggests { _, builder ->
                                    config.economy.map { it.currencyType }.forEach { builder.suggest(it) }
                                    builder.buildFuture()
                                }
                                .requires { source ->
                                    val player = source.player
                                    player != null && hasPermission(player, "beconomy.withdraw", 2)
                                }
                                .executes { context -> withdraw(context) }
                            )
                        )
                        .then(CommandManager.literal("type")
                            .then(CommandManager.argument("currencyType", StringArgumentType.string())
                                .suggests { _, builder ->
                                    config.economy.map { it.currencyType }.forEach { builder.suggest(it) }
                                    builder.buildFuture()
                                }
                                .then(CommandManager.argument("denomination", StringArgumentType.string())
                                    .suggests { ctx, builder ->
                                        val cur = try {
                                            StringArgumentType.getString(ctx, "currencyType")
                                        } catch (_: Exception) {
                                            null
                                        }
                                        val cfg = cur?.let { c ->
                                            config.economy.find { it.currencyType.equals(c, ignoreCase = true) }
                                        }
                                        cfg?.denominations?.forEach { denom ->
                                            val key = denom.id ?: denom.value.toPlainString()
                                            builder.suggest(key)
                                        }
                                        builder.buildFuture()
                                    }
                                    .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                                        .requires { source ->
                                            val player = source.player
                                            player != null && hasPermission(player, "beconomy.withdraw", 2)
                                        }
                                        .executes { ctx -> withdrawByType(ctx) }
                                    )
                                )
                            )
                        )
                    )
                    .then(CommandManager.literal("reload")
                        .requires { source ->
                            val player = source.player
                            player != null && hasPermission(player, "beconomy.reload", 4)
                        }
                        .executes { context -> reloadConfig(context) }
                    )
                    .then(CommandManager.literal("top")
                        .requires { source ->
                            val player = source.player
                            player != null && hasPermission(player, "beconomy.top", 2)
                        }
                        .executes { context ->
                            val player = context.source.player
                            if (player != null) {
                                openTopBalanceGui(player)
                            }
                            1
                        }
                    )
                    .then(CommandManager.literal("set")
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.0))
                            .then(CommandManager.argument("currencyType", StringArgumentType.string())
                                .suggests { _, builder ->
                                    config.economy.map { it.currencyType }.forEach { builder.suggest(it) }
                                    builder.buildFuture()
                                }
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                    .requires { source ->
                                        source.entity == null || hasPermission(source.player!!, "beconomy.set", 4)
                                    }
                                    .executes { context -> setBalance(context) }
                                )
                            )
                        )
                    )
                    .then(CommandManager.literal("rename")
                        .requires { source ->
                            source.entity == null || hasPermission(source.player!!, "beconomy.rename", 4)
                        }
                        .then(CommandManager.argument("oldCurrency", StringArgumentType.string())
                            .suggests { _, builder ->
                                config.economy.map { it.currencyType }.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }
                            .then(CommandManager.argument("newCurrency", StringArgumentType.string())
                                .executes { ctx -> renameCurrency(ctx) }
                            )
                        )
                    )
                    .then(CommandManager.literal("offline")
                        .then(CommandManager.literal("balance")
                            .then(CommandManager.argument("player", StringArgumentType.string())
                                .then(CommandManager.argument("currencyType", StringArgumentType.string())
                                    .suggests { _, builder ->
                                        config.economy.map { it.currencyType }.forEach { builder.suggest(it) }
                                        builder.buildFuture()
                                    }
                                    .requires { source -> hasPermission(source.playerOrThrow, "beconomy.offline.balance", 3) }
                                    .executes { ctx -> showOfflineBalance(ctx) }
                                )
                            )
                        )
                        .then(CommandManager.literal("add")
                            .then(CommandManager.argument("player", StringArgumentType.string())
                                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                    .then(CommandManager.argument("currencyType", StringArgumentType.string())
                                        .suggests { _, builder ->
                                            config.economy.map { it.currencyType }.forEach { builder.suggest(it) }
                                            builder.buildFuture()
                                        }
                                        .requires { source -> hasPermission(source.playerOrThrow, "beconomy.offline.add", 4) }
                                        .executes { ctx -> addOfflineBalance(ctx) }
                                    )
                                )
                            )
                        )
                        .then(CommandManager.literal("remove")
                            .then(CommandManager.argument("player", StringArgumentType.string())
                                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                    .then(CommandManager.argument("currencyType", StringArgumentType.string())
                                        .suggests { _, builder ->
                                            config.economy.map { it.currencyType }.forEach { builder.suggest(it) }
                                            builder.buildFuture()
                                        }
                                        .requires { source -> hasPermission(source.playerOrThrow, "beconomy.offline.remove", 4) }
                                        .executes { ctx -> removeOfflineBalance(ctx) }
                                    )
                                )
                            )
                        )
                        .then(CommandManager.literal("set")
                            .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                .then(CommandManager.argument("currencyType", StringArgumentType.string())
                                    .suggests { _, builder ->
                                        config.economy.map { it.currencyType }.forEach { builder.suggest(it) }
                                        builder.buildFuture()
                                    }
                                    .then(CommandManager.argument("player", StringArgumentType.string())
                                        .requires { source -> source.player == null || hasPermission(source.player!!, "beconomy.offline.set", 4) }
                                        .executes { context -> setBalanceOffline(context) }
                                    )
                                )
                            )
                        )
                    )
            )
        }

        config.balanceCommands.forEach { balanceCommand ->
            dispatcher.register(
                CommandManager.literal(balanceCommand)
                    .requires { source -> source.player != null && hasPermission(source.player!!, "beconomy.balance", 2) }
                    .executes { context -> showBalance(context) }
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .suggests { context, builder ->
                            context.source.server.playerManager.playerList.map { it.name.string }
                                .forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .requires { source -> source.player != null && hasPermission(source.player!!, "beconomy.balance.others", 3) }
                        .executes { context -> showTargetBalance(context) }
                    )
            )
        }
    }

    private val uuid_like_regex =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    private fun showBalance(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        displayBalance(player, player.name.string, player.uuid, context)
        return 1
    }

    private fun showTargetBalance(context: CommandContext<ServerCommandSource>): Int {
        val targetName = StringArgumentType.getString(context, "player")
        val targetPlayer = context.source.server.playerManager.getPlayer(targetName)

        if (targetPlayer == null) {
            context.source.sendMessage(MessageManager.get("playerNotFound", "player" to targetName))
            return 0
        }

        displayBalance(context.source.player, targetName, targetPlayer.uuid, context)
        return 1
    }

    private fun displayBalance(viewer: ServerPlayerEntity?, targetName: String, targetUUID: UUID, context: CommandContext<ServerCommandSource>) {
        val balanceHeader = MessageManager.get("balanceHeader")
        val message = Text.literal("").append(balanceHeader).append("\n")

        config.economy.forEach { currencyConfig ->
            val balance = ConfigManager.getBalance(targetUUID, currencyConfig.currencyType)
            val currencyLine = MessageManager.get(
                "currencyLineFormat",
                "currency" to currencyConfig.currencyType,
                "amount" to ConfigManager.NumberFormatter.format(balance),
                "symbol" to currencyConfig.symbol
            )

            message.append(currencyLine).append("\n")
        }

        viewer?.sendMessage(message, false) ?: context.source.sendMessage(message)
    }

    private fun pay(context: CommandContext<ServerCommandSource>): Int {
        val amount = DoubleArgumentType.getDouble(context, "amount")
        val playerName = StringArgumentType.getString(context, "player")
        val currencyType = StringArgumentType.getString(context, "currencyType")
        val amountBigDecimal = BigDecimal(amount.toString())
        val sender = context.source.player ?: return 0

        if (amountBigDecimal <= BigDecimal.ZERO) {
            sender.sendMessage(MessageManager.get("noValid"))
            return 0
        }

        val receiver = context.source.server.playerManager.getPlayer(playerName) ?: run {
            sender.sendMessage(MessageManager.get("playerNotFound", "player" to playerName))
            return 0
        }

        val currencyConfig = config.economy.find { it.currencyType == currencyType }
        if (currencyConfig == null) {
            sender.sendMessage(MessageManager.get("currencyNotFound", "currencyType" to currencyType))
            return 0
        }

        if (!currencyConfig.transfer) {
            sender.sendMessage(MessageManager.get("transferNotAllowed", "currencyType" to currencyType))
            return 0
        }

        val senderBalance = ConfigManager.getBalance(sender.uuid, currencyType)
        if (senderBalance >= amountBigDecimal) {
            ConfigManager.setBalance(sender.uuid, senderBalance.subtract(amountBigDecimal), currencyType)

            val receiverBalance = ConfigManager.getBalance(receiver.uuid, currencyType)
            ConfigManager.setBalance(receiver.uuid, receiverBalance.add(amountBigDecimal), currencyType)

            val paymentSuccessMessage = MessageManager.get(
                "paymentSuccess",
                "amount" to amount.toString(),
                "player" to playerName,
                "currencyType" to currencyType
            )
            val paymentReceivedMessage = MessageManager.get(
                "paymentReceived",
                "amount" to amount.toString(),
                "player" to sender.name.string,
                "currencyType" to currencyType
            )

            sender.sendMessage(paymentSuccessMessage, false)
            receiver.sendMessage(paymentReceivedMessage, false)
        } else {
            sender.sendMessage(MessageManager.get("insufficientFunds", "currencyType" to currencyType))
        }
        return 1
    }

    private fun add(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val amount = DoubleArgumentType.getDouble(context, "amount")
        val amountBigDecimal = BigDecimal(amount.toString())

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
                    "amount" to amount.toString(),
                    "player" to player.name.string,
                    "currencyType" to currencyType
                )
                source.sendMessage(message)
            }
        }
        return 1
    }

    private fun remove(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val amount = DoubleArgumentType.getDouble(context, "amount")
        val amountBigDecimal = BigDecimal(amount.toString())

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
                    ConfigManager.setBalance(player.uuid, balance.subtract(amountBigDecimal), currencyType)

                    val message = MessageManager.get(
                        "removeBalance",
                        "amount" to amount.toString(),
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

                stack.set(DataComponentTypes.CUSTOM_NAME, processAllFormats(denom.name))
                if (denom.lore.isNotEmpty()) {
                    val loreTexts = denom.lore.map { line -> processAllFormats(line) }
                    stack.set(DataComponentTypes.LORE, LoreComponent(loreTexts))
                }
                stacks += stack
            }
            remaining = remainder
        }
        return if (remaining.compareTo(BigDecimal.ZERO) == 0) stacks else null
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
            val stackCount = if (remaining > maxStack) maxStack else remaining
            remaining -= stackCount

            val stack = ItemStack(item, stackCount)

            if (denom.custommodeldata > 0) {
                stack.set(
                    DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelDataComponent(denom.custommodeldata)
                )
            }

            stack.set(DataComponentTypes.CUSTOM_NAME, processAllFormats(denom.name))

            if (denom.lore.isNotEmpty()) {
                val loreTexts = denom.lore.map { line -> processAllFormats(line) }
                stack.set(DataComponentTypes.LORE, LoreComponent(loreTexts))
            }

            stacks += stack
        }

        return stacks
    }

    private fun withdraw(context: CommandContext<ServerCommandSource>): Int {
        val amountDouble = DoubleArgumentType.getDouble(context, "amount")
        val player = context.source.player ?: return 0
        val currencyType = StringArgumentType.getString(context, "currencyType")
        val amount = BigDecimal(amountDouble.toString())

        if (amount <= BigDecimal.ZERO) {
            player.sendMessage(MessageManager.get("noValid"))
            return 0
        }

        val currencyConfig = config.economy.find { it.currencyType == currencyType }
        if (currencyConfig == null) {
            player.sendMessage(MessageManager.get("currencyNotFound", "currencyType" to currencyType))
            return 0
        }

        if (!currencyConfig.withdraw) {
            player.sendMessage(MessageManager.get("withdrawNotAllowed", "currencyType" to currencyType))
            return 0
        }

        val balance = ConfigManager.getBalance(player.uuid, currencyType)
        if (balance < amount) {
            player.sendMessage(MessageManager.get("withdrawFail", "currencyType" to currencyType))
            return 0
        }

        val stacks = createWithdrawalStacks(amount, currencyConfig)
        if (stacks.isNullOrEmpty()) {
            player.sendMessage(MessageManager.get("noValid"))
            return 0
        }

        ConfigManager.setBalance(player.uuid, balance.subtract(amount), currencyType)

        stacks.forEach { stack ->
            player.inventory.offerOrDrop(stack)
        }

        player.sendMessage(
            MessageManager.get(
                "withdrawSuccess",
                "amount" to amount.toString(),
                "currencyType" to currencyType
            )
        )
        return 1
    }

    private fun withdrawByType(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0

        val currencyType = StringArgumentType.getString(context, "currencyType")
        val denomKey = StringArgumentType.getString(context, "denomination")
        val count = IntegerArgumentType.getInteger(context, "count")

        if (count <= 0) {
            player.sendMessage(MessageManager.get("noValid"))
            return 0
        }

        val currencyCfg = config.economy.find { it.currencyType == currencyType }
        if (currencyCfg == null) {
            player.sendMessage(MessageManager.get("currencyNotFound", "currencyType" to currencyType))
            return 0
        }

        if (!currencyCfg.withdraw) {
            player.sendMessage(MessageManager.get("withdrawNotAllowed", "currencyType" to currencyType))
            return 0
        }

        val denoms = currencyCfg.denominations
        if (denoms.isNullOrEmpty()) {
            player.sendMessage(MessageManager.get("noValid"))
            return 0
        }

        val denom = denoms.firstOrNull { d ->
            (d.id != null && d.id.equals(denomKey, ignoreCase = true)) ||
                    d.value.toPlainString().equals(denomKey, ignoreCase = true)
        } ?: run {
            player.sendMessage(MessageManager.get("noValid"))
            return 0
        }

        val total = denom.value.multiply(BigDecimal(count))
        val balance = ConfigManager.getBalance(player.uuid, currencyType)

        if (balance < total) {
            player.sendMessage(MessageManager.get("insufficientFunds", "currencyType" to currencyType))
            return 0
        }

        val stacks = createStacksForDenom(denom, count)

        ConfigManager.setBalance(player.uuid, balance.subtract(total), currencyType)
        stacks.forEach { stack -> player.inventory.offerOrDrop(stack) }

        player.sendMessage(
            MessageManager.get(
                "withdrawSuccess",
                "amount" to total.toString(),
                "currencyType" to currencyType
            )
        )
        return 1
    }

    private fun deposit(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val currencyType = StringArgumentType.getString(context, "currencyType")
        val balance = ConfigManager.getBalance(player.uuid, currencyType)
        var totalAmount = BigDecimal.ZERO

        player.inventory.main.forEachIndexed { index, stack ->
            if (stack.isEmpty) return@forEachIndexed

            val perItemValue = getDenominationValue(stack, currencyType)
            if (perItemValue != null) {
                val stackTotal = perItemValue.multiply(BigDecimal(stack.count))
                totalAmount = totalAmount.add(stackTotal)
                player.inventory.removeStack(index)
            }
        }

        if (totalAmount > BigDecimal.ZERO) {
            ConfigManager.setBalance(player.uuid, balance.add(totalAmount), currencyType)
            player.sendMessage(
                MessageManager.get(
                    "depositSuccess",
                    "amount" to totalAmount.toString(),
                    "currencyType" to currencyType
                )
            )
        } else {
            player.sendMessage(MessageManager.get("noValid"))
        }

        return 1
    }

    private fun setBalanceOffline(ctx: CommandContext<ServerCommandSource>): Int {
        val name = StringArgumentType.getString(ctx, "player")
        val amount = BigDecimal(DoubleArgumentType.getDouble(ctx, "amount"))
        val currency = StringArgumentType.getString(ctx, "currencyType")
        val profile = ctx.source.server.userCache?.findByName(name)?.orElse(null)
        val uuid = profile?.id

        if (uuid == null) {
            ctx.source.sendMessage(MessageManager.get("playerNotFound", "player" to name))
            return 0
        }

        ConfigManager.setBalance(uuid, amount, currency)
        ctx.source.sendMessage(MessageManager.get("addBalance", "amount" to amount.toString(), "player" to name, "currencyType" to currency))
        return 1
    }

    private fun setBalance(ctx: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val amount = BigDecimal(DoubleArgumentType.getDouble(ctx, "amount"))
        val currency = StringArgumentType.getString(ctx, "currencyType")

        ConfigManager.setBalance(player.uuid, amount, currency)

        ctx.source.sendMessage(MessageManager.get("addBalance", "amount" to amount.toString(), "player" to player.name.string, "currencyType" to currency))
        return 1
    }


    private fun showOfflineBalance(ctx: CommandContext<ServerCommandSource>): Int {
        val name = StringArgumentType.getString(ctx, "player")
        val currency = StringArgumentType.getString(ctx, "currencyType")
        val profile = ctx.source.server.userCache?.findByName(name)?.orElse(null)
        val uuid = profile?.id

        if (uuid == null) {
            ctx.source.sendMessage(MessageManager.get("playerNotFound", "player" to name))
            return 0
        }

        val balance = ConfigManager.getBalance(uuid, currency)
        ctx.source.sendMessage(MessageManager.get("currencyLineFormat", "currency" to currency, "amount" to ConfigManager.NumberFormatter.format(balance), "symbol" to ConfigManager.getCurrencySymbol(currency)))
        return 1
    }

    private fun addOfflineBalance(ctx: CommandContext<ServerCommandSource>): Int {
        val name = StringArgumentType.getString(ctx, "player")
        val amount = BigDecimal(DoubleArgumentType.getDouble(ctx, "amount"))
        val currency = StringArgumentType.getString(ctx, "currencyType")
        val profile = ctx.source.server.userCache?.findByName(name)?.orElse(null)
        val uuid = profile?.id

        if (uuid == null) {
            ctx.source.sendMessage(MessageManager.get("playerNotFound", "player" to name))
            return 0
        }

        val oldBalance = ConfigManager.getBalance(uuid, currency)
        ConfigManager.setBalance(uuid, oldBalance + amount, currency)

        ctx.source.sendMessage(MessageManager.get("addBalance", "amount" to amount.toString(), "player" to name, "currencyType" to currency))
        return 1
    }

    private fun removeOfflineBalance(ctx: CommandContext<ServerCommandSource>): Int {
        val name = StringArgumentType.getString(ctx, "player")
        val amount = BigDecimal(DoubleArgumentType.getDouble(ctx, "amount"))
        val currency = StringArgumentType.getString(ctx, "currencyType")
        val profile = ctx.source.server.userCache?.findByName(name)?.orElse(null)
        val uuid = profile?.id

        if (uuid == null) {
            ctx.source.sendMessage(MessageManager.get("playerNotFound", "player" to name))
            return 0
        }

        val current = ConfigManager.getBalance(uuid, currency)
        if (current < amount) {
            ctx.source.sendMessage(MessageManager.get("insufficientFunds", "currencyType" to currency))
            return 0
        }

        ConfigManager.setBalance(uuid, current - amount, currency)
        ctx.source.sendMessage(MessageManager.get("removeBalance", "amount" to amount.toString(), "player" to name, "currencyType" to currency))
        return 1
    }

    private fun renameCurrency(context: CommandContext<ServerCommandSource>): Int {
        val source  = context.source
        val oldType = StringArgumentType.getString(context, "oldCurrency")
        val newType = StringArgumentType.getString(context, "newCurrency")
        if (oldType.equals(newType, ignoreCase = true)) {
            source.sendMessage(MessageManager.get("renameSameCurrencyType"))
            return 0
        }
        val list = config.economy
        val idx = list.indexOfFirst { it.currencyType.equals(oldType, ignoreCase = true) }
        if (idx == -1) {
            source.sendMessage(MessageManager.get("currencyNotFound", "currencyType" to oldType))
            return 0
        }
        if (list.any { it.currencyType.equals(newType, ignoreCase = true) }) {
            source.sendMessage(
                MessageManager.get("currencyAlreadyExists", "currencyType" to newType)
            )
            return 0
        }
        val oldCfg = list[idx]
        val newCfg = oldCfg.copy(currencyType = newType)
        list[idx] = newCfg
        ConfigManager.saveConfig()
        val migrated = ConfigManager.renameCurrencyType(oldType, newType)
        source.sendMessage(MessageManager.get("renameSuccess", "migrated" to migrated.toString(), "old"      to oldType, "new"      to newType))
        return 1
    }

    private fun getDenominationValue(stack: ItemStack, currencyType: String): BigDecimal? {
        val currencyCfg = config.economy.find { it.currencyType == currencyType } ?: return null
        val denoms = currencyCfg.denominations.orEmpty()
        for (denom in denoms) {
            val denomItem = Registries.ITEM.get(Identifier.of(denom.material))
            if (stack.item != denomItem) continue

            if (denom.matchOnlyMaterial) {
                return denom.value
            }

            val data = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA)?.value
            if (denom.custommodeldata == 0 || data == denom.custommodeldata) {
                return denom.value
            }
        }

        val baseItem = Registries.ITEM.get(Identifier.of(currencyCfg.material))
        if (stack.item != baseItem) return null

        if (currencyCfg.matchOnlyMaterial) {
            return BigDecimal.ONE
        }

        val baseData = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA)?.value
        return if (baseData == currencyCfg.custommodeldata) BigDecimal.ONE else null
    }


    private fun initializePlayerData(player: ServerPlayerEntity) {
        val playerUUID = player.uuid

        config.economy.forEach { currency ->
            val currencyType = currency.currencyType

            if (config.localSave) {
                if (!ConfigManager.hasUserData(playerUUID, currencyType)) {
                    ConfigManager.initializeUserData(playerUUID)
                }
            }

            if (config.databaseSave) {
                val adapter = DatabaseManager.getAdapter()
                if (adapter != null) {
                    if (!adapter.hasUserData(playerUUID, currencyType)) {
                        adapter.initializeUserData(playerUUID, currencyType)
                        val startingBalance = currency.balanceStart
                        adapter.setBalance(playerUUID, startingBalance, currencyType)
                    }
                } else {
                    println("[BEconomy] Database adapter not initialized. Skipping database initialization for $playerUUID.")
                }
            }
        }
    }

    private fun reloadConfig(context: CommandContext<ServerCommandSource>): Int {
        ConfigManager.reloadConfig()
        MessageManager.load()
        context.source.sendMessage(MessageManager.get("configReloadSuccess"))
        return 1
    }

    private fun hasPermission(player: ServerPlayerEntity, permission: String, level: Int): Boolean {
        return if (hasLuckPermsPermission(player, permission, level)) {
            true
        } else {
            player.hasPermissionLevel(level)
        }
    }

    private fun hasLuckPermsPermission(player: ServerPlayerEntity, permission: String, level: Int): Boolean {
        return try {
            Permissions.check(player, permission, level)
        } catch (_: NoClassDefFoundError) {
            false
        }
    }

    private fun parseSlots(exprs: List<String>, size: Int): List<Int> {
        val out = linkedSetOf<Int>()
        exprs.forEach { token ->
            token.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
                val range = part.split("..", "-")
                if (range.size == 2) {
                    val a = range[0].toIntOrNull(); val b = range[1].toIntOrNull()
                    if (a != null && b != null) {
                        val lo = maxOf(0, minOf(a, b))
                        val hi = minOf(size - 1, maxOf(a, b))
                        for (i in lo..hi) out.add(i)
                    }
                } else {
                    part.toIntOrNull()?.let { n -> if (n in 0 until size) out.add(n) }
                }
            }
        }
        return out.toList()
    }

    private fun createFillerItem(cfg: ConfigManager.FillerConfig): ItemStack {
        val material = Registries.ITEM.get(Identifier.of(cfg.icon))
        val stack = ItemStack(material)

        val nameText = processAllFormats(cfg.name).styled { it.withItalic(false) }
        stack.set(DataComponentTypes.CUSTOM_NAME, nameText)

        cfg.customModelData?.let { md ->
            if (md > 0) {
                stack.set(
                    DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelDataComponent(md)
                )
            }
        }

        if (cfg.lore.isNotEmpty()) {
            val loreTexts = cfg.lore.map { line ->
                processAllFormats(line).styled { it.withItalic(false) }
            }
            stack.set(DataComponentTypes.LORE, LoreComponent(loreTexts))
        }

        if (cfg.glow) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        }
        if (cfg.hideAdditionalTooltip) {
            stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        }
        if (cfg.hideTooltip) {
            stack.set(DataComponentTypes.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        }

        return stack
    }

    private fun openTopBalanceGui(player: ServerPlayerEntity) {
        val guiConfig = config.gui
        val size = if (guiConfig.size in listOf(27, 54)) guiConfig.size else 27
        val screenHandlerType = getGuiScreenHandlerType(size)

        val currencies = config.economy
        if (currencies.isEmpty()) {
            player.sendMessage(Text.literal("[BEconomy] No currencies configured."), false)
            return
        }

        val primaryIndex = currencies.indexOfFirst { it.isPrimary }.let { if (it == -1) 0 else it }

        data class TopGuiState(
            var page: Int,
            var currencyIndex: Int
        )

        val state = TopGuiState(page = 0, currencyIndex = primaryIndex)

        fun buildPage(pageIndex: Int, currencyIndex: Int): List<GuiItem> {
            val items = MutableList<GuiItem?>(size) { null }

            if (guiConfig.fillersEnabled && guiConfig.fillers.isNotEmpty()) {
                guiConfig.fillers.forEach { fCfg ->
                    val stack = createFillerItem(fCfg)
                    val slots = parseSlots(fCfg.slots, size)
                    slots.forEach { s ->
                        if (s in 0 until size && items[s] == null) {
                            items[s] = GuiItem(stack.copy(), s)
                        }
                    }
                }
            }

            val currency = currencies[currencyIndex]
            val sortedPlayers = getAllPlayersSortedByBalance(player.server, currency.currencyType)

            val perPage = guiConfig.headSlots.size
            if (perPage == 0) return items.filterNotNull()

            val maxPage = if (sortedPlayers.isEmpty()) 0 else (sortedPlayers.size - 1) / perPage
            val clampedPage = pageIndex.coerceIn(0, maxPage.coerceAtLeast(0))
            state.page = clampedPage

            val startIndex = clampedPage * perPage
            val pageEntries = sortedPlayers.drop(startIndex).take(perPage)

            pageEntries.forEachIndexed { index, playerData ->
                val slot = guiConfig.headSlots.getOrNull(index) ?: return@forEachIndexed
                val position = startIndex + index + 1
                val head = createPlayerHeadItem(
                    playerData.uuid,
                    playerData.name,
                    playerData.balance,
                    player.server,
                    position
                )
                items[slot] = GuiItem(head, slot)
            }

            items[guiConfig.closeButton.slot] =
                GuiItem(createItemStack(guiConfig.closeButton), guiConfig.closeButton.slot)

            if (clampedPage < maxPage && guiConfig.nextPageButton.slot in 0 until size) {
                items[guiConfig.nextPageButton.slot] =
                    GuiItem(createItemStack(guiConfig.nextPageButton), guiConfig.nextPageButton.slot)
            }

            if (clampedPage > 0 && guiConfig.prevPageButton.slot in 0 until size) {
                items[guiConfig.prevPageButton.slot] =
                    GuiItem(createItemStack(guiConfig.prevPageButton), guiConfig.prevPageButton.slot)
            }

            guiConfig.sortButton?.let { sortCfg ->
                if (sortCfg.slot in 0 until size) {
                    val sortStack = buildCurrencySortButton(sortCfg, currency.name)
                    items[sortCfg.slot] = GuiItem(sortStack, sortCfg.slot)
                }
            }

            return items.filterNotNull()
        }

        val initialItems = buildPage(0, state.currencyIndex)

        openGui(
            player,
            processAllFormats(guiConfig.title).setStyle(Style.EMPTY.withItalic(false)),
            initialItems,
            size,
            screenHandlerType,
            object : GuiInteraction {
                override fun onItemClick(slot: Int, player: ServerPlayerEntity, rightClick: Boolean) {
                    val currencies = config.economy
                    if (currencies.isEmpty()) return

                    val currentCurrency = currencies[state.currencyIndex]
                    val sortedPlayers = getAllPlayersSortedByBalance(player.server, currentCurrency.currencyType)
                    val perPage = guiConfig.headSlots.size
                    val maxPage = if (sortedPlayers.isEmpty()) 0 else (sortedPlayers.size - 1) / perPage

                    when (slot) {
                        guiConfig.closeButton.slot -> player.closeHandledScreen()

                        guiConfig.nextPageButton.slot -> {
                            if (state.page < maxPage) {
                                val newItems = buildPage(state.page + 1, state.currencyIndex)
                                if (!updateOpenGui(player, newItems, size)) {
                                    openTopBalanceGui(player)
                                }
                            }
                        }

                        guiConfig.prevPageButton.slot -> {
                            if (state.page > 0) {
                                val newItems = buildPage(state.page - 1, state.currencyIndex)
                                if (!updateOpenGui(player, newItems, size)) {
                                    openTopBalanceGui(player)
                                }
                            }
                        }

                        guiConfig.sortButton?.slot -> {
                            if (!rightClick) {
                                state.currencyIndex = (state.currencyIndex + 1) % currencies.size
                                state.page = 0
                                val newItems = buildPage(0, state.currencyIndex)
                                if (!updateOpenGui(player, newItems, size)) {
                                    openTopBalanceGui(player)
                                }
                            }
                        }
                    }
                }

                override fun onGuiClose(player: ServerPlayerEntity) {}
            }
        )
    }

    private fun buildCurrencySortButton(
        cfg: ConfigManager.ButtonConfig,
        currencyName: String
    ): ItemStack {
        val material = Registries.ITEM.get(Identifier.of(cfg.material))
        val stack = ItemStack(material)

        val displayName = cfg.name.replace("%currency%", currencyName)
        val nameText = processAllFormats(displayName).styled { it.withItalic(false) }
        stack.set(DataComponentTypes.CUSTOM_NAME, nameText)

        if (cfg.customModelData > 0) {
            stack.set(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelDataComponent(cfg.customModelData)
            )
        }

        if (cfg.lore.isNotEmpty()) {
            val loreTexts = cfg.lore.map { line ->
                processAllFormats(line.replace("%currency%", currencyName))
                    .styled { it.withItalic(false) }
            }
            stack.set(DataComponentTypes.LORE, LoreComponent(loreTexts))
        }

        return stack
    }

    private fun getAllPlayersSortedByBalance(
        server: MinecraftServer,
        currencyType: String
    ): List<PlayerBalance> {
        val balances = mutableMapOf<UUID, BigDecimal>()

        if (config.databaseSave) {
            val adapter = DatabaseManager.getAdapter()
            if (adapter != null) {
                val dbBalances = adapter.getAllBalances(currencyType)
                balances.putAll(dbBalances)
            }
        }

        if (config.localSave) {
            val userFolder = java.io.File("config/beconomy/user")
            if (userFolder.exists() && userFolder.isDirectory) {
                userFolder.listFiles { file -> file.extension == "json" }?.forEach { file ->
                    val uuid = runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull()
                        ?: return@forEach

                    val json = java.nio.file.Files.readString(file.toPath())
                    val jsonObject = com.google.gson.JsonParser.parseString(json).asJsonObject
                    val bal = jsonObject.get(currencyType)?.asBigDecimal
                    if (bal != null) {
                        balances[uuid] = bal
                    }
                }
            }
        }

        if (balances.isEmpty()) {
            return server.playerManager.playerList
                .map { p ->
                    PlayerBalance(
                        p.uuid,
                        p.name.string,
                        ConfigManager.getBalance(p.uuid, currencyType)
                    )
                }
                .sortedByDescending { it.balance }
        }

        fun resolveName(uuid: UUID): String {
            server.playerManager.getPlayer(uuid)?.let { player ->
                return player.gameProfile.name ?: player.name.string
            }

            val gp = server.userCache?.getByUuid(uuid)?.orElse(null)
            if (gp != null && !gp.name.isNullOrBlank()) {
                return gp.name
            }

            return "Unknown"
        }

        return balances
            .map { (uuid, bal) -> PlayerBalance(uuid, resolveName(uuid), bal) }
            .sortedByDescending { it.balance }
    }

    data class PlayerBalance(
        val uuid: UUID,
        val name: String,
        val balance: BigDecimal
    )

    private fun createPlayerHeadItem(
        playerUuid: UUID,
        playerName: String,
        balance: BigDecimal,
        server: MinecraftServer,
        position: Int? = null
    ): ItemStack {
        val player = server.playerManager.getPlayer(playerUuid)
        val playerHead = ItemStack(Registries.ITEM.get(Identifier.of("minecraft:player_head")))

        val displayName = when {
            player != null -> player.gameProfile.name ?: player.name.string
            playerName.equals("Unknown", ignoreCase = true) -> "Unknown"
            uuid_like_regex.matches(playerName) -> "Unknown"
            playerName.length < 36 && uuid().toString().startsWith(playerName) -> "Unknown"
            else -> playerName
        }

        val cachedProfile = player?.gameProfile
            ?: server.userCache?.getByUuid(playerUuid)?.orElse(null)

        val profileName = when {
            cachedProfile?.name?.isNotBlank() == true -> cachedProfile.name
            displayName != "Unknown" -> displayName
            else -> "Player"
        }.take(16)

        val profile = cachedProfile?.takeIf { it.name == profileName }
            ?: GameProfile(playerUuid, profileName)

        playerHead.set(DataComponentTypes.PROFILE, ProfileComponent(profile))

        val customNameText = processAllFormats("&e$displayName").styled { it.withItalic(false) }
        playerHead.set(DataComponentTypes.CUSTOM_NAME, customNameText)

        val positionStr = position?.toString() ?: ""

        val lore = config.gui.headLore.map {
            it.replace("%playerName%", displayName)
                .replace("%balance%", ConfigManager.NumberFormatter.format(balance))
                .replace("%status%", if (player != null) "&aOnline" else "&cOffline")
                .replace("%position%", positionStr)
        }.map {
            processAllFormats(it, player).styled { style -> style.withItalic(false) }
        }

        playerHead.set(DataComponentTypes.LORE, LoreComponent(lore))

        return playerHead
    }

    private fun createItemStack(itemConfig: Any): ItemStack {
        val materialId = when (itemConfig) {
            is ConfigManager.GuiItem      -> itemConfig.material
            is ConfigManager.ButtonConfig -> itemConfig.material
            else -> error("Unsupported config type")
        }

        val name = when (itemConfig) {
            is ConfigManager.GuiItem      -> itemConfig.name
            is ConfigManager.ButtonConfig -> itemConfig.name
            else -> "Unnamed"
        }

        val lore = when (itemConfig) {
            is ConfigManager.GuiItem      -> itemConfig.lore
            is ConfigManager.ButtonConfig -> itemConfig.lore
            else -> emptyList()
        }

        val customModelData = when (itemConfig) {
            is ConfigManager.GuiItem      -> itemConfig.customModelData
            is ConfigManager.ButtonConfig -> itemConfig.customModelData
            else -> 0
        }

        val glow = when (itemConfig) {
            is ConfigManager.GuiItem      -> itemConfig.glow
            is ConfigManager.ButtonConfig -> itemConfig.glow
            else -> false
        }

        val hideAdditionalTooltip = when (itemConfig) {
            is ConfigManager.GuiItem      -> itemConfig.hideAdditionalTooltip
            is ConfigManager.ButtonConfig -> itemConfig.hideAdditionalTooltip
            else -> true
        }

        val hideTooltip = when (itemConfig) {
            is ConfigManager.GuiItem      -> itemConfig.hideTooltip
            is ConfigManager.ButtonConfig -> itemConfig.hideTooltip
            else -> false
        }

        val material = Registries.ITEM.get(Identifier.of(materialId))
        val stack = ItemStack(material)

        if (customModelData > 0) {
            stack.set(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelDataComponent(customModelData)
            )
        }

        val displayName = processAllFormats(name)
        stack.set(DataComponentTypes.CUSTOM_NAME, displayName)?.style =
            Style.EMPTY.withItalic(false)

        if (lore.isNotEmpty()) {
            val loreList = lore.map {
                processAllFormats(it).setStyle(Style.EMPTY.withItalic(false))
            }
            stack.set(DataComponentTypes.LORE, LoreComponent(loreList))
        }

        if (glow) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        }
        if (hideAdditionalTooltip) {
            stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        }
        if (hideTooltip) {
            stack.set(DataComponentTypes.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        }

        return stack
    }
}