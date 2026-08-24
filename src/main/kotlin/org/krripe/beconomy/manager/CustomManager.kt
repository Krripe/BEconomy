package org.krripe.beconomy.manager

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import org.krripe.beconomy.api.BEconomy
import org.krripe.beconomy.config.ConfigManager
import org.krripe.beconomy.config.ConfigManager.config
import org.krripe.beconomy.config.MenuConfigManager
import org.krripe.beconomy.config.MessageManager
import org.krripe.beconomy.utils.CustomColorParser.processAllFormats
import java.util.Locale

object CustomManager {

    internal val renamedCurrencyTypes = mutableMapOf<String, String>()

    internal fun registerEconomyCommands(
        dispatcher: CommandDispatcher<ServerCommandSource>
    ) {
        val commandRegex = Regex("^[a-z0-9_-]+$")

        fun normalize(
            values: List<String>,
            currencyType: String,
            type: String
        ): List<String> {
            return values.mapNotNull { raw ->
                val value = raw
                    .trim()
                    .removePrefix("/")
                    .lowercase(Locale.ROOT)

                if (value.isBlank() || !commandRegex.matches(value)) {
                    println(
                        "[BEconomy] Invalid $type command '$raw' for '$currencyType'."
                    )
                    null
                } else {
                    value
                }
            }.distinct()
        }

        for (economy in config.economy) {
            val currencyType = economy.currencyType
            val commands = economy.customCommands

            if (!commands.enabled) continue
            if (
                (!commands.balance || !economy.balance) &&
                (!commands.top || !economy.top)
            ) continue

            val rootAliases = normalize(
                commands.aliases,
                currencyType,
                "root"
            )

            val balanceAliases = normalize(
                commands.subcommands.balance,
                currencyType,
                "balance subcommand"
            )

            val topAliases = normalize(
                commands.subcommands.top,
                currencyType,
                "top subcommand"
            )

            for (alias in rootAliases) {
                if (
                    dispatcher.root.children.any {
                        it.name.equals(alias, ignoreCase = true)
                    }
                ) {
                    println(
                        "[BEconomy] Custom command '/$alias' for '$currencyType' conflicts with an existing command."
                    )
                    continue
                }

                val root = CommandManager.literal(alias)
                    .requires { source ->
                        val player = source.player

                        player != null &&
                                isEconomyAliasActive(
                                    currencyType,
                                    alias
                                ) &&
                                canAccessEconomyCommand(
                                    player,
                                    currencyType
                                )
                    }
                    .executes { context ->
                        executeEconomyDefaultAction(
                            context,
                            currencyType
                        )
                    }

                val usedSubcommands = mutableSetOf<String>()

                if (commands.balance && economy.balance) {
                    for (subcommand in balanceAliases) {
                        if (!usedSubcommands.add(subcommand)) continue

                        root.then(
                            CommandManager.literal(subcommand)
                                .requires { source ->
                                    val player = source.player

                                    player != null &&
                                            canUseEconomyBalance(currencyType) &&
                                            hasEconomyCommandPermission(
                                                player,
                                                currencyType
                                            ) &&
                                            hasEconomyBalancePermission(
                                                player,
                                                currencyType
                                            )
                                }
                                .executes { context ->
                                    showEconomyBalance(
                                        context,
                                        currencyType
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
                                                .map { it.name.string }
                                                .forEach {
                                                    builder.suggest(it)
                                                }

                                            builder.buildFuture()
                                        }
                                        .requires { source ->
                                            val player = source.player

                                            player != null &&
                                                    canUseEconomyBalance(
                                                        currencyType
                                                    ) &&
                                                    hasEconomyCommandPermission(
                                                        player,
                                                        currencyType
                                                    ) &&
                                                    hasEconomyBalancePermission(
                                                        player,
                                                        currencyType
                                                    ) &&
                                                    hasEconomyBalanceOthersPermission(
                                                        player,
                                                        currencyType
                                                    )
                                        }
                                        .executes { context ->
                                            showTargetEconomyBalance(
                                                context,
                                                currencyType
                                            )
                                        }
                                )
                        )
                    }
                }

                if (commands.top && economy.top) {
                    for (subcommand in topAliases) {
                        if (!usedSubcommands.add(subcommand)) {
                            println(
                                "[BEconomy] Subcommand '/$alias $subcommand' for '$currencyType' is already used."
                            )
                            continue
                        }

                        root.then(
                            CommandManager.literal(subcommand)
                                .requires { source ->
                                    val player = source.player

                                    player != null &&
                                            canUseEconomyTop(currencyType) &&
                                            hasEconomyCommandPermission(
                                                player,
                                                currencyType
                                            ) &&
                                            hasEconomyTopPermission(
                                                player,
                                                currencyType
                                            )
                                }
                                .executes { context ->
                                    openEconomyTop(
                                        context,
                                        currencyType
                                    )
                                }
                        )
                    }
                }

                dispatcher.register(root)
            }
        }
    }

    internal fun getEconomy(
        currencyType: String
    ): ConfigManager.EconomyConfig? {
        var current = currencyType
        val visited = mutableSetOf<String>()

        while (
            visited.add(
                current.lowercase(Locale.ROOT)
            )
        ) {
            config.economy.find {
                it.currencyType.equals(
                    current,
                    ignoreCase = true
                )
            }?.let {
                return it
            }

            current =
                renamedCurrencyTypes[
                    current.lowercase(Locale.ROOT)
                ] ?: return null
        }

        return null
    }

    private fun isEconomyAliasActive(
        currencyType: String,
        alias: String
    ): Boolean {
        val economy = getEconomy(currencyType)
            ?: return false

        val commands = economy.customCommands

        return commands.enabled &&
                commands.aliases.any {
                    it.trim()
                        .removePrefix("/")
                        .equals(
                            alias,
                            ignoreCase = true
                        )
                }
    }

    private fun resolveEconomyPermission(
        permission: String,
        currencyType: String
    ): String {
        val key = currencyType
            .lowercase(Locale.ROOT)
            .replace(
                Regex("[^a-z0-9._-]"),
                "_"
            )

        return permission.replace(
            "%currencyType%",
            key,
            ignoreCase = true
        )
    }

    private fun hasEconomyPermission(
        player: ServerPlayerEntity,
        currencyType: String,
        permission: String,
        level: Int
    ): Boolean {
        if (permission.isBlank()) return true

        return PermManager.hasPermission(
            player,
            resolveEconomyPermission(
                permission,
                currencyType
            ),
            level
        )
    }

    private fun hasEconomyCommandPermission(
        player: ServerPlayerEntity,
        currencyType: String
    ): Boolean {
        val economy = getEconomy(currencyType)
            ?: return false

        return hasEconomyPermission(
            player,
            economy.currencyType,
            economy.customCommands.permissions.command,
            2
        )
    }

    private fun hasEconomyBalancePermission(
        player: ServerPlayerEntity,
        currencyType: String
    ): Boolean {
        val economy = getEconomy(currencyType)
            ?: return false

        return hasEconomyPermission(
            player,
            economy.currencyType,
            economy.customCommands.permissions.balance,
            2
        )
    }

    private fun hasEconomyBalanceOthersPermission(
        player: ServerPlayerEntity,
        currencyType: String
    ): Boolean {
        val economy = getEconomy(currencyType)
            ?: return false

        return hasEconomyPermission(
            player,
            economy.currencyType,
            economy.customCommands.permissions.balanceOthers,
            3
        )
    }

    private fun hasEconomyTopPermission(player: ServerPlayerEntity, currencyType: String): Boolean {
        val economy = getEconomy(currencyType)
            ?: return false

        return hasEconomyPermission(player, economy.currencyType, economy.customCommands.permissions.top, 2)
    }

    private fun canUseEconomyBalance(
        currencyType: String
    ): Boolean {
        val economy = getEconomy(currencyType)
            ?: return false

        return economy.balance &&
                economy.customCommands.enabled &&
                economy.customCommands.balance
    }

    private fun canUseEconomyTop(
        currencyType: String
    ): Boolean {
        val economy = getEconomy(currencyType)
            ?: return false

        return economy.top &&
                economy.customCommands.enabled &&
                economy.customCommands.top
    }

    private fun canAccessEconomyCommand(player: ServerPlayerEntity, currencyType: String): Boolean {
        if (!hasEconomyCommandPermission(player, currencyType)) {
            return false
        }

        return (canUseEconomyBalance(currencyType) && hasEconomyBalancePermission(player, currencyType)) || (canUseEconomyTop(currencyType) && hasEconomyTopPermission(player, currencyType))
    }

    private fun executeEconomyDefaultAction(context: CommandContext<ServerCommandSource>, currencyType: String): Int {
        val player = context.source.player
            ?: return 0

        val economy = getEconomy(currencyType)
            ?: return 0

        val commands = economy.customCommands

        if (!commands.enabled) return 0

        if (!hasEconomyCommandPermission(player, currencyType)
        ) {
            return 0
        }

        return when (
            commands.defaultAction
                .lowercase(Locale.ROOT)
        ) {
            "balance" -> {
                if (!canUseEconomyBalance(currencyType) || !hasEconomyBalancePermission(player, currencyType)) {
                    return 0
                }

                showEconomyBalance(context, currencyType)
            }

            "top" -> {
                if (!canUseEconomyTop(currencyType) || !hasEconomyTopPermission(player, currencyType)) {
                    return 0
                }

                openEconomyTop(context, currencyType)
            }

            "none" -> 0

            else -> {
                println(
                    "[BEconomy] Invalid defaultAction '${commands.defaultAction}' for '$currencyType'."
                )
                0
            }
        }
    }

    private fun openEconomyTop(context: CommandContext<ServerCommandSource>, currencyType: String): Int {
        val player = context.source.player
            ?: return 0

        val economy = getEconomy(currencyType)
            ?: return 0

        if (
            !economy.top ||
            !economy.customCommands.top
        ) {
            return 0
        }

        MenuManager.openEconomyMenu(
            player = player,
            economy = economy
        )

        return 1
    }

    private fun replaceEconomyMessagePlaceholders(
        text: String,
        target: ServerPlayerEntity,
        viewer: ServerPlayerEntity?,
        economy: ConfigManager.EconomyConfig
    ): String {
        val balance = ConfigManager.getBalance(
            target.uuid,
            economy.currencyType
        )

        val api = BEconomy.getAPI()

        val inventoryBalance = api.getInventoryValue(
            target.uuid,
            economy.currencyType
        )

        val effectiveBalance = api.getEffectiveBalance(
            target.uuid,
            economy.currencyType
        )

        val replacements = mapOf(
            "%player%" to target.name.string,
            "%uuid%" to target.uuid.toString(),
            "%viewer%" to (viewer?.name?.string ?: target.name.string),
            "%currency%" to economy.name,
            "%currencyType%" to economy.currencyType,
            "%symbol%" to economy.symbol,
            "%balance%" to ConfigManager.NumberFormatter.format(balance),
            "%raw_balance%" to balance.toPlainString(),
            "%inventory_balance%" to ConfigManager.NumberFormatter.format(inventoryBalance),
            "%raw_inventory_balance%" to inventoryBalance.toPlainString(),
            "%effective_balance%" to ConfigManager.NumberFormatter.format(effectiveBalance),
            "%raw_effective_balance%" to effectiveBalance.toPlainString()
        )

        return replacements.entries.fold(text) {
                result,
                (placeholder, value) ->
            result.replace(
                placeholder,
                value
            )
        }
    }

    private fun sendEconomyBalanceMessage(
        viewer: ServerPlayerEntity,
        target: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig
    ) {
        val economyMenu =
            MenuConfigManager.get(
                economy.currencyType
            )

        if (
            economyMenu == null ||
            !economyMenu.balanceMessage.enabled
        ) {
            val message = Text.literal("")
                .append(MessageManager.get("balanceHeader"))
                .append("\n")

            val balance =
                ConfigManager.getBalance(target.uuid, economy.currencyType)

            message.append(
                MessageManager.get(
                    "currencyLineFormat",
                    "currency" to economy.currencyType,
                    "amount" to ConfigManager.NumberFormatter.format(balance),
                    "symbol" to economy.symbol
                )
            )

            viewer.sendMessage(
                message,
                false
            )

            return
        }

        val message = Text.literal("")

        economyMenu.balanceMessage.lines
            .forEachIndexed { index, line ->
                val replaced = replaceEconomyMessagePlaceholders(line, target, viewer, economy)

                message.append(processAllFormats(replaced, target))

                if (index < economyMenu.balanceMessage.lines.lastIndex) {
                    message.append("\n")
                }
            }

        viewer.sendMessage(
            message,
            false
        )
    }

    private fun showEconomyBalance(context: CommandContext<ServerCommandSource>, currencyType: String): Int {
        val player = context.source.player
            ?: return 0

        val economy = getEconomy(currencyType)
        if (economy == null) { player.sendMessage(MessageManager.get("currencyNotFound", "currencyType" to currencyType))

            return 0
        }

        if (!economy.balance || !economy.customCommands.balance) {
            return 0
        }

        sendEconomyBalanceMessage(
            viewer = player,
            target = player,
            economy = economy
        )

        return 1
    }

    private fun showTargetEconomyBalance(
        context: CommandContext<ServerCommandSource>,
        currencyType: String
    ): Int {
        val viewer = context.source.player
            ?: return 0

        val economy = getEconomy(currencyType)
            ?: return 0

        if (!economy.balance || !economy.customCommands.balance) {
            return 0
        }

        val targetName = StringArgumentType.getString(context, "player")
        val target = context.source.server.playerManager.getPlayer(targetName)

        if (target == null) {
            viewer.sendMessage(
                MessageManager.get("playerNotFound", "player" to targetName))

            return 0
        }

        sendEconomyBalanceMessage(
            viewer = viewer,
            target = target,
            economy = economy
        )

        return 1
    }
}