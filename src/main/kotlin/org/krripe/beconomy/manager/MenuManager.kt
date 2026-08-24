package org.krripe.beconomy.manager

import com.mojang.authlib.GameProfile
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent
import net.minecraft.component.type.LoreComponent
import net.minecraft.component.type.ProfileComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import org.krripe.beconomy.api.BEconomy
import org.krripe.beconomy.config.ConfigManager
import org.krripe.beconomy.config.MenuConfigManager
import org.krripe.beconomy.utils.CustomColorParser.processAllFormats
import org.krripe.beconomy.utils.GuiInteraction
import org.krripe.beconomy.utils.GuiItem
import org.krripe.beconomy.utils.getGuiScreenHandlerType
import org.krripe.beconomy.utils.openGui
import org.krripe.beconomy.utils.updateOpenGui
import java.util.Locale

object MenuManager {

    private data class EconomyMenuState(
        var page: Int,
        var sort: GuiManager.TopSort
    )

    private data class EconomyMenuClickedPlayer(
        val data: GuiManager.PlayerBalance,
        val position: Int
    )

    private fun parseTopSort(value: String): GuiManager.TopSort? {
        return runCatching {
            GuiManager.TopSort.valueOf(value.trim().uppercase(Locale.ROOT))
        }.getOrNull()
    }

    private fun getEconomyMenuSortModes(
        config: MenuConfigManager.EconomyMenuConfig
    ): List<GuiManager.TopSort> {
        return config.menu.leaderboard.sortModes
            .mapNotNull { parseTopSort(it) }
            .distinct()
            .ifEmpty { listOf(GuiManager.TopSort.PRICE_HIGH) }
    }

    private fun createEconomyMenuBaseStack(
        materialId: String,
        currencyType: String,
        source: String
    ): ItemStack? {
        val id = runCatching {
            Identifier.of(materialId.trim())
        }.getOrNull()

        if (id == null) {
            println("[BEconomy] Invalid material '$materialId' in $source for '$currencyType'.")
            return null
        }

        val item = runCatching {
            Registries.ITEM.get(id)
        }.getOrNull()

        if (item == null || item == Items.AIR) {
            println("[BEconomy] Unknown material '$materialId' in $source for '$currencyType'.")
            return null
        }

        return ItemStack(item)
    }

    private fun createEconomyMenuFiller(
        cfg: MenuConfigManager.FillerConfig,
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig,
        replacements: Map<String, String>
    ): ItemStack? {
        val stack = createEconomyMenuBaseStack(
            cfg.material,
            economy.currencyType,
            "filler"
        ) ?: return null

        stack.set(
            DataComponentTypes.CUSTOM_NAME,
            processAllFormats(
                replaceEconomyMenuPlaceholders(cfg.name, replacements),
                player
            ).styled { it.withItalic(false) }
        )

        cfg.customModelData?.takeIf { it > 0 }?.let {
            stack.set(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelDataComponent(it)
            )
        }

        if (cfg.lore.isNotEmpty()) {
            stack.set(
                DataComponentTypes.LORE,
                LoreComponent(
                    cfg.lore.map {
                        processAllFormats(
                            replaceEconomyMenuPlaceholders(it, replacements),
                            player
                        ).styled { style -> style.withItalic(false) }
                    }
                )
            )
        }

        if (cfg.glow) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        }

        if (cfg.hideAdditionalTooltip) {
            stack.set(
                DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP,
                net.minecraft.util.Unit.INSTANCE
            )
        }

        if (cfg.hideTooltip) {
            stack.set(
                DataComponentTypes.HIDE_TOOLTIP,
                net.minecraft.util.Unit.INSTANCE
            )
        }

        return stack
    }

    private fun createEconomyMenuItem(
        cfg: MenuConfigManager.MenuItemConfig,
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig,
        replacements: Map<String, String>
    ): ItemStack? {
        val stack = createEconomyMenuBaseStack(
            cfg.material,
            economy.currencyType,
            "menu item"
        ) ?: return null

        stack.set(
            DataComponentTypes.CUSTOM_NAME,
            processAllFormats(
                replaceEconomyMenuPlaceholders(cfg.name, replacements),
                player
            ).styled { it.withItalic(false) }
        )

        cfg.customModelData?.takeIf { it > 0 }?.let {
            stack.set(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelDataComponent(it)
            )
        }

        if (cfg.lore.isNotEmpty()) {
            stack.set(
                DataComponentTypes.LORE,
                LoreComponent(
                    cfg.lore.map {
                        processAllFormats(
                            replaceEconomyMenuPlaceholders(it, replacements),
                            player
                        ).styled { style -> style.withItalic(false) }
                    }
                )
            )
        }

        if (cfg.glow) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        }

        if (cfg.hideAdditionalTooltip) {
            stack.set(
                DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP,
                net.minecraft.util.Unit.INSTANCE
            )
        }

        if (cfg.hideTooltip) {
            stack.set(
                DataComponentTypes.HIDE_TOOLTIP,
                net.minecraft.util.Unit.INSTANCE
            )
        }

        return stack
    }

    private fun createEconomyLeaderboardHead(
        viewer: ServerPlayerEntity,
        playerData: GuiManager.PlayerBalance,
        position: Int,
        economy: ConfigManager.EconomyConfig,
        menuConfig: MenuConfigManager.EconomyMenuConfig,
        replacements: Map<String, String>
    ): ItemStack {
        val onlinePlayer = viewer.server.playerManager.getPlayer(playerData.uuid)
        val stack = ItemStack(
            Registries.ITEM.get(
                Identifier.of("minecraft:player_head")
            )
        )

        val displayName = when {
            onlinePlayer != null -> onlinePlayer.name.string
            playerData.name.equals("Unknown", ignoreCase = true) -> "Unknown"
            GuiManager.uuid_like_regex.matches(playerData.name) -> "Unknown"
            else -> playerData.name
        }

        val cachedProfile = onlinePlayer?.gameProfile
            ?: viewer.server.userCache?.getByUuid(playerData.uuid)?.orElse(null)

        val profileName = when {
            cachedProfile?.name?.isNotBlank() == true -> cachedProfile.name
            displayName != "Unknown" -> displayName
            else -> "Player"
        }.take(16)

        val profile = cachedProfile?.takeIf { it.name == profileName }
            ?: GameProfile(playerData.uuid, profileName)

        stack.set(
            DataComponentTypes.PROFILE,
            ProfileComponent(profile)
        )

        val head = menuConfig.menu.leaderboard.head

        val headReplacements = replacements + mapOf(
            "%position%" to position.toString(),
            "%top_player%" to displayName,
            "%playerName%" to displayName,
            "%top_uuid%" to playerData.uuid.toString(),
            "%top_balance%" to ConfigManager.NumberFormatter.format(playerData.balance),
            "%raw_top_balance%" to playerData.balance.toPlainString(),
            "%status%" to if (onlinePlayer != null) "&aOnline" else "&cOffline",
            "%clicked_player%" to displayName,
            "%clicked_uuid%" to playerData.uuid.toString(),
            "%clicked_balance%" to ConfigManager.NumberFormatter.format(playerData.balance),
            "%raw_clicked_balance%" to playerData.balance.toPlainString(),
            "%clicked_position%" to position.toString(),
            "%clicked_status%" to if (onlinePlayer != null) "&aOnline" else "&cOffline"
        )

        stack.set(
            DataComponentTypes.CUSTOM_NAME,
            processAllFormats(
                replaceEconomyMenuPlaceholders(
                    head.name,
                    headReplacements
                ),
                onlinePlayer ?: viewer
            ).styled { it.withItalic(false) }
        )

        if (head.lore.isNotEmpty()) {
            stack.set(
                DataComponentTypes.LORE,
                LoreComponent(
                    head.lore.map {
                        processAllFormats(
                            replaceEconomyMenuPlaceholders(
                                it,
                                headReplacements
                            ),
                            onlinePlayer ?: viewer
                        ).styled { style -> style.withItalic(false) }
                    }
                )
            )
        }

        head.customModelData?.takeIf { it > 0 }?.let {
            stack.set(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelDataComponent(it)
            )
        }

        if (head.glow) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        }

        if (head.hideAdditionalTooltip) {
            stack.set(
                DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP,
                net.minecraft.util.Unit.INSTANCE
            )
        }

        if (head.hideTooltip) {
            stack.set(
                DataComponentTypes.HIDE_TOOLTIP,
                net.minecraft.util.Unit.INSTANCE
            )
        }

        return stack
    }

    private fun isEconomyMenuMaterialValid(materialId: String): Boolean {
        val id = runCatching {
            Identifier.of(materialId.trim())
        }.getOrNull() ?: return false

        return Registries.ITEM.getOrEmpty(id)
            .map { it != Items.AIR }
            .orElse(false)
    }

    private fun getEconomyMenuItemAtSlot(
        items: List<MenuConfigManager.MenuItemConfig>,
        slot: Int,
        size: Int,
        state: EconomyMenuState,
        maxPage: Int,
        leaderboardEnabled: Boolean,
        sortModes: List<GuiManager.TopSort>
    ): MenuConfigManager.MenuItemConfig? {
        return items.asReversed().firstOrNull {
            slot in GuiManager.parseSlots(it.slots, size) &&
                    shouldRenderEconomyMenuItem(
                        it,
                        state,
                        maxPage,
                        leaderboardEnabled,
                        sortModes
                    ) &&
                    isEconomyMenuMaterialValid(it.material)
        }
    }

    private fun shouldRenderEconomyMenuItem(
        item: MenuConfigManager.MenuItemConfig,
        state: EconomyMenuState,
        maxPage: Int,
        leaderboardEnabled: Boolean,
        sortModes: List<GuiManager.TopSort>
    ): Boolean {
        if (!item.type.equals("button", ignoreCase = true)) return true

        val types = listOf(
            item.actions.leftClick.type.trim().lowercase(Locale.ROOT),
            item.actions.rightClick.type.trim().lowercase(Locale.ROOT)
        ).filter { it.isNotBlank() && it != "none" }

        if (types.isEmpty()) return true

        val contextual = setOf(
            "next_page",
            "previous_page",
            "change_sort"
        )

        if (types.any { it !in contextual }) return true

        return types.any {
            when (it) {
                "next_page" -> leaderboardEnabled && state.page < maxPage
                "previous_page" -> leaderboardEnabled && state.page > 0
                "change_sort" -> leaderboardEnabled && sortModes.size > 1
                else -> false
            }
        }
    }

    private fun playEconomyMenuSound(
        soundConfig: MenuConfigManager.SoundConfig?,
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig
    ) {
        if (soundConfig == null || !soundConfig.enabled || soundConfig.id.isBlank()) return

        val id = runCatching {
            Identifier.of(soundConfig.id.trim())
        }.getOrNull()

        if (id == null) {
            println("[BEconomy] Invalid sound '${soundConfig.id}' for '${economy.currencyType}'.")
            return
        }

        val sound = Registries.SOUND_EVENT.getOrEmpty(id).orElse(null)

        if (sound == null) {
            println("[BEconomy] Unknown sound '${soundConfig.id}' for '${economy.currencyType}'.")
            return
        }

        player.playSoundToPlayer(
            sound,
            SoundCategory.MASTER,
            soundConfig.volume.coerceAtLeast(0f),
            soundConfig.pitch.coerceAtLeast(0.01f)
        )
    }

    private fun executeEconomyMenuAction(
        action: MenuConfigManager.ActionConfig,
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig,
        state: EconomyMenuState,
        sortModes: List<GuiManager.TopSort>,
        maxPage: Int,
        leaderboardEnabled: Boolean,
        refresh: (Int) -> Unit,
        clicked: EconomyMenuClickedPlayer? = null
    ) {
        playEconomyMenuSound(action.sound, player, economy)

        when (action.type.trim().lowercase(Locale.ROOT)) {
            "", "none" -> Unit

            "close" -> player.closeHandledScreen()

            "refresh" -> refresh(state.page)

            "next_page" -> {
                if (leaderboardEnabled && state.page < maxPage) {
                    refresh(state.page + 1)
                }
            }

            "previous_page" -> {
                if (leaderboardEnabled && state.page > 0) {
                    refresh(state.page - 1)
                }
            }

            "change_sort" -> {
                if (!leaderboardEnabled || sortModes.size <= 1) return

                val index = sortModes.indexOf(state.sort)

                state.sort = if (index == -1) {
                    sortModes.first()
                } else {
                    sortModes[(index + 1) % sortModes.size]
                }

                state.page = 0
                refresh(0)
            }

            "player_command" -> {
                if (action.value.isBlank()) return

                val replacements = getEconomyMenuReplacements(
                    player,
                    economy,
                    state,
                    maxPage,
                    clicked
                )

                val command = replaceEconomyMenuPlaceholders(
                    action.value,
                    replacements
                ).trim()

                if (command.isBlank()) return

                runCatching {
                    player.server.commandManager.executeWithPrefix(
                        player.commandSource,
                        command
                    )
                }.onFailure {
                    println(
                        "[BEconomy] Failed player_command '$command' for '${economy.currencyType}': ${it.message}"
                    )
                }

                if (action.closeAfterAction) {
                    player.closeHandledScreen()
                }
            }

            "console_command" -> {
                if (action.value.isBlank()) return

                val replacements = getEconomyMenuReplacements(
                    player,
                    economy,
                    state,
                    maxPage,
                    clicked
                )

                val command = replaceEconomyMenuPlaceholders(
                    action.value,
                    replacements
                ).trim()

                if (command.isBlank()) return

                runCatching {
                    player.server.commandManager.executeWithPrefix(
                        player.server.commandSource,
                        command
                    )
                }.onFailure {
                    println(
                        "[BEconomy] Failed console_command '$command' for '${economy.currencyType}': ${it.message}"
                    )
                }

                if (action.closeAfterAction) {
                    player.closeHandledScreen()
                }
            }

            else -> {
                println(
                    "[BEconomy] Unknown economy menu action '${action.type}' for '${economy.currencyType}'."
                )
            }
        }
    }

    internal fun openEconomyMenu(
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig,
        initialPage: Int = 0,
        initialSort: GuiManager.TopSort? = null
    ) {
        val menuConfig = MenuConfigManager.get(economy.currencyType)
            ?: MenuConfigManager.load(economy.currencyType)

        if (!menuConfig.menu.enabled) {
            GuiManager.openTopBalanceGui(
                player = player,
                initialCurrencyType = economy.currencyType,
                lockCurrency = true
            )
            return
        }

        val menu = menuConfig.menu

        val dynamicTitle = listOf(
            "%balance%",
            "%raw_balance%",
            "%inventory_balance%",
            "%raw_inventory_balance%",
            "%effective_balance%",
            "%raw_effective_balance%",
            "%sort%",
            "%page%",
            "%max_page%"
        ).any {
            menu.title.contains(it)
        }

        val size = if (
            menu.size in 9..54 &&
            menu.size % 9 == 0
        ) {
            menu.size
        } else {
            println(
                "[BEconomy] Invalid menu size '${menu.size}' for '${economy.currencyType}'. Using 54."
            )
            54
        }

        val screenHandlerType = getGuiScreenHandlerType(size)
        val sortModes = getEconomyMenuSortModes(menuConfig)

        val defaultSort = parseTopSort(menu.leaderboard.defaultSort)
            ?.takeIf { it in sortModes }
            ?: sortModes.first()

        val state = EconomyMenuState(
            page = initialPage,
            sort = initialSort?.takeIf { it in sortModes } ?: defaultSort
        )

        val leaderboardSlots = menu.leaderboard.slots
            .filter { it in 0 until size }
            .distinct()

        if (
            menu.leaderboard.enabled &&
            menu.leaderboard.slots.isNotEmpty() &&
            leaderboardSlots.isEmpty()
        ) {
            println(
                "[BEconomy] No valid leaderboard slots for '${economy.currencyType}'."
            )
        }

        val validMenuItems = menu.items.filter { item ->
            val type = item.type.trim().lowercase(Locale.ROOT)
            val valid = type == "item" || type == "button"

            if (!valid) {
                println(
                    "[BEconomy] Invalid menu item type '${item.type}' for '${economy.currencyType}'."
                )
            }

            valid
        }

        menu.fillers.forEach { filler ->
            if (
                filler.slots.isNotEmpty() &&
                GuiManager.parseSlots(filler.slots, size).isEmpty()
            ) {
                println(
                    "[BEconomy] Filler has no valid slots for '${economy.currencyType}'."
                )
            }
        }

        validMenuItems.forEach { item ->
            if (
                item.slots.isNotEmpty() &&
                GuiManager.parseSlots(item.slots, size).isEmpty()
            ) {
                println(
                    "[BEconomy] Menu item '${item.name}' has no valid slots for '${economy.currencyType}'."
                )
            }
        }

        fun getLeaderboardPlayers(): List<GuiManager.PlayerBalance> {
            if (
                !menu.leaderboard.enabled ||
                leaderboardSlots.isEmpty()
            ) {
                return emptyList()
            }

            return GuiManager.getAllPlayersSortedByBalance(
                player.server,
                economy.currencyType,
                state.sort
            )
        }

        fun getMaxPage(players: List<GuiManager.PlayerBalance>): Int {
            if (
                !menu.leaderboard.enabled ||
                leaderboardSlots.isEmpty() ||
                players.isEmpty()
            ) {
                return 0
            }

            return (players.size - 1) / leaderboardSlots.size
        }

        fun buildPage(page: Int): List<GuiItem> {
            val items = MutableList<GuiItem?>(size) { null }
            val players = getLeaderboardPlayers()
            val maxPage = getMaxPage(players)

            state.page = page.coerceIn(0, maxPage)

            val replacements = getEconomyMenuReplacements(
                player,
                economy,
                state,
                maxPage
            )

            if (menu.fillersEnabled) {
                menu.fillers.forEach { filler ->
                    val stack = createEconomyMenuFiller(
                        filler,
                        player,
                        economy,
                        replacements
                    ) ?: return@forEach

                    GuiManager.parseSlots(filler.slots, size).forEach { slot ->
                        if (items[slot] == null) {
                            items[slot] = GuiItem(
                                stack.copy(),
                                slot
                            )
                        }
                    }
                }
            }

            if (
                menu.leaderboard.enabled &&
                leaderboardSlots.isNotEmpty()
            ) {
                val startIndex = state.page * leaderboardSlots.size

                players
                    .drop(startIndex)
                    .take(leaderboardSlots.size)
                    .forEachIndexed { index, playerData ->
                        val slot = leaderboardSlots[index]

                        items[slot] = GuiItem(
                            createEconomyLeaderboardHead(
                                viewer = player,
                                playerData = playerData,
                                position = startIndex + index + 1,
                                economy = economy,
                                menuConfig = menuConfig,
                                replacements = replacements
                            ),
                            slot
                        )
                    }
            }

            validMenuItems.forEach { itemConfig ->
                if (
                    !shouldRenderEconomyMenuItem(
                        itemConfig,
                        state,
                        maxPage,
                        menu.leaderboard.enabled && leaderboardSlots.isNotEmpty(),
                        sortModes
                    )
                ) {
                    return@forEach
                }

                val stack = createEconomyMenuItem(
                    itemConfig,
                    player,
                    economy,
                    replacements
                ) ?: return@forEach

                GuiManager.parseSlots(itemConfig.slots, size).forEach { slot ->
                    items[slot] = GuiItem(
                        stack.copy(),
                        slot
                    )
                }
            }

            return items.filterNotNull()
        }

        fun refresh(page: Int) {
            val players = getLeaderboardPlayers()
            val maxPage = getMaxPage(players)

            state.page = page.coerceIn(0, maxPage)

            if (dynamicTitle) {
                player.closeHandledScreen()

                openEconomyMenu(
                    player = player,
                    economy = economy,
                    initialPage = state.page,
                    initialSort = state.sort
                )

                return
            }

            val items = buildPage(state.page)

            if (!updateOpenGui(player, items, size)) {
                openEconomyMenu(
                    player = player,
                    economy = economy,
                    initialPage = state.page,
                    initialSort = state.sort
                )
            }
        }

        val initialPlayers = getLeaderboardPlayers()
        val initialMaxPage = getMaxPage(initialPlayers)

        state.page = state.page.coerceIn(
            0,
            initialMaxPage
        )

        val titleReplacements = getEconomyMenuReplacements(
            player,
            economy,
            state,
            initialMaxPage
        )

        val title = processAllFormats(
            replaceEconomyMenuPlaceholders(
                menu.title,
                titleReplacements
            ),
            player
        ).styled { it.withItalic(false) }

        openGui(
            player,
            title,
            buildPage(state.page),
            size,
            screenHandlerType,
            object : GuiInteraction {
                override fun onItemClick(
                    slot: Int,
                    player: ServerPlayerEntity,
                    rightClick: Boolean
                ) {
                    val players = getLeaderboardPlayers()
                    val maxPage = getMaxPage(players)

                    val leaderboardEnabled =
                        menu.leaderboard.enabled &&
                                leaderboardSlots.isNotEmpty()

                    val clickedItem = getEconomyMenuItemAtSlot(
                        items = validMenuItems,
                        slot = slot,
                        size = size,
                        state = state,
                        maxPage = maxPage,
                        leaderboardEnabled = leaderboardEnabled,
                        sortModes = sortModes
                    )

                    if (clickedItem != null) {
                        if (!clickedItem.type.equals("button", ignoreCase = true)) return

                        val action = if (rightClick) {
                            clickedItem.actions.rightClick
                        } else {
                            clickedItem.actions.leftClick
                        }

                        executeEconomyMenuAction(
                            action = action,
                            player = player,
                            economy = economy,
                            state = state,
                            sortModes = sortModes,
                            maxPage = maxPage,
                            leaderboardEnabled = leaderboardEnabled,
                            refresh = ::refresh
                        )

                        return
                    }

                    if (!menu.leaderboard.enabled || leaderboardSlots.isEmpty()) return

                    val slotIndex = leaderboardSlots.indexOf(slot)
                    if (slotIndex == -1) return

                    val playerIndex =
                        state.page * leaderboardSlots.size +
                                slotIndex

                    val clickedPlayerData =
                        players.getOrNull(playerIndex)
                            ?: return

                    val clicked = EconomyMenuClickedPlayer(
                        data = clickedPlayerData,
                        position = playerIndex + 1
                    )

                    val action = if (rightClick) {
                        menu.leaderboard.head.actions.rightClick
                    } else {
                        menu.leaderboard.head.actions.leftClick
                    }

                    executeEconomyMenuAction(
                        action = action,
                        player = player,
                        economy = economy,
                        state = state,
                        sortModes = sortModes,
                        maxPage = maxPage,
                        leaderboardEnabled = true,
                        refresh = ::refresh,
                        clicked = clicked
                    )
                }

                override fun onGuiClose(player: ServerPlayerEntity) {}
            }
        )
    }

    private fun getEconomyMenuReplacements(
        player: ServerPlayerEntity,
        economy: ConfigManager.EconomyConfig,
        state: EconomyMenuState,
        maxPage: Int,
        clicked: EconomyMenuClickedPlayer? = null
    ): Map<String, String> {
        val balance = ConfigManager.getBalance(player.uuid, economy.currencyType)
        val api = BEconomy.getAPI()
        val inventoryBalance = api.getInventoryValue(player.uuid, economy.currencyType)
        val effectiveBalance = api.getEffectiveBalance(player.uuid, economy.currencyType)

        val clickedData = clicked?.data
        val clickedOnline = clickedData?.let {
            player.server.playerManager.getPlayer(it.uuid) != null
        } ?: false

        return mapOf(
            "%player%" to player.name.string,
            "%uuid%" to player.uuid.toString(),
            "%currency%" to economy.name,
            "%currencyType%" to economy.currencyType,
            "%symbol%" to economy.symbol,
            "%balance%" to ConfigManager.NumberFormatter.format(balance),
            "%raw_balance%" to balance.toPlainString(),
            "%inventory_balance%" to ConfigManager.NumberFormatter.format(inventoryBalance),
            "%raw_inventory_balance%" to inventoryBalance.toPlainString(),
            "%effective_balance%" to ConfigManager.NumberFormatter.format(effectiveBalance),
            "%raw_effective_balance%" to effectiveBalance.toPlainString(),
            "%sort%" to state.sort.displayName,
            "%page%" to (state.page + 1).toString(),
            "%max_page%" to (maxPage + 1).toString(),
            "%clicked_player%" to (clickedData?.name ?: ""),
            "%clicked_uuid%" to (clickedData?.uuid?.toString() ?: ""),
            "%clicked_balance%" to (clickedData?.balance?.let {
                ConfigManager.NumberFormatter.format(it)
            } ?: ""),
            "%raw_clicked_balance%" to (clickedData?.balance?.toPlainString() ?: ""),
            "%clicked_position%" to (clicked?.position?.toString() ?: ""),
            "%clicked_status%" to if (clickedData == null) {
                ""
            } else if (clickedOnline) {
                "&aOnline"
            } else {
                "&cOffline"
            }
        )
    }

    private fun replaceEconomyMenuPlaceholders(
        text: String,
        replacements: Map<String, String>
    ): String {
        return replacements.entries.fold(text) { result, (key, value) ->
            result.replace(key, value)
        }
    }
}