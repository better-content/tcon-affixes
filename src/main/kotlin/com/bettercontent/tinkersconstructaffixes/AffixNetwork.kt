package com.bettercontent.tinkersconstructaffixes

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

internal object AffixNetwork {
    private const val VERSION = "1"
    private val lastNonce = ConcurrentHashMap<UUID, Long>()
    val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(TConAffixesMod.MOD_ID, "main"),
        { VERSION },
        VERSION::equals,
        VERSION::equals
    )

    fun register() {
        CHANNEL.messageBuilder(AffixInventoryActionPacket::class.java, 0, NetworkDirection.PLAY_TO_SERVER)
            .encoder(AffixInventoryActionPacket::encode)
            .decoder(AffixInventoryActionPacket::decode)
            .consumerMainThread(AffixInventoryActionPacket::handle)
            .add()
    }

    fun send(packet: AffixInventoryActionPacket) = CHANNEL.sendToServer(packet)

    internal fun handle(packet: AffixInventoryActionPacket, player: ServerPlayer) {
        if (packet.nonce <= (lastNonce[player.uuid] ?: Long.MIN_VALUE)) return
        lastNonce[player.uuid] = packet.nonce
        if (player.containerMenu.containerId != packet.menuId) return
        val inventory = player.inventory
        if (packet.targetSlot !in 0 until inventory.containerSize) return
        val target = inventory.getItem(packet.targetSlot)
        if (target.isEmpty || fingerprint(target) != packet.targetFingerprint || !AffixCrafting.canTarget(target)) return

        if (packet.salvage) {
            val payout = AffixCrafting.salvage(target) ?: return
            payout.groupingBy { it }.eachCount().forEach { (type, count) ->
                val reward = AffixItems.stack(type, count)
                if (!inventory.add(reward)) player.drop(reward, false)
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.7f, 0.9f)
            inventory.setChanged()
            player.containerMenu.broadcastChanges()
            return
        }

        if (packet.sourceSlot !in 0 until inventory.containerSize || packet.sourceSlot == packet.targetSlot) return
        val source = inventory.getItem(packet.sourceSlot)
        val type = AffixItems.type(source) ?: return
        if (type.name != packet.currencyType) return
        val result = AffixCrafting.apply(target, type, player.random)
        if (result != AffixOperationResult.APPLIED && result != AffixOperationResult.DESTROYED) {
            player.displayClientMessage(Component.translatable("message.tinkers_construct_affixes.operation.${result.name.lowercase()}"), true)
            return
        }
        source.shrink(1)
        inventory.setChanged()
        player.containerMenu.broadcastChanges()
        val level = player.level() as ServerLevel
        if (result == AffixOperationResult.DESTROYED) {
            level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9f, 0.55f)
            level.sendParticles(ParticleTypes.SMOKE, player.x, player.y + 1.0, player.z, 18, 0.25, 0.35, 0.25, 0.02)
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.55f, if (type == AffixCurrencyType.MUTATE) 0.65f else 1.15f)
            level.sendParticles(ParticleTypes.ENCHANT, player.x, player.y + 1.0, player.z, 10, 0.2, 0.3, 0.2, 0.05)
        }
    }

    fun fingerprint(stack: net.minecraft.world.item.ItemStack): Int {
        var result = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.item)?.hashCode() ?: 0
        result = 31 * result + stack.count
        result = 31 * result + (stack.tag?.toString()?.hashCode() ?: 0)
        return result
    }
}

internal data class AffixInventoryActionPacket(
    val menuId: Int,
    val sourceSlot: Int,
    val targetSlot: Int,
    val currencyType: String,
    val targetFingerprint: Int,
    val nonce: Long,
    val salvage: Boolean
) {
    fun encode(buffer: FriendlyByteBuf) {
        buffer.writeVarInt(menuId)
        buffer.writeVarInt(sourceSlot + 1)
        buffer.writeVarInt(targetSlot)
        buffer.writeUtf(currencyType, 48)
        buffer.writeInt(targetFingerprint)
        buffer.writeLong(nonce)
        buffer.writeBoolean(salvage)
    }

    fun handle(context: Supplier<NetworkEvent.Context>) {
        context.get().sender?.let { AffixNetwork.handle(this, it) }
        context.get().packetHandled = true
    }

    companion object {
        fun encode(packet: AffixInventoryActionPacket, buffer: FriendlyByteBuf) = packet.encode(buffer)
        fun decode(buffer: FriendlyByteBuf) = AffixInventoryActionPacket(
            buffer.readVarInt(),
            buffer.readVarInt() - 1,
            buffer.readVarInt(),
            buffer.readUtf(48),
            buffer.readInt(),
            buffer.readLong(),
            buffer.readBoolean()
        )
        fun handle(packet: AffixInventoryActionPacket, context: Supplier<NetworkEvent.Context>) = packet.handle(context)
    }
}
