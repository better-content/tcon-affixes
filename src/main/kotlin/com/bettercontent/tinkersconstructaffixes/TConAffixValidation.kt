package com.bettercontent.tinkersconstructaffixes

import com.mojang.logging.LogUtils
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.event.config.ModConfigEvent
import net.minecraftforge.registries.ForgeRegistries
import slimeknights.tconstruct.library.events.MaterialsLoadedEvent
import slimeknights.tconstruct.library.materials.MaterialRegistry
import slimeknights.tconstruct.library.materials.definition.IMaterial
import slimeknights.tconstruct.library.materials.definition.MaterialId
import slimeknights.tconstruct.library.modifiers.ModifierId
import slimeknights.tconstruct.library.modifiers.ModifierManager
import slimeknights.tconstruct.library.tools.part.ToolPartItem

object TConAffixValidation {
    private val logger = LogUtils.getLogger()

    @SubscribeEvent
    fun onMaterialsLoaded(event: MaterialsLoadedEvent) = validateMaterials()

    @SubscribeEvent
    fun onModifiersLoaded(event: ModifierManager.ModifiersLoadedEvent) = validateModifiers()

    fun onConfigLoading(event: ModConfigEvent.Loading) = validateAfterConfigChange(event)

    fun onConfigReloading(event: ModConfigEvent.Reloading) = validateAfterConfigChange(event)

    private fun validateAfterConfigChange(event: ModConfigEvent) {
        if (event.config.getSpec<net.minecraftforge.common.ForgeConfigSpec>() !== TConAffixConfig.SPEC) return
        if (MaterialRegistry.isFullyLoaded()) validateMaterials()
        if (ModifierManager.INSTANCE.isDynamicModifiersLoaded) validateModifiers()
    }

    private fun validateMaterials() {
        val registry = MaterialRegistry.getInstance()
        var errors = 0
        val origins = listOf(AffixOrigin.GLOBAL, AffixOrigin.NETHER, AffixOrigin.AETHER, AffixOrigin.UNDERGARDEN, AffixOrigin.OTHERSIDE)
        origins.forEach { origin ->
            for (tier in 1..4) {
                AffixOrigins.materialIds(origin, tier).forEach materialLoop@{ rawId ->
                    val id = MaterialId.tryParse(rawId)
                    if (id == null) {
                        logger.error("Tinkers Construct Affixes {} tier {} contains malformed material ID {}", origin.id, tier, rawId)
                        errors++
                        return@materialLoop
                    }
                    val material = registry.getMaterial(id)
                    val declaredTier = material.tier.coerceAtLeast(1)
                    when {
                        material == IMaterial.UNKNOWN -> {
                            logger.error("Tinkers Construct Affixes {} tier {} material {} is not loaded", origin.id, tier, id)
                            errors++
                        }
                        material.isHidden -> {
                            logger.error("Tinkers Construct Affixes {} tier {} material {} is hidden", origin.id, tier, id)
                            errors++
                        }
                        declaredTier != tier -> {
                            logger.error("Tinkers Construct Affixes {} material {} declares tier {} but is configured in tier {}", origin.id, id, declaredTier, tier)
                            errors++
                        }
                    }
                }
            }
        }

        origins.forEach { origin ->
            val poolModes = if (origin == AffixOrigin.GLOBAL) listOf(false) else listOf(false, true)
            poolModes.forEach { exclusive ->
                val profiles = TConAffixRewards.allPartProfiles.filter { AffixOrigins.allowsPart(origin, it.itemId, exclusive) }
                var viableProfiles = 0
                profiles.forEach profileLoop@{ profile ->
                    val id = ResourceLocation.tryParse(profile.itemId)
                    val part = id?.let { ForgeRegistries.ITEMS.getValue(it) } as? ToolPartItem
                    if (part == null) {
                        if (exclusive) {
                            logger.warn(
                                "Tinkers Construct Affixes {} exclusive pool part {} is unavailable; rolls will use the themed fallback pool",
                                origin.id, profile.itemId
                            )
                        } else {
                            logger.error(
                                "Tinkers Construct Affixes {} themed pool part {} is missing or is not a ToolPartItem",
                                origin.id, profile.itemId
                            )
                            errors++
                        }
                        return@profileLoop
                    }
                    val materialsByTier = TConAffixRewards.viableMaterialsByTier(part, origin)
                    val compatibleTiers = (1..4).filter { materialsByTier[it].orEmpty().isNotEmpty() }
                    if (compatibleTiers.isEmpty()) {
                        logger.error(
                            "Tinkers Construct Affixes {} {} pool part {} has no compatible loaded, visible material",
                            origin.id, poolName(exclusive), profile.itemId
                        )
                        errors++
                    } else if (!TConAffixRewards.hasPositiveWeightedMaterials(materialsByTier, origin)) {
                        logger.error(
                            "Tinkers Construct Affixes {} {} pool part {} has compatible tiers {} but all have zero weight",
                            origin.id, poolName(exclusive), profile.itemId, compatibleTiers.joinToString(",")
                        )
                        errors++
                    } else {
                        viableProfiles++
                    }
                }
                if (profiles.isNotEmpty() && viableProfiles == 0) {
                    if (exclusive) {
                        logger.warn("Tinkers Construct Affixes {} exclusive part pool has no viable reward profile; rolls will use the themed fallback pool", origin.id)
                    } else {
                        logger.error("Tinkers Construct Affixes {} themed part pool has no viable reward profile", origin.id)
                        errors++
                    }
                }
            }
        }
        if (errors == 0) logger.info("Tinkers Construct Affixes validated {} part profiles across {} physical origin pools", TConAffixRewards.allPartProfiles.size, origins.size)
        else logger.error("Tinkers Construct Affixes material validation found {} error(s); invalid rewards will fail closed", errors)
    }

    private fun poolName(exclusive: Boolean): String = if (exclusive) "exclusive" else "themed"

    private fun validateModifiers() {
        val ids = TConAffixRewards.affixPool.flatMap { it.modifiers }.map { it.id }.toSet()
        val invalid = ids.filter { id -> ModifierId.tryParse(id)?.let(ModifierManager.INSTANCE::contains) != true }
        if (invalid.isEmpty()) logger.info("Tinkers Construct Affixes validated {} native modifier grants", ids.size)
        else logger.error("Tinkers Construct Affixes will ignore unknown native modifier grants: {}", invalid.joinToString(", "))
    }
}
