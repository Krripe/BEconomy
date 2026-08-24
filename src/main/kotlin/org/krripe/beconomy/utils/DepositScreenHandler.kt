package org.krripe.beconomy.utils

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import org.krripe.beconomy.config.DepositConfigManager
import org.krripe.beconomy.manager.EcoManager
import java.util.Locale

interface DepositGuiInteraction {

    fun onDeposit(
        player: ServerPlayerEntity,
        result: EcoManager.DepositResult
    )

    fun onInvalidDeposit(
        player: ServerPlayerEntity
    )

    fun onItemClick(
        slot: Int,
        player: ServerPlayerEntity,
        rightClick: Boolean
    )

    fun onGuiClose(
        player: ServerPlayerEntity
    )
}

private class DepositLockedSlot(
    inventory: Inventory,
    index: Int,
    x: Int,
    y: Int
) : Slot(
    inventory,
    index,
    x,
    y
) {

    override fun canInsert(
        stack: ItemStack
    ) = false

    override fun canTakeItems(
        player: PlayerEntity
    ) = false
}

class DepositScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
    private val guiItems: List<GuiItem>,
    private val inventorySize: Int,
    private val currencyType: String,
    private val depositSlots: Set<Int>,
    private val behavior: DepositConfigManager.BehaviorConfig,
    private val interaction: DepositGuiInteraction
) : ScreenHandler(
    getGuiScreenHandlerType(inventorySize),
    syncId
) {

    val size: Int
        get() = inventorySize

    private val guiInventory =
        SimpleInventory(
            inventorySize
        )

    private var processingDeposit = false

    init {
        guiItems.forEach {
            if (
                it.slot in 0 until inventorySize &&
                it.slot !in depositSlots
            ) {
                guiInventory.setStack(
                    it.slot,
                    it.itemStack
                )
            }
        }

        initializeSlots(
            playerInventory
        )
    }

    private fun initializeSlots(
        playerInventory: PlayerInventory
    ) {
        for (
        slotIndex in
        0 until inventorySize
        ) {
            addSlot(
                DepositLockedSlot(
                    guiInventory,
                    slotIndex,
                    getX(slotIndex),
                    getY(slotIndex)
                )
            )
        }

        for (i in 0..2) {
            for (j in 0..8) {
                addSlot(
                    Slot(
                        playerInventory,
                        j + i * 9 + 9,
                        8 + j * 18,
                        84 + i * 18
                    )
                )
            }
        }

        for (i in 0..8) {
            addSlot(
                Slot(
                    playerInventory,
                    i,
                    8 + i * 18,
                    142
                )
            )
        }
    }

    override fun canUse(
        player: PlayerEntity?
    ) = true

    override fun quickMove(
        player: PlayerEntity,
        slotIndex: Int
    ): ItemStack {
        if (
            player !is ServerPlayerEntity ||
            processingDeposit ||
            !behavior.shiftClick ||
            slotIndex !in inventorySize until slots.size
        ) {
            return ItemStack.EMPTY
        }

        val slot =
            slots.getOrNull(
                slotIndex
            ) ?: return ItemStack.EMPTY

        if (
            !slot.hasStack() ||
            !slot.canTakeItems(player)
        ) {
            return ItemStack.EMPTY
        }

        val stack =
            slot.stack

        if (stack.isEmpty) {
            return ItemStack.EMPTY
        }

        val original =
            stack.copy()

        processingDeposit = true

        try {
            val result =
                EcoManager.depositStack(
                    player,
                    currencyType,
                    stack,
                    stack.count
                )

            if (result == null) {
                interaction.onInvalidDeposit(
                    player
                )

                return ItemStack.EMPTY
            }

            if (stack.isEmpty) {
                slot.stack =
                    ItemStack.EMPTY
            } else {
                slot.markDirty()
            }

            syncInventory(
                player
            )

            interaction.onDeposit(
                player,
                result
            )

            return original
        } finally {
            processingDeposit = false
        }
    }

    override fun onSlotClick(
        slotIndex: Int,
        button: Int,
        actionType: SlotActionType?,
        player: PlayerEntity?
    ) {
        if (
            player !is ServerPlayerEntity ||
            actionType == null ||
            processingDeposit
        ) {
            return
        }

        when (actionType) {
            SlotActionType.QUICK_CRAFT,
            SlotActionType.PICKUP_ALL,
            SlotActionType.CLONE,
            SlotActionType.THROW -> return

            else -> Unit
        }

        val guiSlot =
            slotIndex in
                    0 until inventorySize

        if (guiSlot) {
            if (
                slotIndex in
                depositSlots
            ) {
                handleDepositSlot(
                    player,
                    button,
                    actionType
                )

                return
            }

            if (
                actionType ==
                SlotActionType.PICKUP &&
                button in 0..1 &&
                !guiInventory
                    .getStack(slotIndex)
                    .isEmpty
            ) {
                interaction.onItemClick(
                    slotIndex,
                    player,
                    button == 1
                )
            }

            return
        }

        super.onSlotClick(
            slotIndex,
            button,
            actionType,
            player
        )
    }

    private fun handleDepositSlot(
        player: ServerPlayerEntity,
        button: Int,
        actionType: SlotActionType
    ) {
        when (actionType) {
            SlotActionType.PICKUP -> {
                if (button in 0..1) {
                    depositCursor(
                        player,
                        button
                    )
                }
            }

            SlotActionType.SWAP -> {
                if (button in 0..8) {
                    depositHotbar(
                        player,
                        button
                    )
                }
            }

            else -> Unit
        }
    }

    private fun depositCursor(
        player: ServerPlayerEntity,
        button: Int
    ) {
        if (
            processingDeposit ||
            button !in 0..1
        ) {
            return
        }

        val stack =
            getCursorStack()

        if (stack.isEmpty) {
            return
        }

        val mode =
            if (button == 0) {
                behavior.leftClick
            } else {
                behavior.rightClick
            }

        val count =
            when (
                mode.trim()
                    .uppercase(
                        Locale.ROOT
                    )
            ) {
                "STACK" ->
                    stack.count

                "ONE" ->
                    1

                else ->
                    0
            }

        if (
            count <= 0 ||
            count > stack.count
        ) {
            return
        }

        processingDeposit = true

        try {
            val result =
                EcoManager.depositStack(
                    player,
                    currencyType,
                    stack,
                    count
                )

            if (result == null) {
                interaction.onInvalidDeposit(
                    player
                )

                return
            }

            if (stack.isEmpty) {
                setCursorStack(
                    ItemStack.EMPTY
                )
            } else {
                setCursorStack(
                    stack
                )
            }

            syncInventory(
                player
            )

            interaction.onDeposit(
                player,
                result
            )
        } finally {
            processingDeposit = false
        }
    }

    private fun depositHotbar(
        player: ServerPlayerEntity,
        hotbarSlot: Int
    ) {
        if (
            processingDeposit ||
            !behavior.hotbarKeys ||
            hotbarSlot !in 0..8 ||
            !getCursorStack().isEmpty
        ) {
            return
        }

        val stack =
            player.inventory.getStack(
                hotbarSlot
            )

        if (stack.isEmpty) {
            return
        }

        processingDeposit = true

        try {
            val result =
                EcoManager.depositStack(
                    player,
                    currencyType,
                    stack,
                    stack.count
                )

            if (result == null) {
                interaction.onInvalidDeposit(
                    player
                )

                return
            }

            if (stack.isEmpty) {
                player.inventory.setStack(
                    hotbarSlot,
                    ItemStack.EMPTY
                )
            }

            syncInventory(
                player
            )

            interaction.onDeposit(
                player,
                result
            )
        } finally {
            processingDeposit = false
        }
    }

    private fun syncInventory(
        player: ServerPlayerEntity
    ) {
        player.inventory.markDirty()
        sendContentUpdates()
    }

    override fun onClosed(
        player: PlayerEntity?
    ) {
        super.onClosed(
            player
        )

        if (
            player is
                    ServerPlayerEntity
        ) {
            interaction.onGuiClose(
                player
            )
        }
    }

    fun updateGuiContents(
        newItems: List<GuiItem>
    ) {
        for (
        i in
        0 until inventorySize
        ) {
            guiInventory.setStack(
                i,
                ItemStack.EMPTY
            )
        }

        newItems.forEach {
            if (
                it.slot in
                0 until inventorySize &&
                it.slot !in depositSlots
            ) {
                guiInventory.setStack(
                    it.slot,
                    it.itemStack
                )
            }
        }

        guiInventory.markDirty()
        sendContentUpdates()
    }

    private fun getX(
        slotIndex: Int
    ): Int {
        return 8 +
                (slotIndex % 9) * 18
    }

    private fun getY(
        slotIndex: Int
    ): Int {
        return 18 *
                (slotIndex / 9)
    }
}

fun openDepositGui(
    player: ServerPlayerEntity,
    title: Text,
    guiItems: List<GuiItem>,
    inventorySize: Int,
    currencyType: String,
    depositSlots: Set<Int>,
    behavior: DepositConfigManager.BehaviorConfig,
    interaction: DepositGuiInteraction
) {
    require(
        inventorySize in 9..54 &&
                inventorySize % 9 == 0
    ) {
        "Inventory size must be between 9 and 54 and a multiple of 9."
    }

    val factory =
        DepositScreenHandlerFactory(
            title,
            guiItems,
            inventorySize,
            currencyType,
            depositSlots,
            behavior,
            interaction
        )

    player.openHandledScreen(
        factory.create()
    )
}

fun updateOpenDepositGui(
    player: ServerPlayerEntity,
    newItems: List<GuiItem>,
    expectedSize: Int? = null
): Boolean {
    val handler =
        player.currentScreenHandler

    if (
        handler is
                DepositScreenHandler &&
        (
                expectedSize == null ||
                        expectedSize ==
                        handler.size
                )
    ) {
        handler.updateGuiContents(
            newItems
        )

        return true
    }

    return false
}

class DepositScreenHandlerFactory(
    private val title: Text,
    private val guiItems: List<GuiItem>,
    private val inventorySize: Int,
    private val currencyType: String,
    private val depositSlots: Set<Int>,
    private val behavior: DepositConfigManager.BehaviorConfig,
    private val interaction: DepositGuiInteraction
) {

    fun create():
            SimpleNamedScreenHandlerFactory {
        return SimpleNamedScreenHandlerFactory(
            {
                    syncId,
                    inventory,
                    _ ->

                DepositScreenHandler(
                    syncId,
                    inventory,
                    guiItems,
                    inventorySize,
                    currencyType,
                    depositSlots,
                    behavior,
                    interaction
                )
            },
            title
        )
    }
}