package com.bettercontent.tinkersconstructaffixes

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.ContainerScreenEvent
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.lwjgl.glfw.GLFW

@Mod.EventBusSubscriber(modid = TConAffixesMod.MOD_ID, value = [Dist.CLIENT])
internal object AffixClientEvents {
    private data class Armed(val slot: Int, val type: AffixCurrencyType)
    private var armed: Armed? = null
    private var nonce = 0L

    @SubscribeEvent
    fun onMousePressed(event: ScreenEvent.MouseButtonPressed.Pre) {
        val screen = event.screen as? AbstractContainerScreen<*> ?: return
        val player = Minecraft.getInstance().player ?: return
        val hovered = screen.slotUnderMouse

        if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (hovered != null && isPlayerSlot(hovered, player.inventory)) {
                val type = AffixItems.type(hovered.item)
                if (type != null) {
                    armed = Armed(hovered.containerSlot, type)
                    player.displayClientMessage(Component.translatable("message.tinkers_construct_affixes.armed", hovered.item.hoverName), true)
                    event.isCanceled = true
                    return
                }
                if (armed == null && Screen.hasShiftDown() && TConAffixRewards.existingToolAffixes(hovered.item).isNotEmpty()) {
                    openConfirmation(screen, false, hovered)
                    event.isCanceled = true
                    return
                }
            }
            if (armed != null) {
                armed = null
                event.isCanceled = true
            }
            return
        }

        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return
        val selected = armed ?: return
        val source = player.inventory.getItem(selected.slot)
        if (AffixItems.type(source) != selected.type) {
            armed = null
            return
        }
        if (hovered == null || !isPlayerSlot(hovered, player.inventory) || !AffixCrafting.canTarget(hovered.item)) return
        event.isCanceled = true
        if (selected.type == AffixCurrencyType.MUTATE) {
            openConfirmation(screen, true, hovered)
            return
        }
        send(screen, selected, hovered)
        if (!Screen.hasShiftDown() || source.count <= 1) armed = null
    }

    @SubscribeEvent
    fun onKeyPressed(event: ScreenEvent.KeyPressed.Pre) {
        if (event.keyCode == InputConstants.KEY_ESCAPE && armed != null) {
            armed = null
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    fun onClosing(event: ScreenEvent.Closing) {
        if (event.screen is AbstractContainerScreen<*>) armed = null
    }

    @SubscribeEvent
    fun onForeground(event: ContainerScreenEvent.Render.Foreground) {
        val selected = armed ?: return
        val screen = event.containerScreen
        val player = Minecraft.getInstance().player ?: return
        if (AffixItems.type(player.inventory.getItem(selected.slot)) != selected.type) {
            armed = null
            return
        }
        screen.menu.slots.filter { slot ->
            isPlayerSlot(slot, player.inventory) && (slot.containerSlot == selected.slot || AffixCrafting.canTarget(slot.item))
        }.forEach { slot ->
            val color = if (slot.containerSlot == selected.slot) 0x80FFD54F.toInt() else 0x6035D7FF
            event.guiGraphics.fill(screen.guiLeft + slot.x, screen.guiTop + slot.y, screen.guiLeft + slot.x + 16, screen.guiTop + slot.y + 16, color)
        }
        event.guiGraphics.drawString(
            Minecraft.getInstance().font,
            Component.translatable("message.tinkers_construct_affixes.targeting").withStyle(ChatFormatting.AQUA),
            screen.guiLeft,
            screen.guiTop - 11,
            0xFFFFFF,
            true
        )
    }

    private fun openConfirmation(parent: AbstractContainerScreen<*>, mutation: Boolean, target: Slot) {
        val selected = armed
        val title = if (mutation) Component.translatable("screen.tinkers_construct_affixes.mutate.title") else Component.translatable("screen.tinkers_construct_affixes.salvage.title")
        val message = if (mutation) Component.translatable("screen.tinkers_construct_affixes.mutate.message") else Component.translatable("screen.tinkers_construct_affixes.salvage.message")
        Minecraft.getInstance().execute {
            Minecraft.getInstance().setScreen(ConfirmScreen({ accepted ->
                Minecraft.getInstance().setScreen(parent)
                if (!accepted) {
                    if (mutation) armed = null
                    return@ConfirmScreen
                }
                if (mutation && selected != null) send(parent, selected, target)
                else sendSalvage(parent, target)
                armed = null
            }, title, message))
        }
    }

    private fun send(screen: AbstractContainerScreen<*>, selected: Armed, target: Slot) {
        AffixNetwork.send(AffixInventoryActionPacket(
            screen.menu.containerId,
            selected.slot,
            target.containerSlot,
            selected.type.name,
            AffixNetwork.fingerprint(target.item),
            nextNonce(),
            false
        ))
    }

    private fun sendSalvage(screen: AbstractContainerScreen<*>, target: Slot) {
        AffixNetwork.send(AffixInventoryActionPacket(
            screen.menu.containerId,
            -1,
            target.containerSlot,
            "",
            AffixNetwork.fingerprint(target.item),
            nextNonce(),
            true
        ))
    }

    private fun nextNonce(): Long {
        // Wall-clock time keeps the sequence increasing when a client restarts while the server stays online.
        val now = System.currentTimeMillis()
        nonce = maxOf(nonce + 1, now)
        return nonce
    }

    private fun isPlayerSlot(slot: Slot, inventory: Inventory): Boolean = slot.container === inventory
}
