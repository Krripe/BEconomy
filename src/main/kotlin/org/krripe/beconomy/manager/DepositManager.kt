package org.krripe.beconomy.manager

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import org.krripe.beconomy.api.BEconomy
import org.krripe.beconomy.config.ConfigManager
import org.krripe.beconomy.config.DepositConfigManager
import org.krripe.beconomy.utils.CustomColorParser.processAllFormats
import org.krripe.beconomy.utils.DepositGuiInteraction
import org.krripe.beconomy.utils.GuiItem
import org.krripe.beconomy.utils.openDepositGui
import org.krripe.beconomy.utils.updateOpenDepositGui
import java.math.BigDecimal
import java.util.Locale

object DepositManager {

    private data class DepositState(
        var depositedItems: Int = 0,
        var depositedValue: BigDecimal = BigDecimal.ZERO
    )

    internal fun registerCommands(
        dispatcher: CommandDispatcher<ServerCommandSource>
    ) {
        val registeredAliases =
            mutableSetOf<String>()

        ConfigManager.config.economy.forEach { economy ->
            val depositConfig =
                DepositConfigManager.get(
                    economy.currencyType
                ) ?: return@forEach

            val commandConfig =
                depositConfig.commands

            if (!commandConfig.enabled) {
                return@forEach
            }

            commandConfig.aliases.forEach aliasLoop@{ configuredAlias ->
                val alias =
                    configuredAlias
                        .trim()
                        .removePrefix("/")
                        .lowercase(
                            Locale.ROOT
                        )

                if (
                    alias.isBlank() ||
                    alias.contains(" ")
                ) {
                    println(
                        "[BEconomy] Invalid deposit command alias '$configuredAlias' for '${economy.currencyType}'."
                    )

                    return@aliasLoop
                }

                if (
                    !registeredAliases.add(
                        alias
                    )
                ) {
                    println(
                        "[BEconomy] Duplicate deposit command alias '/$alias' for '${economy.currencyType}'."
                    )

                    return@aliasLoop
                }

                if (
                    dispatcher.root.getChild(
                        alias
                    ) != null
                ) {
                    println(
                        "[BEconomy] Deposit command alias '/$alias' for '${economy.currencyType}' conflicts with an existing command."
                    )

                    return@aliasLoop
                }

                val permission =
                    resolvePermission(
                        commandConfig.permission,
                        economy.currencyType
                    )

                dispatcher.register(
                    CommandManager.literal(
                        alias
                    )
                        .requires { source ->
                            val player =
                                source.player

                            player != null &&
                                    PermManager.hasPermission(
                                        player,
                                        permission,
                                        2
                                    )
                        }
                        .executes { context ->
                            val player =
                                context.source.player
                                    ?: return@executes 0

                            val currentEconomy =
                                ConfigManager.config.economy.find {
                                    it.currencyType.equals(
                                        economy.currencyType,
                                        ignoreCase = true
                                    )
                                } ?: return@executes 0

                            val currentConfig =
                                DepositConfigManager.get(
                                    currentEconomy.currencyType
                                ) ?: return@executes 0

                            if (
                                !currentConfig.enabled ||
                                !currentConfig.commands.enabled
                            ) {
                                return@executes 0
                            }

                            if (
                                openDepositMenu(
                                    player,
                                    currentEconomy.currencyType
                                )
                            ) {
                                1
                            } else {
                                0
                            }
                        }
                )
            }
        }
    }

    internal fun openDepositMenu(
        player: ServerPlayerEntity,
        currencyType: String
    ): Boolean {
        val economy =
            ConfigManager.config.economy.find {
                it.currencyType.equals(
                    currencyType,
                    ignoreCase = true
                )
            } ?: return false

        val depositConfig =
            DepositConfigManager.get(
                economy.currencyType
            ) ?: DepositConfigManager.load(
                economy.currencyType
            )

        if (!depositConfig.enabled) {
            return false
        }

        val size =
            if (
                depositConfig.size in 9..54 &&
                depositConfig.size % 9 == 0
            ) {
                depositConfig.size
            } else {
                println(
                    "[BEconomy] Invalid deposit GUI size '${depositConfig.size}' for '${economy.currencyType}'. Using 27."
                )

                27
            }

        val depositSlots =
            GuiManager.parseSlots(
                depositConfig.depositSlots,
                size
            ).toSet()

        if (
            depositConfig.depositSlots.isNotEmpty() &&
            depositSlots.isEmpty()
        ) {
            println(
                "[BEconomy] Deposit GUI for '${economy.currencyType}' has no valid deposit slots."
            )
        }

        val validItems =
            depositConfig.items.filter { item ->
                val type =
                    item.type.trim()
                        .lowercase(
                            Locale.ROOT
                        )

                val valid =
                    type == "item" ||
                            type == "button"

                if (!valid) {
                    println(
                        "[BEconomy] Invalid deposit GUI item type '${item.type}' for '${economy.currencyType}'."
                    )
                }

                valid
            }

        depositConfig.fillers.forEach { filler ->
            if (
                filler.slots.isNotEmpty() &&
                GuiManager.parseSlots(
                    filler.slots,
                    size
                ).isEmpty()
            ) {
                println(
                    "[BEconomy] Deposit GUI filler has no valid slots for '${economy.currencyType}'."
                )
            }
        }

        validItems.forEach { item ->
            if (
                item.slots.isNotEmpty() &&
                GuiManager.parseSlots(
                    item.slots,
                    size
                ).isEmpty()
            ) {
                println(
                    "[BEconomy] Deposit GUI item '${item.name}' has no valid slots for '${economy.currencyType}'."
                )
            }
        }

        val state =
            DepositState()

        fun buildItems(): List<GuiItem> {
            val guiItems =
                MutableList<GuiItem?>(
                    size
                ) {
                    null
                }

            val replacements =
                getReplacements(
                    player,
                    economy,
                    state
                )

            if (
                depositConfig.fillersEnabled
            ) {
                depositConfig.fillers.forEach { filler ->
                    val stack =
                        createFiller(
                            filler,
                            player,
                            economy,
                            replacements
                        ) ?: return@forEach

                    GuiManager.parseSlots(
                        filler.slots,
                        size
                    ).forEach { slot ->
                        if (
                            slot !in depositSlots &&
                            guiItems[slot] == null
                        ) {
                            guiItems[slot] =
                                GuiItem(
                                    stack.copy(),
                                    slot
                                )
                        }
                    }
                }
            }

            validItems.forEach { item ->
                val stack =
                    createItem(
                        item,
                        player,
                        economy,
                        replacements
                    ) ?: return@forEach

                GuiManager.parseSlots(
                    item.slots,
                    size
                ).forEach { slot ->
                    if (
                        slot !in depositSlots
                    ) {
                        guiItems[slot] =
                            GuiItem(
                                stack.copy(),
                                slot
                            )
                    }
                }
            }

            return guiItems.filterNotNull()
        }

        fun refresh() {
            updateOpenDepositGui(
                player,
                buildItems(),
                size
            )
        }

        val titleReplacements =
            getReplacements(
                player,
                economy,
                state
            )

        val title =
            processAllFormats(
                replacePlaceholders(
                    depositConfig.title,
                    titleReplacements
                ),
                player
            ).styled {
                it.withItalic(false)
            }

        openDepositGui(
            player = player,
            title = title,
            guiItems = buildItems(),
            inventorySize = size,
            currencyType = economy.currencyType,
            depositSlots = depositSlots,
            behavior = depositConfig.behavior,
            interaction =
                object :
                    DepositGuiInteraction {

                    override fun onDeposit(
                        player: ServerPlayerEntity,
                        result: EcoManager.DepositResult
                    ) {
                        state.depositedItems +=
                            result.items

                        state.depositedValue =
                            state.depositedValue.add(
                                result.value
                            )

                        sendDepositFeedback(
                            player = player,
                            economy = economy,
                            state = state,
                            feedback = depositConfig.feedback,
                            result = result,
                            success = true
                        )

                        refresh()
                    }

                    override fun onInvalidDeposit(
                        player: ServerPlayerEntity
                    ) {
                        sendDepositFeedback(
                            player = player,
                            economy = economy,
                            state = state,
                            feedback = depositConfig.feedback,
                            result = null,
                            success = false
                        )
                    }

                    override fun onItemClick(
                        slot: Int,
                        player: ServerPlayerEntity,
                        rightClick: Boolean
                    ) {
                        val item =
                            getItemAtSlot(
                                validItems,
                                slot,
                                size,
                                depositSlots
                            ) ?: return

                        if (
                            !item.type.equals(
                                "button",
                                ignoreCase = true
                            )
                        ) {
                            return
                        }

                        val action =
                            if (rightClick) {
                                item.actions.rightClick
                            } else {
                                item.actions.leftClick
                            }

                        executeAction(
                            action = action,
                            player = player,
                            economy = economy,
                            behavior = depositConfig.behavior,
                            state = state,
                            refresh = ::refresh
                        )
                    }

                    override fun onGuiClose(
                        player: ServerPlayerEntity
                    ) {}
                }
        )

        return true
    }

    private fun getItemAtSlot(
        items: List<DepositConfigManager.ItemConfig>,
        slot: Int,
        size: Int,
        depositSlots: Set<Int>
    ): DepositConfigManager.ItemConfig? {
        if (slot in depositSlots) {
            return null
        }

        return items.asReversed()
            .firstOrNull {
                slot in GuiManager.parseSlots(
                    it.slots,
                    size
                )
            }
    }

    private fun executeAction(
        action: DepositConfigManager.ActionConfig,
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig,
        behavior: DepositConfigManager.BehaviorConfig,
        state: DepositState,
        refresh: () -> Unit
    ) {
        playSound(
            action.sound,
            player,
            economy
        )

        var closed = false

        when (
            action.type.trim()
                .lowercase(
                    Locale.ROOT
                )
        ) {
            "", "none" -> Unit

            "close" -> {
                player.closeHandledScreen()
                closed = true
            }

            "refresh" -> {
                refresh()
            }

            "deposit_all" -> {
                val result =
                    depositAll(
                        player,
                        economy.currencyType,
                        behavior.depositAllIncludesHotbar
                    )

                if (result != null) {
                    state.depositedItems +=
                        result.items

                    state.depositedValue =
                        state.depositedValue.add(
                            result.value
                        )

                    sendDepositFeedback(
                        player = player,
                        economy = economy,
                        state = state,
                        feedback = DepositConfigManager.get(
                            economy.currencyType
                        )!!.feedback,
                        result = result,
                        success = true
                    )

                    refresh()
                } else {
                    sendDepositFeedback(
                        player = player,
                        economy = economy,
                        state = state,
                        feedback = DepositConfigManager.get(
                            economy.currencyType
                        )!!.feedback,
                        result = null,
                        success = false
                    )
                }
            }

            "player_command" -> {
                executePlayerCommand(
                    action,
                    player,
                    economy,
                    state
                )
            }

            "console_command" -> {
                executeConsoleCommand(
                    action,
                    player,
                    economy,
                    state
                )
            }

            else -> {
                println(
                    "[BEconomy] Unknown deposit GUI action '${action.type}' for '${economy.currencyType}'."
                )
            }
        }

        if (
            !closed &&
            action.closeAfterAction
        ) {
            player.closeHandledScreen()
        }
    }

    private fun depositAll(
        player: ServerPlayerEntity,
        currencyType: String,
        includeHotbar: Boolean
    ): EcoManager.DepositResult? {
        var totalItems = 0
        var totalValue =
            BigDecimal.ZERO

        for (
        slot in
        player.inventory.main.indices
        ) {
            if (
                !includeHotbar &&
                slot in 0..8
            ) {
                continue
            }

            val stack =
                player.inventory.getStack(
                    slot
                )

            if (stack.isEmpty) {
                continue
            }

            val result =
                EcoManager.depositStack(
                    player,
                    currencyType,
                    stack,
                    stack.count
                ) ?: continue

            totalItems +=
                result.items

            totalValue =
                totalValue.add(
                    result.value
                )

            if (stack.isEmpty) {
                player.inventory.setStack(
                    slot,
                    ItemStack.EMPTY
                )
            }
        }

        if (
            totalItems <= 0 ||
            totalValue <= BigDecimal.ZERO
        ) {
            return null
        }

        player.inventory.markDirty()
        player.currentScreenHandler.sendContentUpdates()

        return EcoManager.DepositResult(
            items = totalItems,
            value = totalValue
        )
    }

    private fun executePlayerCommand(
        action: DepositConfigManager.ActionConfig,
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig,
        state: DepositState
    ) {
        if (action.value.isBlank()) {
            return
        }

        val command =
            replacePlaceholders(
                action.value,
                getReplacements(
                    player,
                    economy,
                    state
                )
            ).trim()

        if (command.isBlank()) {
            return
        }

        runCatching {
            player.server.commandManager
                .executeWithPrefix(
                    player.commandSource,
                    command
                )
        }.onFailure {
            println(
                "[BEconomy] Failed deposit player_command '$command' for '${economy.currencyType}': ${it.message}"
            )
        }
    }

    private fun executeConsoleCommand(
        action: DepositConfigManager.ActionConfig,
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig,
        state: DepositState
    ) {
        if (action.value.isBlank()) {
            return
        }

        val command =
            replacePlaceholders(
                action.value,
                getReplacements(
                    player,
                    economy,
                    state
                )
            ).trim()

        if (command.isBlank()) {
            return
        }

        runCatching {
            player.server.commandManager
                .executeWithPrefix(
                    player.server.commandSource,
                    command
                )
        }.onFailure {
            println(
                "[BEconomy] Failed deposit console_command '$command' for '${economy.currencyType}': ${it.message}"
            )
        }
    }

    private fun createFiller(
        config: DepositConfigManager.FillerConfig,
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig,
        replacements: Map<String, String>
    ): ItemStack? {
        val stack =
            createBaseStack(
                config.material,
                economy.currencyType,
                "deposit filler"
            ) ?: return null

        applyStackDisplay(
            stack = stack,
            player = player,
            name = config.name,
            lore = config.lore,
            customModelData = config.customModelData,
            glow = config.glow,
            hideAdditionalTooltip =
                config.hideAdditionalTooltip,
            hideTooltip =
                config.hideTooltip,
            replacements = replacements
        )

        return stack
    }

    private fun createItem(
        config: DepositConfigManager.ItemConfig,
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig,
        replacements: Map<String, String>
    ): ItemStack? {
        val stack =
            createBaseStack(
                config.material,
                economy.currencyType,
                "deposit item"
            ) ?: return null

        applyStackDisplay(
            stack = stack,
            player = player,
            name = config.name,
            lore = config.lore,
            customModelData = config.customModelData,
            glow = config.glow,
            hideAdditionalTooltip =
                config.hideAdditionalTooltip,
            hideTooltip =
                config.hideTooltip,
            replacements = replacements
        )

        return stack
    }

    private fun createBaseStack(
        materialId: String,
        currencyType: String,
        source: String
    ): ItemStack? {
        val id =
            runCatching {
                Identifier.of(
                    materialId.trim()
                )
            }.getOrNull()

        if (id == null) {
            println(
                "[BEconomy] Invalid material '$materialId' in $source for '$currencyType'."
            )

            return null
        }

        val item =
            Registries.ITEM
                .getOrEmpty(id)
                .orElse(null)

        if (
            item == null ||
            item == Items.AIR
        ) {
            println(
                "[BEconomy] Unknown material '$materialId' in $source for '$currencyType'."
            )

            return null
        }

        return ItemStack(item)
    }

    private fun applyStackDisplay(
        stack: ItemStack,
        player: ServerPlayerEntity,
        name: String,
        lore: List<String>,
        customModelData: Int?,
        glow: Boolean,
        hideAdditionalTooltip: Boolean,
        hideTooltip: Boolean,
        replacements: Map<String, String>
    ) {
        stack.set(
            DataComponentTypes.CUSTOM_NAME,
            processAllFormats(
                replacePlaceholders(
                    name,
                    replacements
                ),
                player
            ).styled {
                it.withItalic(false)
            }
        )

        customModelData
            ?.takeIf {
                it > 0
            }
            ?.let {
                stack.set(
                    DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelDataComponent(
                        it
                    )
                )
            }

        if (lore.isNotEmpty()) {
            stack.set(
                DataComponentTypes.LORE,
                LoreComponent(
                    lore.map {
                        processAllFormats(
                            replacePlaceholders(
                                it,
                                replacements
                            ),
                            player
                        ).styled { style ->
                            style.withItalic(false)
                        }
                    }
                )
            )
        }

        if (glow) {
            stack.set(
                DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,
                true
            )
        }

        if (hideAdditionalTooltip) {
            stack.set(
                DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP,
                net.minecraft.util.Unit.INSTANCE
            )
        }

        if (hideTooltip) {
            stack.set(
                DataComponentTypes.HIDE_TOOLTIP,
                net.minecraft.util.Unit.INSTANCE
            )
        }
    }

    private fun sendDepositFeedback(
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig,
        state: DepositState,
        feedback: DepositConfigManager.FeedbackConfig,
        result: EcoManager.DepositResult?,
        success: Boolean
    ) {
        val sound =
            if (success) {
                feedback.successSound
            } else {
                feedback.invalidSound
            }

        playSound(
            sound,
            player,
            economy
        )

        val message =
            if (success) {
                feedback.successMessage
            } else {
                feedback.invalidMessage
            }

        if (message.isBlank()) {
            return
        }

        val replacements =
            getReplacements(
                player,
                economy,
                state
            ).toMutableMap()

        replacements["%amount%"] =
            ConfigManager.NumberFormatter.format(
                result?.value ?: BigDecimal.ZERO
            )

        replacements["%raw_amount%"] =
            result?.value
                ?.toPlainString()
                ?: "0"

        replacements["%items%"] =
            result?.items
                ?.toString()
                ?: "0"

        player.sendMessage(
            processAllFormats(
                replacePlaceholders(
                    message,
                    replacements
                ),
                player
            )
        )
    }

    private fun playSound(
        config: DepositConfigManager.SoundConfig?,
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig
    ) {
        if (
            config == null ||
            !config.enabled ||
            config.id.isBlank()
        ) {
            return
        }

        val id =
            runCatching {
                Identifier.of(
                    config.id.trim()
                )
            }.getOrNull()

        if (id == null) {
            println(
                "[BEconomy] Invalid deposit sound '${config.id}' for '${economy.currencyType}'."
            )

            return
        }

        val sound =
            Registries.SOUND_EVENT
                .getOrEmpty(id)
                .orElse(null)

        if (sound == null) {
            println(
                "[BEconomy] Unknown deposit sound '${config.id}' for '${economy.currencyType}'."
            )

            return
        }

        player.playSoundToPlayer(
            sound,
            SoundCategory.MASTER,
            config.volume.coerceAtLeast(
                0f
            ),
            config.pitch.coerceAtLeast(
                0.01f
            )
        )
    }

    private fun getReplacements(
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig,
        state: DepositState
    ): Map<String, String> {
        val balance =
            ConfigManager.getBalance(
                player.uuid,
                economy.currencyType
            )

        val api =
            BEconomy.getAPI()

        val inventoryBalance =
            api.getInventoryValue(
                player.uuid,
                economy.currencyType
            )

        val effectiveBalance =
            api.getEffectiveBalance(
                player.uuid,
                economy.currencyType
            )

        return mapOf(
            "%player%" to
                    player.name.string,

            "%uuid%" to
                    player.uuid.toString(),

            "%currency%" to
                    economy.name,

            "%currencyType%" to
                    economy.currencyType,

            "%symbol%" to
                    economy.symbol,

            "%balance%" to
                    ConfigManager.NumberFormatter.format(
                        balance
                    ),

            "%raw_balance%" to
                    balance.toPlainString(),

            "%inventory_balance%" to
                    ConfigManager.NumberFormatter.format(
                        inventoryBalance
                    ),

            "%raw_inventory_balance%" to
                    inventoryBalance.toPlainString(),

            "%effective_balance%" to
                    ConfigManager.NumberFormatter.format(
                        effectiveBalance
                    ),

            "%raw_effective_balance%" to
                    effectiveBalance.toPlainString(),

            "%deposited_amount%" to
                    ConfigManager.NumberFormatter.format(
                        state.depositedValue
                    ),

            "%raw_deposited_amount%" to
                    state.depositedValue
                        .toPlainString(),

            "%deposited_items%" to
                    state.depositedItems
                        .toString()
        )
    }

    private fun resolvePermission(
        permission: String,
        currencyType: String
    ): String {
        return permission.replace(
            "%currencyType%",
            currencyType.lowercase(
                Locale.ROOT
            )
        )
    }

    private fun replacePlaceholders(
        text: String,
        replacements: Map<String, String>
    ): String {
        return replacements.entries
            .fold(text) {
                    result,
                    (placeholder, value) ->

                result.replace(
                    placeholder,
                    value
                )
            }
    }
}