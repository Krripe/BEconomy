package org.krripe.beconomy.manager

import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import net.minecraft.command.argument.UuidArgumentType.uuid
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent
import net.minecraft.component.type.LoreComponent
import net.minecraft.component.type.ProfileComponent
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.krripe.beconomy.config.ConfigManager
import org.krripe.beconomy.config.ConfigManager.config
import org.krripe.beconomy.config.GuiConfigManager
import org.krripe.beconomy.database.DatabaseManager
import org.krripe.beconomy.utils.CustomColorParser.processAllFormats
import org.krripe.beconomy.utils.GuiInteraction
import org.krripe.beconomy.utils.GuiItem
import org.krripe.beconomy.utils.getGuiScreenHandlerType
import org.krripe.beconomy.utils.openGui
import org.krripe.beconomy.utils.updateOpenGui
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID

object GuiManager {

    internal enum class TopSort(val displayName: String) {
        PRICE_HIGH("Price High"),
        PRICE_LOW("Price Low"),
        A_Z("A-Z");

        fun next(): TopSort = entries[(ordinal + 1) % entries.size]
    }

    private data class TopGuiState(
        var page: Int,
        var currencyIndex: Int,
        var sort: TopSort
    )

    internal val uuid_like_regex =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    internal fun parseSlots(exprs: List<String>, size: Int): List<Int> {
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

    private fun createFillerItem(cfg: GuiConfigManager.FillerConfig): ItemStack {
        val material = Registries.ITEM.get(Identifier.of(cfg.icon))
        val stack = ItemStack(material)

        stack.set(
            DataComponentTypes.CUSTOM_NAME,
            processAllFormats(cfg.name).styled { it.withItalic(false) }
        )

        cfg.customModelData?.takeIf { it > 0 }?.let {
            stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelDataComponent(it))
        }

        if (cfg.lore.isNotEmpty()) {
            stack.set(
                DataComponentTypes.LORE,
                LoreComponent(cfg.lore.map {
                    processAllFormats(it).styled { style -> style.withItalic(false) }
                })
            )
        }

        if (cfg.glow) stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        if (cfg.hideAdditionalTooltip) {
            stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        }
        if (cfg.hideTooltip) {
            stack.set(DataComponentTypes.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        }

        return stack
    }

    internal fun openTopBalanceGui(
        player: ServerPlayerEntity,
        initialCurrencyType: String? = null,
        initialSort: TopSort = TopSort.PRICE_HIGH,
        lockCurrency: Boolean = false
    ) {
        val guiConfig = GuiConfigManager.config
        val size = guiConfig.size.takeIf { it in 9..54 && it % 9 == 0 } ?: 54
        val screenHandlerType = getGuiScreenHandlerType(size)

        val topCurrencies = config.economy.filter {
            it.top && (lockCurrency || it.showInGlobalTop)
        }
        val currencies = if (lockCurrency && initialCurrencyType != null) {
            topCurrencies.filter {
                it.currencyType.equals(initialCurrencyType, ignoreCase = true)
            }
        } else {
            topCurrencies
        }

        if (currencies.isEmpty()) {
            player.sendMessage(Text.literal("[BEconomy] No economies have Top enabled."), false)
            return
        }

        val initialIndex = initialCurrencyType
            ?.let { type ->
                currencies.indexOfFirst {
                    it.currencyType.equals(type, ignoreCase = true)
                }
            }
            ?.takeIf { it >= 0 }
            ?: currencies.indexOfFirst { it.isPrimary }.takeIf { it >= 0 }
            ?: 0

        val state = TopGuiState(0, initialIndex, initialSort)

        fun buildPage(pageIndex: Int): List<GuiItem> {
            val items = MutableList<GuiItem?>(size) { null }

            if (guiConfig.fillersEnabled) {
                guiConfig.fillers.forEach { filler ->
                    val stack = createFillerItem(filler)

                    parseSlots(filler.slots, size).forEach { slot ->
                        if (items[slot] == null) {
                            items[slot] = GuiItem(stack.copy(), slot)
                        }
                    }
                }
            }

            val currency = currencies[state.currencyIndex]

            val sortedPlayers = getAllPlayersSortedByBalance(
                player.server,
                currency.currencyType,
                state.sort
            )

            val perPage = guiConfig.headSlots.size
            if (perPage == 0) return items.filterNotNull()

            val maxPage = if (sortedPlayers.isEmpty()) {
                0
            } else {
                (sortedPlayers.size - 1) / perPage
            }

            state.page = pageIndex.coerceIn(0, maxPage)

            val startIndex = state.page * perPage

            sortedPlayers
                .drop(startIndex)
                .take(perPage)
                .forEachIndexed { index, playerData ->
                    val slot = guiConfig.headSlots.getOrNull(index) ?: return@forEachIndexed
                    if (slot !in 0 until size) return@forEachIndexed

                    items[slot] = GuiItem(
                        createPlayerHeadItem(
                            playerData.uuid,
                            playerData.name,
                            playerData.balance,
                            player.server,
                            startIndex + index + 1
                        ),
                        slot
                    )
                }

            fun addButton(
                cfg: GuiConfigManager.ButtonConfig,
                replacements: Map<String, String> = emptyMap()
            ) {
                if (cfg.slot in 0 until size) {
                    items[cfg.slot] = GuiItem(
                        createTopButton(cfg, replacements),
                        cfg.slot
                    )
                }
            }

            addButton(guiConfig.closeButton)

            if (state.page < maxPage) {
                addButton(guiConfig.nextPageButton)
            }

            if (state.page > 0) {
                addButton(guiConfig.prevPageButton)
            }

            val replacements = mapOf(
                "%currency%" to currency.name,
                "%currencyType%" to currency.currencyType,
                "%sort%" to state.sort.displayName
            )

            if (!lockCurrency) {
                addButton(guiConfig.currencyButton, replacements)
            }

            addButton(guiConfig.sortButton, replacements)

            return items.filterNotNull()
        }

        fun refresh(page: Int) {
            val newItems = buildPage(page)

            if (!updateOpenGui(player, newItems, size)) {
                openTopBalanceGui(
                    player = player,
                    initialCurrencyType = currencies[state.currencyIndex].currencyType,
                    initialSort = state.sort,
                    lockCurrency = lockCurrency
                )
            }
        }

        openGui(
            player,
            processAllFormats(guiConfig.title).styled { it.withItalic(false) },
            buildPage(0),
            size,
            screenHandlerType,
            object : GuiInteraction {
                override fun onItemClick(
                    slot: Int,
                    player: ServerPlayerEntity,
                    rightClick: Boolean
                ) {
                    val currency = currencies[state.currencyIndex]

                    val sortedPlayers = getAllPlayersSortedByBalance(
                        player.server,
                        currency.currencyType,
                        state.sort
                    )

                    val perPage = guiConfig.headSlots.size
                    val maxPage = if (perPage == 0 || sortedPlayers.isEmpty()) {
                        0
                    } else {
                        (sortedPlayers.size - 1) / perPage
                    }

                    when (slot) {
                        guiConfig.closeButton.slot -> {
                            player.closeHandledScreen()
                        }

                        guiConfig.nextPageButton.slot -> {
                            if (state.page < maxPage) {
                                refresh(state.page + 1)
                            }
                        }

                        guiConfig.prevPageButton.slot -> {
                            if (state.page > 0) {
                                refresh(state.page - 1)
                            }
                        }

                        guiConfig.currencyButton.slot -> {
                            if (!lockCurrency && !rightClick) {
                                state.currencyIndex =
                                    (state.currencyIndex + 1) % currencies.size

                                state.page = 0
                                refresh(0)
                            }
                        }

                        guiConfig.sortButton.slot -> {
                            if (!rightClick) {
                                state.sort = state.sort.next()
                                state.page = 0
                                refresh(0)
                            }
                        }
                    }
                }

                override fun onGuiClose(player: ServerPlayerEntity) {}
            }
        )
    }

    private fun replaceGuiPlaceholders(
        text: String,
        replacements: Map<String, String>
    ): String {
        return replacements.entries.fold(text) { result, entry ->
            result.replace(entry.key, entry.value)
        }
    }

    private fun createTopButton(
        cfg: GuiConfigManager.ButtonConfig,
        replacements: Map<String, String> = emptyMap()
    ): ItemStack {
        val material = Registries.ITEM.get(Identifier.of(cfg.material))
        val stack = ItemStack(material)

        if (cfg.customModelData > 0) {
            stack.set(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelDataComponent(cfg.customModelData)
            )
        }

        stack.set(
            DataComponentTypes.CUSTOM_NAME,
            processAllFormats(replaceGuiPlaceholders(cfg.name, replacements))
                .styled { it.withItalic(false) }
        )

        if (cfg.lore.isNotEmpty()) {
            stack.set(
                DataComponentTypes.LORE,
                LoreComponent(cfg.lore.map {
                    processAllFormats(replaceGuiPlaceholders(it, replacements))
                        .styled { style -> style.withItalic(false) }
                })
            )
        }

        if (cfg.glow) stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        if (cfg.hideAdditionalTooltip) {
            stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        }
        if (cfg.hideTooltip) {
            stack.set(DataComponentTypes.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        }

        return stack
    }

    internal fun getAllPlayersSortedByBalance(
        server: MinecraftServer,
        currencyType: String,
        sort: TopSort
    ): List<PlayerBalance> {
        val balances = mutableMapOf<UUID, BigDecimal>()

        if (config.databaseSave) {
            DatabaseManager.getAdapter()?.let {
                balances.putAll(it.getAllBalances(currencyType))
            }
        }

        if (config.localSave) {
            val userFolder = java.io.File("config/beconomy/user")

            if (userFolder.exists() && userFolder.isDirectory) {
                userFolder.listFiles { file -> file.extension == "json" }?.forEach { file ->
                    val uuid = runCatching {
                        UUID.fromString(file.nameWithoutExtension)
                    }.getOrNull() ?: return@forEach

                    val jsonObject = JsonParser.parseString(
                        java.nio.file.Files.readString(file.toPath())
                    ).asJsonObject

                    jsonObject.get(currencyType)?.asBigDecimal?.let {
                        balances[uuid] = it
                    }
                }
            }
        }

        fun resolveName(uuid: UUID): String {
            server.playerManager.getPlayer(uuid)?.let {
                return it.gameProfile.name ?: it.name.string
            }

            server.userCache?.getByUuid(uuid)?.orElse(null)?.let {
                if (!it.name.isNullOrBlank()) return it.name
            }

            return "Unknown"
        }

        val players = if (balances.isEmpty()) {
            server.playerManager.playerList.map {
                PlayerBalance(
                    it.uuid,
                    it.name.string,
                    ConfigManager.getBalance(it.uuid, currencyType)
                )
            }
        } else {
            balances.map { (uuid, balance) ->
                PlayerBalance(uuid, resolveName(uuid), balance)
            }
        }

        return when (sort) {
            TopSort.PRICE_HIGH -> players.sortedWith(
                compareByDescending<PlayerBalance> { it.balance }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )

            TopSort.PRICE_LOW -> players.sortedWith(
                compareBy<PlayerBalance> { it.balance }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )

            TopSort.A_Z -> players.sortedWith(
                compareBy<PlayerBalance> { it.name.lowercase(Locale.ROOT) }
                    .thenByDescending { it.balance }
            )
        }
    }

    internal data class PlayerBalance(
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

        val lore = GuiConfigManager.config.headLore.map {
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
}