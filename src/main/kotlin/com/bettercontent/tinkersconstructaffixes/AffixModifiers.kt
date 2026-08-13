package com.bettercontent.tinkersconstructaffixes

import net.minecraft.resources.ResourceLocation
import slimeknights.tconstruct.library.modifiers.Modifier
import slimeknights.tconstruct.library.modifiers.ModifierEntry
import slimeknights.tconstruct.library.modifiers.ModifierHooks
import slimeknights.tconstruct.library.modifiers.hook.build.ToolStatsModifierHook
import slimeknights.tconstruct.library.modifiers.util.ModifierDeferredRegister
import slimeknights.tconstruct.library.module.ModuleHookMap
import slimeknights.tconstruct.library.tools.nbt.IToolContext
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder
import slimeknights.tconstruct.library.tools.stat.ToolStatId
import slimeknights.tconstruct.library.tools.stat.ToolStats

object AffixModifiers {
    val REGISTRY: ModifierDeferredRegister = ModifierDeferredRegister.create(TConAffixesMod.MOD_ID)
    internal val STAT_DRIVER = REGISTRY.register("affix_stats") { AffixStatsModifier() }
    val MULTIPLIERS_KEY = ResourceLocation(TConAffixesMod.MOD_ID, "stat_multipliers")
    val OWNED_MODIFIERS_KEY = ResourceLocation(TConAffixesMod.MOD_ID, "owned_modifiers")
}

internal class AffixStatsModifier : Modifier(), ToolStatsModifierHook {
    override fun registerHooks(builder: ModuleHookMap.Builder) {
        super.registerHooks(builder)
        builder.addHook(this, ModifierHooks.TOOL_STATS)
    }

    override fun addToolStats(context: IToolContext, modifier: ModifierEntry, builder: ModifierStatsBuilder) {
        val multipliers = context.persistentData.getCompound(AffixModifiers.MULTIPLIERS_KEY)
        multipliers.allKeys.forEach { statId ->
            val multiplier = multipliers.getFloat(statId)
            if (multiplier <= 0.0f || multiplier == 1.0f) return@forEach
            val stat = ToolStats.getToolStat(ToolStatId.tryParse(statId) ?: return@forEach)
            val numeric = stat as? slimeknights.tconstruct.library.tools.stat.INumericToolStat<*> ?: return@forEach
            if (!numeric.supports(context.item)) return@forEach
            builder.multiplier(numeric, multiplier.toDouble())
        }
    }

    override fun shouldDisplay(advanced: Boolean): Boolean = false
}
