package io.github.tconaffixes

import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

@Mod(TConAffixesMod.MOD_ID)
class TConAffixesMod {
    init {
        val modBus = FMLJavaModLoadingContext.get().modEventBus
        AffixItems.REGISTRY.register(modBus)
        AffixModifiers.REGISTRY.register(modBus)
        modBus.addListener(TConAffixValidation::onConfigLoading)
        modBus.addListener(TConAffixValidation::onConfigReloading)
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TConAffixConfig.SPEC)
        MinecraftForge.EVENT_BUS.register(TConAffixRewards)
        MinecraftForge.EVENT_BUS.register(GlobalAffixLoot)
        MinecraftForge.EVENT_BUS.register(TConAffixValidation)
    }

    companion object {
        const val MOD_ID = "tconaffixes"
    }
}
