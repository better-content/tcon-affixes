package com.bettercontent.tinkersconstructaffixes

import net.minecraft.nbt.CompoundTag
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import slimeknights.tconstruct.library.materials.definition.IMaterial
import slimeknights.tconstruct.library.tools.part.ToolPartItem

internal enum class AffixCurrencyType {
    RECAST,
    ADD,
    REMOVE,
    PRESERVE_PREFIXES,
    PRESERVE_SUFFIXES,
    MUTATE
}

internal enum class AffixOperationResult {
    APPLIED,
    DESTROYED,
    INCOMPATIBLE,
    LOCKED,
    NO_AFFIXES,
    FULL,
    NEEDS_BOTH_SIDES
}

internal object AffixCrafting {
    const val MUTATION_LOCKED_TAG = "tinkers_construct_affixes_mutation_locked"
    const val SALVAGE_BAND_TAG = "tinkers_construct_affixes_salvage_band"
    const val SALVAGE_SEED_TAG = "tinkers_construct_affixes_salvage_seed"
    const val SALVAGE_SPENT_TAG = "tinkers_construct_affixes_salvage_spent"
    const val ORIGIN_TAG = "tinkers_construct_affixes_origin"
    const val PROVENANCE_TAG = "tinkers_construct_affixes_provenance"
    const val NATURAL_TAG = "tinkers_construct_affixes_natural"
    const val DATA_VERSION_TAG = "tinkers_construct_affixes_data_version"
    const val DATA_VERSION = 2

    fun canTarget(stack: ItemStack): Boolean = stack.count == 1 && profile(stack) != null

    fun apply(stack: ItemStack, currency: AffixCurrencyType, random: RandomSource): AffixOperationResult {
        val profile = profile(stack) ?: return AffixOperationResult.INCOMPATIBLE
        if (stack.tag?.getBoolean(MUTATION_LOCKED_TAG) == true) return AffixOperationResult.LOCKED
        val affixes = TConAffixRewards.existingToolAffixes(stack)
        val partId = profile.itemId
        val origin = physicalOrigin(stack, partId)
        val next = when (currency) {
            AffixCurrencyType.RECAST -> TConAffixRewards.rollAffixes(partId, profile.family, random, origin = origin)
            AffixCurrencyType.ADD -> {
                if (affixes.size >= 6) return AffixOperationResult.FULL
                val added = TConAffixRewards.rollAdditionalAffix(partId, profile.family, affixes, random, origin)
                    ?: return AffixOperationResult.FULL
                affixes + added
            }
            AffixCurrencyType.REMOVE -> {
                if (affixes.isEmpty()) return AffixOperationResult.NO_AFFIXES
                affixes.filterIndexed { index, _ -> index != random.nextInt(affixes.size) }
            }
            AffixCurrencyType.PRESERVE_PREFIXES -> preserveSide(
                affixes, TConAffixRewards.AffixKind.PREFIX, partId, profile.family, origin, random
            ) ?: return AffixOperationResult.NEEDS_BOTH_SIDES
            AffixCurrencyType.PRESERVE_SUFFIXES -> preserveSide(
                affixes, TConAffixRewards.AffixKind.SUFFIX, partId, profile.family, origin, random
            ) ?: return AffixOperationResult.NEEDS_BOTH_SIDES
            AffixCurrencyType.MUTATE -> return mutate(stack, affixes, partId, profile.family, origin, random)
        }
        TConAffixRewards.writeToolAffixes(stack, next)
        markForged(stack, origin)
        return AffixOperationResult.APPLIED
    }

    fun salvage(stack: ItemStack): List<AffixCurrencyType>? {
        if (profile(stack) == null || TConAffixRewards.existingToolAffixes(stack).isEmpty()) return null
        TConAffixRewards.writeToolAffixes(stack, emptyList())
        val tag = stack.orCreateTag
        if (tag.getBoolean(SALVAGE_SPENT_TAG)) return emptyList()
        tag.putBoolean(SALVAGE_SPENT_TAG, true)
        val band = tag.getInt(SALVAGE_BAND_TAG).coerceIn(0, 4)
        if (band == 0) return emptyList()
        val random = RandomSource.create(tag.getLong(SALVAGE_SEED_TAG))
        val common = List(band.coerceAtMost(3)) { rollSalvageCommon(random) }.toMutableList()
        if (band == 4 && random.nextFloat() < 0.10f) common += AffixCurrencyType.MUTATE
        return common
    }

    fun stampNatural(stack: ItemStack, origin: AffixOrigin, provenance: String, random: RandomSource) {
        val affixes = TConAffixRewards.existingToolAffixes(stack)
        val tag = stack.orCreateTag
        tag.putInt(DATA_VERSION_TAG, DATA_VERSION)
        tag.putString(ORIGIN_TAG, origin.id)
        tag.putString(PROVENANCE_TAG, provenance)
        tag.putBoolean(NATURAL_TAG, true)
        tag.putBoolean(SALVAGE_SPENT_TAG, false)
        tag.putInt(SALVAGE_BAND_TAG, salvageBand(affixes, origin))
        tag.putLong(SALVAGE_SEED_TAG, random.nextLong())
    }

    internal fun salvageBand(affixes: List<CompoundTag>, origin: AffixOrigin): Int {
        if (affixes.isEmpty()) return 0
        val quality = affixes.sumOf { affix ->
            val tier = affix.getInt("tier")
            if (tier <= 0) 4 else (6 - tier).coerceIn(1, 5)
        } + affixes.size + if (origin == AffixOrigin.GLOBAL) 0 else 3
        return when {
            quality >= 29 -> 4
            quality >= 20 -> 3
            quality >= 12 -> 2
            else -> 1
        }
    }

    private fun preserveSide(
        affixes: List<CompoundTag>,
        preservedKind: TConAffixRewards.AffixKind,
        partId: String,
        family: TConAffixRewards.PartFamily,
        origin: AffixOrigin,
        random: RandomSource
    ): List<CompoundTag>? {
        val prefixes = affixes.filter { it.getString("kind") == TConAffixRewards.AffixKind.PREFIX.id }
        val suffixes = affixes.filter { it.getString("kind") == TConAffixRewards.AffixKind.SUFFIX.id }
        if (prefixes.isEmpty() || suffixes.isEmpty()) return null
        val preserved = if (preservedKind == TConAffixRewards.AffixKind.PREFIX) prefixes else suffixes
        val rerolledKind = if (preservedKind == TConAffixRewards.AffixKind.PREFIX) TConAffixRewards.AffixKind.SUFFIX else TConAffixRewards.AffixKind.PREFIX
        val rerolled = TConAffixRewards.rollAffixSide(
            partId, family, rerolledKind, random.nextInt(3) + 1, preserved, random, origin
        )
        return preserved.map(CompoundTag::copy) + rerolled
    }

    private fun mutate(
        stack: ItemStack,
        affixes: List<CompoundTag>,
        partId: String,
        family: TConAffixRewards.PartFamily,
        origin: AffixOrigin,
        random: RandomSource
    ): AffixOperationResult {
        if (affixes.isEmpty()) return AffixOperationResult.NO_AFFIXES
        val roll = random.nextFloat()
        if (roll >= 0.80f) {
            stack.shrink(1)
            return AffixOperationResult.DESTROYED
        }
        val next = when {
            roll < 0.35f -> affixes + mutationImplicit(partId, family, random)
            roll < 0.60f -> elevate(affixes, random) ?: (affixes + mutationImplicit(partId, family, random))
            else -> TConAffixRewards.rollAffixes(
                partId, family, random, origin = origin, targetCount = 4 + random.nextInt(3), lucky = true,
                guaranteeOriginAffix = origin != AffixOrigin.GLOBAL
            )
        }
        TConAffixRewards.writeToolAffixes(stack, next)
        markForged(stack, origin)
        stack.orCreateTag.putBoolean(MUTATION_LOCKED_TAG, true)
        return AffixOperationResult.APPLIED
    }

    private fun elevate(affixes: List<CompoundTag>, random: RandomSource): List<CompoundTag>? {
        val candidates = affixes.mapIndexedNotNull { index, tag ->
            val definition = TConAffixRewards.definition(tag.getString("id")) ?: return@mapIndexedNotNull null
            val tier = tag.getInt("tier")
            if (tier > 1) Triple(index, definition, tier - 1) else null
        }
        if (candidates.isEmpty()) return null
        val (index, definition, rank) = candidates[random.nextInt(candidates.size)]
        val tier = definition.tiers.firstOrNull { it.rank == rank } ?: return null
        return affixes.mapIndexed { at, tag ->
            if (at == index) TConAffixRewards.createAffix(definition, tier, tag.getString("source_part"), random) else tag.copy()
        }
    }

    private fun mutationImplicit(partId: String, family: TConAffixRewards.PartFamily, random: RandomSource): CompoundTag {
        val stat = when (family) {
            TConAffixRewards.PartFamily.MELEE_HEAD -> "tconstruct:attack_damage"
            TConAffixRewards.PartFamily.TOOL_HEAD -> "tconstruct:mining_speed"
            TConAffixRewards.PartFamily.HANDLE, TConAffixRewards.PartFamily.BINDING -> "tconstruct:durability"
            TConAffixRewards.PartFamily.BOW -> "tconstruct:draw_speed"
            TConAffixRewards.PartFamily.RANGED -> "tconstruct:projectile_damage"
            TConAffixRewards.PartFamily.ARMOR -> "tconstruct:armor_toughness"
            TConAffixRewards.PartFamily.SHIELD -> "tconstruct:knockback_resistance"
        }
        return CompoundTag().apply {
            putString("id", "mutation:${family.name.lowercase()}")
            putString("name", "Ruin-Touched")
            putString("kind", TConAffixRewards.AffixKind.IMPLICIT.id)
            putString("group", "mutation_implicit")
            putString("tier_name", "mutated")
            putInt("tier", 0)
            putString("source_part", partId)
            putString("stat", stat)
            val percent = 0.22 + random.nextDouble() * 0.10
            putDouble("percent", percent)
            put("rolls", TConAffixRewards.rollList(listOf(stat to percent)))
        }
    }

    private fun profile(stack: ItemStack): TConAffixRewards.PartProfile? {
        if (stack.item !is ToolPartItem) return null
        return TConAffixRewards.partProfile(stack)
    }

    private fun physicalOrigin(stack: ItemStack, partId: String): AffixOrigin {
        val part = stack.item as? ToolPartItem ?: return AffixOrigin.GLOBAL
        val material = part.getMaterial(stack).takeUnless { it == IMaterial.UNKNOWN_ID }?.toString().orEmpty()
        return AffixOrigins.physicalOrigin(partId, material)
    }

    private fun markForged(stack: ItemStack, origin: AffixOrigin) {
        val tag = stack.orCreateTag
        tag.putInt(DATA_VERSION_TAG, DATA_VERSION)
        tag.putString(ORIGIN_TAG, origin.id)
        tag.putBoolean(NATURAL_TAG, false)
    }

    private fun rollSalvageCommon(random: RandomSource): AffixCurrencyType = when (random.nextInt(10)) {
        in 0..5 -> AffixCurrencyType.RECAST
        in 6..7 -> AffixCurrencyType.REMOVE
        else -> AffixCurrencyType.ADD
    }
}
