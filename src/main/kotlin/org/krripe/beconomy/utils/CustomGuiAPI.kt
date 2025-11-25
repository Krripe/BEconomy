package org.krripe.beconomy.utils

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

data class GuiItem(
    val itemStack: ItemStack,
    val slot: Int,
    val id: String? = null
)

interface GuiInteraction {
    fun onItemClick(slot: Int, player: ServerPlayerEntity, rightClick: Boolean)
    fun onGuiClose(player: ServerPlayerEntity)
}

fun openGui(
    player: ServerPlayerEntity,
    title: Text,
    guiItems: List<GuiItem>,
    inventorySize: Int,
    screenHandlerType: ScreenHandlerType<*>,
    interaction: GuiInteraction
) {
    require(inventorySize in 9..54 && inventorySize % 9 == 0) {
        "Inventory size must be between 9 and 54 and a multiple of 9."
    }
    val factory = CustomGuiScreenHandlerFactory(title, guiItems, inventorySize, interaction)
    player.openHandledScreen(factory.create(player))
}

fun updateOpenGui(
    player: ServerPlayerEntity,
    newItems: List<GuiItem>,
    expectedSize: Int? = null
): Boolean {
    val h = player.currentScreenHandler
    if (h is CustomGuiScreenHandler && (expectedSize == null || expectedSize == h.size)) {
        h.updateGuiContents(newItems)
        return true
    }
    return false
}

fun getGuiScreenHandlerType(size: Int): ScreenHandlerType<*> =
    when (size) {
        9  -> ScreenHandlerType.GENERIC_9X1
        18 -> ScreenHandlerType.GENERIC_9X2
        27 -> ScreenHandlerType.GENERIC_9X3
        36 -> ScreenHandlerType.GENERIC_9X4
        45 -> ScreenHandlerType.GENERIC_9X5
        54 -> ScreenHandlerType.GENERIC_9X6
        else -> throw IllegalArgumentException("Invalid GUI size: $size. Must be a multiple of 9 between 9 and 54.")
    }

private class GuiLockedSlot(
    inventory: Inventory,
    index: Int,
    x: Int,
    y: Int
) : Slot(inventory, index, x, y) {
    override fun canInsert(stack: ItemStack) = false
    override fun canTakeItems(player: PlayerEntity) = false
}

class CustomGuiScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
    private val initialGuiItems: List<GuiItem>,
    private val inventorySize: Int,
    private val interaction: GuiInteraction
) : ScreenHandler(getGuiScreenHandlerType(inventorySize), syncId) {

    val size: Int get() = inventorySize

    private val guiInventory: Inventory = object : Inventory {
        private val items = Array(inventorySize) { ItemStack.EMPTY }

        init {
            initialGuiItems.forEach { item ->
                if (item.slot in 0 until inventorySize) {
                    items[item.slot] = item.itemStack
                }
            }
        }

        override fun clear() { items.fill(ItemStack.EMPTY) }
        override fun size() = items.size
        override fun isEmpty() = items.all { it.isEmpty }
        override fun getStack(slot: Int): ItemStack = items[slot]
        override fun removeStack(slot: Int, amount: Int): ItemStack {
            val stack = getStack(slot)
            return if (stack.count <= amount) removeStack(slot) else {
                val splitStack = stack.split(amount)
                items[slot] = stack
                splitStack
            }
        }
        override fun removeStack(slot: Int): ItemStack {
            val stack = getStack(slot)
            items[slot] = ItemStack.EMPTY
            return stack
        }
        override fun setStack(slot: Int, stack: ItemStack) { items[slot] = stack }

        override fun markDirty() {
            this@CustomGuiScreenHandler.onContentChanged(this)
            this@CustomGuiScreenHandler.sendContentUpdates()
        }

        override fun canPlayerUse(player: PlayerEntity?) = true
    }

    init {
        initializeSlots(playerInventory)
    }

    private fun initializeSlots(playerInventory: PlayerInventory) {
        for (slotIndex in 0 until inventorySize) {
            addSlot(GuiLockedSlot(guiInventory, slotIndex, getX(slotIndex), getY(slotIndex)))
        }
        for (i in 0..2) {
            for (j in 0..8) {
                addSlot(Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18))
            }
        }
        for (k in 0..8) {
            addSlot(Slot(playerInventory, k, 8 + k * 18, 142))
        }
    }

    override fun canUse(player: PlayerEntity?) = true

    override fun quickMove(player: PlayerEntity, slotIndex: Int): ItemStack {
        val slot = slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
        if (!slot.hasStack()) return ItemStack.EMPTY

        val stackInSlot = slot.stack
        val original = stackInSlot.copy()

        val startPlayerInv = inventorySize
        val startHotbar    = inventorySize + 27
        val endPlayerInv   = inventorySize + 36

        if (slotIndex < inventorySize) {
            return ItemStack.EMPTY
        }

        if (slotIndex in startPlayerInv until startHotbar) {
            if (!insertItem(stackInSlot, startHotbar, endPlayerInv, false)) {
                return ItemStack.EMPTY
            }
        }
        else if (slotIndex in startHotbar until endPlayerInv) {
            if (!insertItem(stackInSlot, startPlayerInv, startHotbar, false)) {
                return ItemStack.EMPTY
            }
        } else {
            return ItemStack.EMPTY
        }

        if (stackInSlot.isEmpty) slot.stack = ItemStack.EMPTY else slot.markDirty()
        return original
    }

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType?, player: PlayerEntity?) {
        val isCustomSlot = slotIndex in 0 until guiInventory.size()
        if (isCustomSlot && player is ServerPlayerEntity) {
            val isRightClick = button == 1
            interaction.onItemClick(slotIndex, player, isRightClick)
            return
        }

        if (actionType == SlotActionType.PICKUP_ALL) {
            return
        }

        super.onSlotClick(slotIndex, button, actionType, player)
    }

    override fun onClosed(player: PlayerEntity?) {
        super.onClosed(player)
        if (player is ServerPlayerEntity) {
            interaction.onGuiClose(player)
        }
    }

    private fun getX(slotIndex: Int): Int = 8 + (slotIndex % 9) * 18
    private fun getY(slotIndex: Int): Int = 18 * (slotIndex / 9)

    fun updateGuiContents(newItems: List<GuiItem>) {
        for (i in 0 until inventorySize) {
            guiInventory.setStack(i, ItemStack.EMPTY)
        }
        newItems.forEach { gi ->
            if (gi.slot in 0 until inventorySize) {
                guiInventory.setStack(gi.slot, gi.itemStack)
            }
        }
        guiInventory.markDirty()
    }
}

class CustomGuiScreenHandlerFactory(
    private val title: Text,
    private val guiItems: List<GuiItem>,
    private val inventorySize: Int,
    private val interaction: GuiInteraction
) {
    fun create(player: ServerPlayerEntity): SimpleNamedScreenHandlerFactory {
        return SimpleNamedScreenHandlerFactory({ syncId, inv, _ ->
            CustomGuiScreenHandler(syncId, inv, guiItems, inventorySize, interaction)
        }, title)
    }
}