package com.bettercontent.tinkersconstructaffixes

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object AffixItems {
    val REGISTRY: DeferredRegister<Item> = DeferredRegister.create(ForgeRegistries.ITEMS, TConAffixesMod.MOD_ID)
    val CACHE: RegistryObject<Item> = REGISTRY.register("affixed_part_cache") { AffixedPartCacheItem() }
    val RECASTING_FLUX: RegistryObject<Item> = currency("recasting_flux", AffixCurrencyType.RECAST)
    val GRAFTING_FLUX: RegistryObject<Item> = currency("grafting_flux", AffixCurrencyType.ADD)
    val SHEARING_FLUX: RegistryObject<Item> = currency("shearing_flux", AffixCurrencyType.REMOVE)
    val FORERUNE_SEAL: RegistryObject<Item> = currency("forerune_seal", AffixCurrencyType.PRESERVE_PREFIXES, true)
    val AFTERRUNE_SEAL: RegistryObject<Item> = currency("afterrune_seal", AffixCurrencyType.PRESERVE_SUFFIXES, true)
    val RUINOUS_FLUX: RegistryObject<Item> = currency("ruinous_flux", AffixCurrencyType.MUTATE, true)

    private fun currency(name: String, type: AffixCurrencyType, foil: Boolean = false): RegistryObject<Item> =
        REGISTRY.register(name) { AffixCurrencyItem(type, foil) }

    internal fun type(stack: ItemStack): AffixCurrencyType? = (stack.item as? AffixCurrencyItem)?.type

    internal fun stack(type: AffixCurrencyType, count: Int = 1): ItemStack = ItemStack(when (type) {
        AffixCurrencyType.RECAST -> RECASTING_FLUX.get()
        AffixCurrencyType.ADD -> GRAFTING_FLUX.get()
        AffixCurrencyType.REMOVE -> SHEARING_FLUX.get()
        AffixCurrencyType.PRESERVE_PREFIXES -> FORERUNE_SEAL.get()
        AffixCurrencyType.PRESERVE_SUFFIXES -> AFTERRUNE_SEAL.get()
        AffixCurrencyType.MUTATE -> RUINOUS_FLUX.get()
    }, count)
}

internal class AffixCurrencyItem(val type: AffixCurrencyType, private val foil: Boolean) : Item(Properties().stacksTo(64)) {
    override fun isFoil(stack: ItemStack): Boolean = foil || super.isFoil(stack)

    override fun appendHoverText(stack: ItemStack, level: Level?, tooltip: MutableList<net.minecraft.network.chat.Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, level, tooltip, flag)
        tooltip += net.minecraft.network.chat.Component.translatable("tooltip.tinkers_construct_affixes.currency.${type.name.lowercase()}")
            .withStyle(net.minecraft.ChatFormatting.GRAY)
        tooltip += net.minecraft.network.chat.Component.translatable("tooltip.tinkers_construct_affixes.currency.use")
            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
    }
}

private class AffixedPartCacheItem : Item(Properties().stacksTo(16)) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val held = player.getItemInHand(hand)
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(held, true)

        val origin = AffixOrigins.fromDimension(level.dimension().location())
        val reward = TConAffixRewards.rollAffixedPart(level.random, origin, "cache")
            ?: return InteractionResultHolder.fail(held)
        held.shrink(1)
        val serverPlayer = player as ServerPlayer
        if (!serverPlayer.inventory.add(reward)) {
            serverPlayer.drop(reward, false)
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.65f, 1.05f)
        return InteractionResultHolder.consume(held)
    }
}
