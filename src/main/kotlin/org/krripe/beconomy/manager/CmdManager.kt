package org.krripe.beconomy.manager

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import org.krripe.beconomy.config.ConfigManager.config
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import org.krripe.beconomy.config.DepositConfigManager
import org.krripe.beconomy.config.MessageManager

object CmdManager {

    internal fun registerCommands(
        dispatcher: CommandDispatcher<ServerCommandSource>
    ) {
        val commands = config.commands

        commands.forEach { command ->
            dispatcher.register(
                CommandManager.literal(command)
                    .then(
                        CommandManager.literal("pay")
                            .then(
                                CommandManager.argument(
                                    "amount",
                                    DoubleArgumentType.doubleArg()
                                )
                                    .then(
                                        CommandManager.argument(
                                            "player",
                                            StringArgumentType.string()
                                        )
                                            .suggests { context, builder ->
                                                context.source.server
                                                    .playerManager
                                                    .playerList
                                                    .map { it.name.string }
                                                    .forEach {
                                                        builder.suggest(it)
                                                    }

                                                builder.buildFuture()
                                            }
                                            .then(
                                                CommandManager.argument(
                                                    "currencyType",
                                                    StringArgumentType.string()
                                                )
                                                    .suggests { _, builder ->
                                                        config.economy
                                                            .map {
                                                                it.currencyType
                                                            }
                                                            .forEach {
                                                                builder.suggest(it)
                                                            }

                                                        builder.buildFuture()
                                                    }
                                                    .requires { source ->
                                                        val player =
                                                            source.player

                                                        player != null &&
                                                                PermManager.hasPermission(
                                                                    player,
                                                                    "beconomy.pay",
                                                                    2
                                                                )
                                                    }
                                                    .executes { context ->
                                                        EcoManager.pay(
                                                            context
                                                        )
                                                    }
                                            )
                                    )
                            )
                    )
                    .then(
                        CommandManager.literal("add")
                            .then(
                                CommandManager.argument(
                                    "amount",
                                    DoubleArgumentType.doubleArg()
                                )
                                    .then(
                                        CommandManager.argument(
                                            "player",
                                            EntityArgumentType.entities()
                                        )
                                            .then(
                                                CommandManager.argument(
                                                    "currencyType",
                                                    StringArgumentType.string()
                                                )
                                                    .suggests { _, builder ->
                                                        config.economy
                                                            .map {
                                                                it.currencyType
                                                            }
                                                            .forEach {
                                                                builder.suggest(it)
                                                            }

                                                        builder.buildFuture()
                                                    }
                                                    .requires { source ->
                                                        source.entity == null ||
                                                                PermManager.hasPermission(
                                                                    source.player!!,
                                                                    "beconomy.add",
                                                                    4
                                                                )
                                                    }
                                                    .executes { context ->
                                                        EcoManager.add(
                                                            context
                                                        )
                                                    }
                                            )
                                    )
                            )
                    )
                    .then(
                        CommandManager.literal("remove")
                            .then(
                                CommandManager.argument(
                                    "amount",
                                    DoubleArgumentType.doubleArg()
                                )
                                    .then(
                                        CommandManager.argument(
                                            "player",
                                            EntityArgumentType.entities()
                                        )
                                            .then(
                                                CommandManager.argument(
                                                    "currencyType",
                                                    StringArgumentType.string()
                                                )
                                                    .suggests { _, builder ->
                                                        config.economy
                                                            .map {
                                                                it.currencyType
                                                            }
                                                            .forEach {
                                                                builder.suggest(it)
                                                            }

                                                        builder.buildFuture()
                                                    }
                                                    .requires { source ->
                                                        source.entity == null ||
                                                                PermManager.hasPermission(
                                                                    source.player!!,
                                                                    "beconomy.remove",
                                                                    4
                                                                )
                                                    }
                                                    .executes { context ->
                                                        EcoManager.remove(
                                                            context
                                                        )
                                                    }
                                            )
                                    )
                            )
                    )
                    .then(
                        CommandManager.literal("deposit")
                            .then(
                                CommandManager.argument(
                                    "currencyType",
                                    StringArgumentType.string()
                                )
                                    .suggests { _, builder ->
                                        config.economy
                                            .map { it.currencyType }
                                            .forEach {
                                                builder.suggest(it)
                                            }

                                        builder.buildFuture()
                                    }
                                    .requires { source ->
                                        val player =
                                            source.player

                                        player != null &&
                                                PermManager.hasPermission(
                                                    player,
                                                    "beconomy.deposit",
                                                    2
                                                )
                                    }
                                    .executes { context ->
                                        EcoManager.deposit(
                                            context
                                        )
                                    }
                            )
                    )
                    .then(
                        CommandManager.literal("depositgui")
                            .then(
                                CommandManager.argument(
                                    "currencyType",
                                    StringArgumentType.string()
                                )
                                    .suggests { _, builder ->
                                        config.economy
                                            .filter {
                                                DepositConfigManager.get(
                                                    it.currencyType
                                                )?.enabled == true
                                            }
                                            .map {
                                                it.currencyType
                                            }
                                            .forEach {
                                                builder.suggest(it)
                                            }

                                        builder.buildFuture()
                                    }
                                    .requires { source ->
                                        val player =
                                            source.player

                                        player != null &&
                                                PermManager.hasPermission(
                                                    player,
                                                    "beconomy.depositgui",
                                                    2
                                                )
                                    }
                                    .executes { context ->
                                        val player =
                                            context.source.player
                                                ?: return@executes 0

                                        val currencyType =
                                            StringArgumentType.getString(
                                                context,
                                                "currencyType"
                                            )

                                        if (
                                            DepositManager.openDepositMenu(
                                                player,
                                                currencyType
                                            )
                                        ) {
                                            1
                                        } else {
                                            0
                                        }
                                    }
                            )
                            .then(
                                CommandManager.argument(
                                    "player",
                                    EntityArgumentType.player()
                                )
                                    .requires { source ->
                                        val player =
                                            source.player

                                        player == null ||
                                                PermManager.hasPermission(
                                                    player,
                                                    "beconomy.depositgui.others",
                                                    4
                                                )
                                    }
                                    .then(
                                        CommandManager.argument(
                                            "targetCurrencyType",
                                            StringArgumentType.string()
                                        )
                                            .suggests { _, builder ->
                                                config.economy
                                                    .filter {
                                                        DepositConfigManager.get(
                                                            it.currencyType
                                                        )?.enabled == true
                                                    }
                                                    .map {
                                                        it.currencyType
                                                    }
                                                    .forEach {
                                                        builder.suggest(it)
                                                    }

                                                builder.buildFuture()
                                            }
                                            .executes { context ->
                                                val player =
                                                    EntityArgumentType.getPlayer(
                                                        context,
                                                        "player"
                                                    )

                                                val currencyType =
                                                    StringArgumentType.getString(
                                                        context,
                                                        "targetCurrencyType"
                                                    )

                                                if (
                                                    DepositManager.openDepositMenu(
                                                        player,
                                                        currencyType
                                                    )
                                                ) {
                                                    1
                                                } else {
                                                    0
                                                }
                                            }
                                    )
                            )
                    )
                    .then(
                        CommandManager.literal(
                            "depositblock"
                        )
                            .then(
                                CommandManager.literal(
                                    "set"
                                )
                                    .requires { source ->
                                        val player =
                                            source.player

                                        player != null &&
                                                PermManager.hasPermission(
                                                    player,
                                                    "beconomy.depositblock.set",
                                                    4
                                                )
                                    }
                                    .then(
                                        CommandManager.argument(
                                            "currencyType",
                                            StringArgumentType.string()
                                        )
                                            .suggests { _, builder ->
                                                config.economy
                                                    .filter {
                                                        DepositConfigManager.get(
                                                            it.currencyType
                                                        )?.enabled == true
                                                    }
                                                    .map {
                                                        it.currencyType
                                                    }
                                                    .forEach {
                                                        builder.suggest(it)
                                                    }

                                                builder.buildFuture()
                                            }
                                            .executes { context ->
                                                val player =
                                                    context.source.player
                                                        ?: return@executes 0

                                                val currencyType =
                                                    StringArgumentType.getString(
                                                        context,
                                                        "currencyType"
                                                    )

                                                val economy =
                                                    config.economy.find {
                                                        it.currencyType.equals(
                                                            currencyType,
                                                            ignoreCase = true
                                                        )
                                                    }

                                                if (economy == null) {
                                                    player.sendMessage(
                                                        MessageManager.get(
                                                            "currencyNotFound",
                                                            "currencyType" to
                                                                    currencyType
                                                        )
                                                    )

                                                    return@executes 0
                                                }

                                                val depositConfig =
                                                    DepositConfigManager.get(
                                                        economy.currencyType
                                                    )

                                                if (
                                                    depositConfig == null ||
                                                    !depositConfig.enabled
                                                ) {
                                                    player.sendMessage(
                                                        MessageManager.get(
                                                            "depositGuiDisabled",
                                                            "currencyType" to
                                                                    economy.currencyType
                                                        )
                                                    )

                                                    return@executes 0
                                                }

                                                val hit =
                                                    player.raycast(
                                                        5.0,
                                                        0f,
                                                        false
                                                    )

                                                if (
                                                    hit !is BlockHitResult ||
                                                    hit.type != HitResult.Type.BLOCK
                                                ) {
                                                    player.sendMessage(
                                                        MessageManager.get(
                                                            "depositBlockNoTarget"
                                                        )
                                                    )

                                                    return@executes 0
                                                }

                                                val world =
                                                    player.world as? ServerWorld
                                                        ?: return@executes 0

                                                DepositBlockManager.set(
                                                    world,
                                                    hit.blockPos,
                                                    economy.currencyType
                                                )

                                                player.sendMessage(
                                                    MessageManager.get(
                                                        "depositBlockSet",
                                                        "currencyType" to
                                                                economy.currencyType
                                                    )
                                                )

                                                1
                                            }
                                    )
                            )
                            .then(
                                CommandManager.literal(
                                    "remove"
                                )
                                    .requires { source ->
                                        val player =
                                            source.player

                                        player != null &&
                                                PermManager.hasPermission(
                                                    player,
                                                    "beconomy.depositblock.remove",
                                                    4
                                                )
                                    }
                                    .executes { context ->
                                        val player =
                                            context.source.player
                                                ?: return@executes 0

                                        val hit =
                                            player.raycast(
                                                5.0,
                                                0f,
                                                false
                                            )

                                        if (
                                            hit !is BlockHitResult ||
                                            hit.type != HitResult.Type.BLOCK
                                        ) {
                                            player.sendMessage(
                                                MessageManager.get(
                                                    "depositBlockNoTarget"
                                                )
                                            )

                                            return@executes 0
                                        }

                                        val world =
                                            player.world as? ServerWorld
                                                ?: return@executes 0

                                        val removed =
                                            DepositBlockManager.remove(
                                                world,
                                                hit.blockPos
                                            )

                                        if (removed == null) {
                                            player.sendMessage(
                                                MessageManager.get(
                                                    "depositBlockNotSet"
                                                )
                                            )

                                            return@executes 0
                                        }

                                        player.sendMessage(
                                            MessageManager.get(
                                                "depositBlockRemoved"
                                            )
                                        )

                                        1
                                    }
                            )
                    )
                    .then(
                        CommandManager.literal("withdraw")
                            .then(
                                CommandManager.argument(
                                    "amount",
                                    DoubleArgumentType.doubleArg(
                                        0.01
                                    )
                                )
                                    .then(
                                        CommandManager.argument(
                                            "currencyType",
                                            StringArgumentType.string()
                                        )
                                            .suggests { _, builder ->
                                                config.economy
                                                    .map {
                                                        it.currencyType
                                                    }
                                                    .forEach {
                                                        builder.suggest(it)
                                                    }

                                                builder.buildFuture()
                                            }
                                            .requires { source ->
                                                val player =
                                                    source.player

                                                player != null &&
                                                        PermManager.hasPermission(
                                                            player,
                                                            "beconomy.withdraw",
                                                            2
                                                        )
                                            }
                                            .executes { context ->
                                                EcoManager.withdraw(
                                                    context
                                                )
                                            }
                                    )
                            )
                            .then(
                                CommandManager.literal("type")
                                    .then(
                                        CommandManager.argument(
                                            "currencyType",
                                            StringArgumentType.string()
                                        )
                                            .suggests { _, builder ->
                                                config.economy
                                                    .map {
                                                        it.currencyType
                                                    }
                                                    .forEach {
                                                        builder.suggest(it)
                                                    }

                                                builder.buildFuture()
                                            }
                                            .then(
                                                CommandManager.argument(
                                                    "denomination",
                                                    StringArgumentType.string()
                                                )
                                                    .suggests { ctx, builder ->
                                                        val cur =
                                                            try {
                                                                StringArgumentType
                                                                    .getString(
                                                                        ctx,
                                                                        "currencyType"
                                                                    )
                                                            } catch (
                                                                _: Exception
                                                            ) {
                                                                null
                                                            }

                                                        val cfg =
                                                            cur?.let { c ->
                                                                config.economy
                                                                    .find {
                                                                        it.currencyType
                                                                            .equals(
                                                                                c,
                                                                                ignoreCase = true
                                                                            )
                                                                    }
                                                            }

                                                        cfg?.denominations
                                                            ?.forEach { denom ->
                                                                val key =
                                                                    denom.id
                                                                        ?: denom.value
                                                                            .toPlainString()

                                                                builder.suggest(
                                                                    key
                                                                )
                                                            }

                                                        builder.buildFuture()
                                                    }
                                                    .then(
                                                        CommandManager.argument(
                                                            "count",
                                                            IntegerArgumentType
                                                                .integer(
                                                                    1
                                                                )
                                                        )
                                                            .requires { source ->
                                                                val player =
                                                                    source.player

                                                                player != null &&
                                                                        PermManager.hasPermission(
                                                                            player,
                                                                            "beconomy.withdraw",
                                                                            2
                                                                        )
                                                            }
                                                            .executes { ctx ->
                                                                EcoManager
                                                                    .withdrawByType(
                                                                        ctx
                                                                    )
                                                            }
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        CommandManager.literal("reload")
                            .requires { source ->
                                val player =
                                    source.player

                                player != null &&
                                        PermManager.hasPermission(
                                            player,
                                            "beconomy.reload",
                                            4
                                        )
                            }
                            .executes { context ->
                                EcoManager.reloadConfig(
                                    context
                                )
                            }
                    )
                    .then(
                        CommandManager.literal("top")
                            .requires { source ->
                                val player =
                                    source.player

                                player != null &&
                                        PermManager.hasPermission(
                                            player,
                                            "beconomy.top",
                                            2
                                        )
                            }
                            .executes { context ->
                                val player =
                                    context.source.player

                                if (player != null) {
                                    GuiManager.openTopBalanceGui(
                                        player
                                    )
                                }

                                1
                            }
                    )
                    .then(
                        CommandManager.literal("set")
                            .then(
                                CommandManager.argument(
                                    "amount",
                                    DoubleArgumentType.doubleArg(
                                        0.0
                                    )
                                )
                                    .then(
                                        CommandManager.argument(
                                            "currencyType",
                                            StringArgumentType.string()
                                        )
                                            .suggests { _, builder ->
                                                config.economy
                                                    .map {
                                                        it.currencyType
                                                    }
                                                    .forEach {
                                                        builder.suggest(it)
                                                    }

                                                builder.buildFuture()
                                            }
                                            .then(
                                                CommandManager.argument(
                                                    "player",
                                                    EntityArgumentType.player()
                                                )
                                                    .requires { source ->
                                                        source.entity == null ||
                                                                PermManager.hasPermission(
                                                                    source.player!!,
                                                                    "beconomy.set",
                                                                    4
                                                                )
                                                    }
                                                    .executes { context ->
                                                        EcoManager.setBalance(
                                                            context
                                                        )
                                                    }
                                            )
                                    )
                            )
                    )
                    .then(
                        CommandManager.literal("rename")
                            .requires { source ->
                                source.entity == null ||
                                        PermManager.hasPermission(
                                            source.player!!,
                                            "beconomy.rename",
                                            4
                                        )
                            }
                            .then(
                                CommandManager.argument(
                                    "oldCurrency",
                                    StringArgumentType.string()
                                )
                                    .suggests { _, builder ->
                                        config.economy
                                            .map {
                                                it.currencyType
                                            }
                                            .forEach {
                                                builder.suggest(it)
                                            }

                                        builder.buildFuture()
                                    }
                                    .then(
                                        CommandManager.argument(
                                            "newCurrency",
                                            StringArgumentType.string()
                                        )
                                            .executes { ctx ->
                                                EcoManager.renameCurrency(
                                                    ctx
                                                )
                                            }
                                    )
                            )
                    )
                    .then(
                        CommandManager.literal("offline")
                            .then(
                                CommandManager.literal(
                                    "balance"
                                )
                                    .then(
                                        CommandManager.argument(
                                            "player",
                                            StringArgumentType.string()
                                        )
                                            .then(
                                                CommandManager.argument(
                                                    "currencyType",
                                                    StringArgumentType.string()
                                                )
                                                    .suggests { _, builder ->
                                                        config.economy
                                                            .map {
                                                                it.currencyType
                                                            }
                                                            .forEach {
                                                                builder.suggest(it)
                                                            }

                                                        builder.buildFuture()
                                                    }
                                                    .requires { source ->
                                                        PermManager.hasPermission(
                                                            source.playerOrThrow,
                                                            "beconomy.offline.balance",
                                                            3
                                                        )
                                                    }
                                                    .executes { ctx ->
                                                        EcoManager
                                                            .showOfflineBalance(
                                                                ctx
                                                            )
                                                    }
                                            )
                                    )
                            )
                            .then(
                                CommandManager.literal(
                                    "add"
                                )
                                    .then(
                                        CommandManager.argument(
                                            "player",
                                            StringArgumentType.string()
                                        )
                                            .then(
                                                CommandManager.argument(
                                                    "amount",
                                                    DoubleArgumentType.doubleArg(
                                                        0.01
                                                    )
                                                )
                                                    .then(
                                                        CommandManager.argument(
                                                            "currencyType",
                                                            StringArgumentType.string()
                                                        )
                                                            .suggests { _, builder ->
                                                                config.economy
                                                                    .map {
                                                                        it.currencyType
                                                                    }
                                                                    .forEach {
                                                                        builder.suggest(
                                                                            it
                                                                        )
                                                                    }

                                                                builder.buildFuture()
                                                            }
                                                            .requires { source ->
                                                                PermManager.hasPermission(
                                                                    source.playerOrThrow,
                                                                    "beconomy.offline.add",
                                                                    4
                                                                )
                                                            }
                                                            .executes { ctx ->
                                                                EcoManager
                                                                    .addOfflineBalance(
                                                                        ctx
                                                                    )
                                                            }
                                                    )
                                            )
                                    )
                            )
                            .then(
                                CommandManager.literal(
                                    "remove"
                                )
                                    .then(
                                        CommandManager.argument(
                                            "player",
                                            StringArgumentType.string()
                                        )
                                            .then(
                                                CommandManager.argument(
                                                    "amount",
                                                    DoubleArgumentType.doubleArg(
                                                        0.01
                                                    )
                                                )
                                                    .then(
                                                        CommandManager.argument(
                                                            "currencyType",
                                                            StringArgumentType.string()
                                                        )
                                                            .suggests { _, builder ->
                                                                config.economy
                                                                    .map {
                                                                        it.currencyType
                                                                    }
                                                                    .forEach {
                                                                        builder.suggest(
                                                                            it
                                                                        )
                                                                    }

                                                                builder.buildFuture()
                                                            }
                                                            .requires { source ->
                                                                PermManager.hasPermission(
                                                                    source.playerOrThrow,
                                                                    "beconomy.offline.remove",
                                                                    4
                                                                )
                                                            }
                                                            .executes { ctx ->
                                                                EcoManager
                                                                    .removeOfflineBalance(
                                                                        ctx
                                                                    )
                                                            }
                                                    )
                                            )
                                    )
                            )
                            .then(
                                CommandManager.literal(
                                    "set"
                                )
                                    .then(
                                        CommandManager.argument(
                                            "amount",
                                            DoubleArgumentType.doubleArg(
                                                0.0
                                            )
                                        )
                                            .then(
                                                CommandManager.argument(
                                                    "currencyType",
                                                    StringArgumentType.string()
                                                )
                                                    .suggests { _, builder ->
                                                        config.economy
                                                            .map {
                                                                it.currencyType
                                                            }
                                                            .forEach {
                                                                builder.suggest(
                                                                    it
                                                                )
                                                            }

                                                        builder.buildFuture()
                                                    }
                                                    .then(
                                                        CommandManager.argument(
                                                            "player",
                                                            StringArgumentType.string()
                                                        )
                                                            .requires { source ->
                                                                source.player == null ||
                                                                        PermManager.hasPermission(
                                                                            source.player!!,
                                                                            "beconomy.offline.set",
                                                                            4
                                                                        )
                                                            }
                                                            .executes { context ->
                                                                EcoManager
                                                                    .setBalanceOffline(
                                                                        context
                                                                    )
                                                            }
                                                    )
                                            )
                                    )
                            )
                    )
            )
        }

        config.balanceCommands.forEach {
                balanceCommand ->
            dispatcher.register(
                CommandManager.literal(
                    balanceCommand
                )
                    .requires { source ->
                        source.player != null &&
                                PermManager.hasPermission(
                                    source.player!!,
                                    "beconomy.balance",
                                    2
                                )
                    }
                    .executes { context ->
                        EcoManager.showBalance(
                            context
                        )
                    }
                    .then(
                        CommandManager.argument(
                            "player",
                            StringArgumentType.string()
                        )
                            .suggests { context, builder ->
                                context.source.server
                                    .playerManager
                                    .playerList
                                    .map {
                                        it.name.string
                                    }
                                    .forEach {
                                        builder.suggest(it)
                                    }

                                builder.buildFuture()
                            }
                            .requires { source ->
                                source.player != null &&
                                        PermManager.hasPermission(
                                            source.player!!,
                                            "beconomy.balance.others",
                                            3
                                        )
                            }
                            .executes { context ->
                                EcoManager.showTargetBalance(
                                    context
                                )
                            }
                    )
            )
        }

        CustomManager.registerEconomyCommands(
            dispatcher
        )

        DepositManager.registerCommands(
            dispatcher
        )
    }
}