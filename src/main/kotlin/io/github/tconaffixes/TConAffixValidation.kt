package io.github.tconaffixes

import com.mojang.logging.LogUtils
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.event.config.ModConfigEvent
import net.minecraftforge.registries.ForgeRegistries
import slimeknights.tconstruct.library.events.MaterialsLoadedEvent
import slimeknights.tconstruct.library.materials.MaterialRegistry
import slimeknights.tconstruct.library.materials.definition.IMaterial
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
                AffixOrigins.materialIds(origin, tier).mapNotNull(slimeknights.tconstruct.library.materials.definition.MaterialId::tryParse).forEach { id ->
                val material = registry.getMaterial(id)
                val declaredTier = material.tier.coerceAtLeast(1)
                if (material == IMaterial.UNKNOWN || material.isHidden || declaredTier != tier) {
                    logger.error("TCon Affixes {} material {} is unavailable, hidden, or not in configured tier {}", origin.id, id, tier)
                    errors++
                }
            }
            }
        }

        TConAffixRewards.allPartProfiles.forEach { profile ->
            val part = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(profile.itemId)) as? ToolPartItem
            val partOrigin = AffixOrigins.exclusiveParts[profile.itemId]
            val relevantOrigins = partOrigin?.let(::listOf) ?: origins
            val usable = part != null && relevantOrigins.any { origin ->
                (1..4).any { tier ->
                    AffixOrigins.materialIds(origin, tier).mapNotNull(slimeknights.tconstruct.library.materials.definition.MaterialId::tryParse).any { id ->
                        val material = registry.getMaterial(id)
                        material != IMaterial.UNKNOWN && !material.isHidden && part.canUseMaterial(material)
                    }
                }
            }
            if (!usable) {
                logger.error("TCon Affixes part {} has no usable configured material", profile.itemId)
                errors++
            }
        }
        if (errors == 0) logger.info("TCon Affixes validated {} part profiles across {} physical origin pools", TConAffixRewards.allPartProfiles.size, origins.size)
        else logger.error("TCon Affixes material validation found {} error(s); invalid rewards will fail closed", errors)
    }

    private fun validateModifiers() {
        val ids = TConAffixRewards.affixPool.flatMap { it.modifiers }.map { it.id }.toSet()
        val invalid = ids.filter { id -> ModifierId.tryParse(id)?.let(ModifierManager.INSTANCE::contains) != true }
        if (invalid.isEmpty()) logger.info("TCon Affixes validated {} native modifier grants", ids.size)
        else logger.error("TCon Affixes will ignore unknown native modifier grants: {}", invalid.joinToString(", "))
    }
}
