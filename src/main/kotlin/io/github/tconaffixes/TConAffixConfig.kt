package io.github.tconaffixes

import net.minecraftforge.common.ForgeConfigSpec

object TConAffixConfig {
    private val builder = ForgeConfigSpec.Builder()

    val hostileDropChance: ForgeConfigSpec.DoubleValue = builder
        .comment("Chance for a hostile mob to drop an affixed Tinkers part.")
        .defineInRange("hostileDropChance", 0.01, 0.0, 1.0)

    val chestCacheChance: ForgeConfigSpec.DoubleValue = builder
        .comment("Chance for a chests/* loot table to contain an Affixed Part Cache.")
        .defineInRange("chestCacheChance", 0.03, 0.0, 1.0)

    val materialTierWeights: ForgeConfigSpec.ConfigValue<List<out Int>> = builder
        .comment("Relative weights for TConstruct material tiers 1 through 4.")
        .defineList("materialTierWeights", listOf(8000, 1700, 290, 10), { value -> value is Int && value >= 0 })

    val tier1Materials = materialList("tier1Materials", listOf(
        "tconstruct:bamboo", "tconstruct:bone", "tconstruct:cactus", "tconstruct:chorus", "tconstruct:clay",
        "tconstruct:copper", "tconstruct:feather", "tconstruct:flint", "tconstruct:honey", "tconstruct:leather",
        "tconstruct:leaves", "tconstruct:paper", "tconstruct:phantom", "tconstruct:rock", "tconstruct:string",
        "tconstruct:vine", "tconstruct:wood", "tconstruct:wool"
    ))
    val tier2Materials = materialList("tier2Materials", listOf(
        "tconstruct:aluminum", "tconstruct:amethyst", "tconstruct:blaze", "tconstruct:blood", "tconstruct:earthslime",
        "tconstruct:ender_pearl", "tconstruct:glass", "tconstruct:gold", "tconstruct:gunpowder", "tconstruct:iron",
        "tconstruct:ironwood", "tconstruct:lead", "tconstruct:necrotic_bone", "tconstruct:osmium", "tconstruct:prismarine",
        "tconstruct:scorched_stone", "tconstruct:seared_stone", "tconstruct:silver", "tconstruct:skyslime",
        "tconstruct:skyslime_vine", "tconstruct:slimeball", "tconstruct:slimeskin", "tconstruct:slimewood",
        "tconstruct:twisting_vine", "tconstruct:venombone", "tconstruct:weeping_vine",
        "tconstruct:whitestone"
    ))
    val tier3Materials = materialList("tier3Materials", listOf(
        "tconstruct:amethyst_bronze", "tconstruct:bronze", "tconstruct:cobalt", "tconstruct:constantan",
        "tconstruct:darkthread", "tconstruct:electrum", "tconstruct:glowstone", "tconstruct:ice", "tconstruct:ichor",
        "tconstruct:ichorskin", "tconstruct:invar", "tconstruct:magma", "tconstruct:magnetite", "tconstruct:nahuatl",
        "tconstruct:necronium", "tconstruct:obsidian", "tconstruct:pewter", "tconstruct:pig_iron",
        "tconstruct:plated_slimewood", "tconstruct:quartz", "tconstruct:rose_gold", "tconstruct:slimesteel",
        "tconstruct:steel", "tconstruct:steeleaf"
    ))
    val tier4Materials = materialList("tier4Materials", listOf(
        "tconstruct:ancient_hide", "tconstruct:blazewood", "tconstruct:blazing_bone", "tconstruct:cinderslime",
        "tconstruct:dragon_scale", "tconstruct:end_rod", "tconstruct:enderslime", "tconstruct:enderslime_vine",
        "tconstruct:fiery", "tconstruct:hepatizon", "tconstruct:knightly", "tconstruct:knightmetal",
        "tconstruct:manyullyn", "tconstruct:queens_slime", "tconstruct:shulker"
    ))

    val SPEC: ForgeConfigSpec = builder.build()

    fun tierWeights(): List<Int> = materialTierWeights.get().map { it.coerceAtLeast(0) }.let { weights ->
        List(4) { index -> weights.getOrElse(index) { 0 } }
    }

    fun materialsForTier(tier: Int): List<String> = when (tier) {
        1 -> tier1Materials.get()
        2 -> tier2Materials.get()
        3 -> tier3Materials.get()
        4 -> tier4Materials.get()
        else -> emptyList()
    }

    private fun materialList(name: String, defaults: List<String>): ForgeConfigSpec.ConfigValue<List<out String>> {
        return builder.comment("Allowed material IDs for tier ${name.removePrefix("tier").removeSuffix("Materials")}.")
            .defineList(name, defaults, { value -> value is String && value.contains(':') })
    }
}
