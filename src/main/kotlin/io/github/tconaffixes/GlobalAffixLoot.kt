package io.github.tconaffixes

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue
import net.minecraftforge.event.LootTableLoadEvent
import net.minecraftforge.event.entity.living.LivingDropsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object GlobalAffixLoot {
    @SubscribeEvent
    fun onLivingDrops(event: LivingDropsEvent) {
        val level = event.entity.level()
        if (level.isClientSide || event.entity !is Mob || event.entity.type.category != net.minecraft.world.entity.MobCategory.MONSTER) return
        val killer = event.source.entity as? ServerPlayer ?: return
        val origin = AffixOrigins.fromDimension(level.dimension().location())
        val partChance = if (origin == AffixOrigin.GLOBAL) TConAffixConfig.hostileDropChance() else TConAffixConfig.fontDropChance()
        if (level.random.nextDouble() < partChance) {
            val entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(event.entity.type)?.toString() ?: "monster"
            TConAffixRewards.rollAffixedPart(level.random, origin, entityId)?.let { part ->
                event.drops += ItemEntity(level, event.entity.x, event.entity.y, event.entity.z, part)
            }
        }
        val currencyChance = if (origin == AffixOrigin.GLOBAL) TConAffixConfig.hostileCurrencyChance() else TConAffixConfig.fontCurrencyChance()
        if (level.random.nextDouble() < currencyChance) {
            val currency = rollKillCurrency(level.random)
            event.drops += ItemEntity(level, event.entity.x, event.entity.y, event.entity.z, AffixItems.stack(currency))
            if (currency == AffixCurrencyType.PRESERVE_PREFIXES || currency == AffixCurrencyType.PRESERVE_SUFFIXES) {
                killer.playNotifySound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.25f)
            }
        }
    }

    @SubscribeEvent
    fun onLootTableLoad(event: LootTableLoadEvent) {
        val id: ResourceLocation = event.name
        if (!id.path.startsWith("chests/")) return
        event.table.addPool(
            LootPool.lootPool()
                .name("tconaffixes:affixed_part_cache")
                .setRolls(ConstantValue.exactly(1f))
                .`when`(LootItemRandomChanceCondition.randomChance(TConAffixConfig.chestCacheChance().toFloat()))
                .add(LootItem.lootTableItem(AffixItems.CACHE.get()))
                .build()
        )
        event.table.addPool(
            LootPool.lootPool()
                .name("tconaffixes:reforging_currency")
                .setRolls(ConstantValue.exactly(1f))
                .`when`(LootItemRandomChanceCondition.randomChance(TConAffixConfig.chestCurrencyChance().toFloat()))
                .add(LootItem.lootTableItem(AffixItems.RECASTING_FLUX.get()).setWeight(55))
                .add(LootItem.lootTableItem(AffixItems.SHEARING_FLUX.get()).setWeight(25))
                .add(LootItem.lootTableItem(AffixItems.GRAFTING_FLUX.get()).setWeight(18))
                .add(LootItem.lootTableItem(AffixItems.RUINOUS_FLUX.get()).setWeight(2))
                .build()
        )
    }

    internal fun rollKillCurrency(random: net.minecraft.util.RandomSource): AffixCurrencyType {
        val roll = random.nextInt(100)
        return when {
            roll < 45 -> AffixCurrencyType.RECAST
            roll < 70 -> AffixCurrencyType.REMOVE
            roll < 85 -> AffixCurrencyType.ADD
            roll < 90 -> AffixCurrencyType.PRESERVE_PREFIXES
            roll < 95 -> AffixCurrencyType.PRESERVE_SUFFIXES
            else -> AffixCurrencyType.MUTATE
        }
    }
}
