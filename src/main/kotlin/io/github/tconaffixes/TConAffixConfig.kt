package io.github.tconaffixes

import net.minecraftforge.common.ForgeConfigSpec

object TConAffixConfig {
    private const val DEFAULT_HOSTILE_DROP_CHANCE = 0.01
    private const val DEFAULT_FONT_DROP_CHANCE = 0.03
    private const val DEFAULT_HOSTILE_CURRENCY_CHANCE = 0.01
    private const val DEFAULT_FONT_CURRENCY_CHANCE = 0.04
    private const val DEFAULT_CHEST_CACHE_CHANCE = 0.03
    private const val DEFAULT_CHEST_CURRENCY_CHANCE = 0.03
    private val DEFAULT_TIER_WEIGHTS = listOf(8000, 1700, 290, 10)
    private val DEFAULT_TIER_1_MATERIALS = listOf(
        "tconstruct:bamboo", "tconstruct:bone", "tconstruct:cactus", "tconstruct:chorus", "tconstruct:clay",
        "tconstruct:copper", "tconstruct:feather", "tconstruct:flint", "tconstruct:honey", "tconstruct:leather",
        "tconstruct:leaves", "tconstruct:paper", "tconstruct:phantom", "tconstruct:rock", "tconstruct:string",
        "tconstruct:vine", "tconstruct:wood", "tconstruct:wool"
    )
    private val DEFAULT_TIER_2_MATERIALS = listOf(
        "tconstruct:aluminum", "tconstruct:amethyst", "tconstruct:blaze", "tconstruct:blood", "tconstruct:earthslime",
        "tconstruct:ender_pearl", "tconstruct:glass", "tconstruct:gold", "tconstruct:gunpowder", "tconstruct:iron",
        "tconstruct:ironwood", "tconstruct:lead", "tconstruct:necrotic_bone", "tconstruct:osmium", "tconstruct:prismarine",
        "tconstruct:scorched_stone", "tconstruct:seared_stone", "tconstruct:silver", "tconstruct:skyslime",
        "tconstruct:skyslime_vine", "tconstruct:slimeball", "tconstruct:slimeskin", "tconstruct:slimewood",
        "tconstruct:twisting_vine", "tconstruct:venombone", "tconstruct:weeping_vine", "tconstruct:whitestone"
    )
    private val DEFAULT_TIER_3_MATERIALS = listOf(
        "tconstruct:amethyst_bronze", "tconstruct:bronze", "tconstruct:cobalt", "tconstruct:constantan",
        "tconstruct:darkthread", "tconstruct:electrum", "tconstruct:glowstone", "tconstruct:ice", "tconstruct:ichor",
        "tconstruct:ichorskin", "tconstruct:invar", "tconstruct:magma", "tconstruct:magnetite", "tconstruct:nahuatl",
        "tconstruct:necronium", "tconstruct:obsidian", "tconstruct:pewter", "tconstruct:pig_iron",
        "tconstruct:plated_slimewood", "tconstruct:quartz", "tconstruct:rose_gold", "tconstruct:slimesteel",
        "tconstruct:steel", "tconstruct:steeleaf"
    )
    private val DEFAULT_TIER_4_MATERIALS = listOf(
        "tconstruct:ancient_hide", "tconstruct:blazewood", "tconstruct:blazing_bone", "tconstruct:cinderslime",
        "tconstruct:dragon_scale", "tconstruct:end_rod", "tconstruct:enderslime", "tconstruct:enderslime_vine",
        "tconstruct:fiery", "tconstruct:hepatizon", "tconstruct:knightly", "tconstruct:knightmetal",
        "tconstruct:manyullyn", "tconstruct:queens_slime", "tconstruct:shulker"
    )
    private val builder = ForgeConfigSpec.Builder()

    val hostileDropChance: ForgeConfigSpec.DoubleValue = builder
        .comment("Chance for a player-killed hostile mob to drop an affixed Tinkers part outside Font dimensions.")
        .defineInRange("hostileDropChance", DEFAULT_HOSTILE_DROP_CHANCE, 0.0, 1.0)

    val fontDropChance: ForgeConfigSpec.DoubleValue = builder
        .comment("Chance for a player-killed hostile mob in a Font dimension to drop an origin part.")
        .defineInRange("fontDropChance", DEFAULT_FONT_DROP_CHANCE, 0.0, 1.0)

    val hostileCurrencyChance: ForgeConfigSpec.DoubleValue = builder
        .comment("Chance for a player-killed hostile mob to drop one reforging currency outside Font dimensions.")
        .defineInRange("hostileCurrencyChance", DEFAULT_HOSTILE_CURRENCY_CHANCE, 0.0, 1.0)

    val fontCurrencyChance: ForgeConfigSpec.DoubleValue = builder
        .comment("Chance for a player-killed hostile mob in a Font dimension to drop one reforging currency.")
        .defineInRange("fontCurrencyChance", DEFAULT_FONT_CURRENCY_CHANCE, 0.0, 1.0)

    val chestCacheChance: ForgeConfigSpec.DoubleValue = builder
        .comment("Chance for a chests/* loot table to contain an Affixed Part Cache.")
        .defineInRange("chestCacheChance", DEFAULT_CHEST_CACHE_CHANCE, 0.0, 1.0)

    val chestCurrencyChance: ForgeConfigSpec.DoubleValue = builder
        .comment("Chance for a chests/* loot table to contain a non-seal reforging currency.")
        .defineInRange("chestCurrencyChance", DEFAULT_CHEST_CURRENCY_CHANCE, 0.0, 1.0)

    val materialTierWeights: ForgeConfigSpec.ConfigValue<List<out Int>> = builder
        .comment("Relative weights for TConstruct material tiers 1 through 4.")
        .defineList("materialTierWeights", DEFAULT_TIER_WEIGHTS, { value -> value is Int && value >= 0 })

    val tier1Materials = materialList("tier1Materials", DEFAULT_TIER_1_MATERIALS)
    val tier2Materials = materialList("tier2Materials", DEFAULT_TIER_2_MATERIALS)
    val tier3Materials = materialList("tier3Materials", DEFAULT_TIER_3_MATERIALS)
    val tier4Materials = materialList("tier4Materials", DEFAULT_TIER_4_MATERIALS)

    val SPEC: ForgeConfigSpec = builder.build()

    fun hostileDropChance(): Double = safeGet(hostileDropChance, DEFAULT_HOSTILE_DROP_CHANCE)
    fun fontDropChance(): Double = safeGet(fontDropChance, DEFAULT_FONT_DROP_CHANCE)
    fun hostileCurrencyChance(): Double = safeGet(hostileCurrencyChance, DEFAULT_HOSTILE_CURRENCY_CHANCE)
    fun fontCurrencyChance(): Double = safeGet(fontCurrencyChance, DEFAULT_FONT_CURRENCY_CHANCE)
    fun chestCacheChance(): Double = safeGet(chestCacheChance, DEFAULT_CHEST_CACHE_CHANCE)
    fun chestCurrencyChance(): Double = safeGet(chestCurrencyChance, DEFAULT_CHEST_CURRENCY_CHANCE)

    fun tierWeights(): List<Int> = safeGet(materialTierWeights, DEFAULT_TIER_WEIGHTS).map { it.coerceAtLeast(0) }.let { weights ->
        List(4) { index -> weights.getOrElse(index) { 0 } }
    }

    fun materialsForTier(tier: Int): List<String> = when (tier) {
        1 -> safeGet(tier1Materials, DEFAULT_TIER_1_MATERIALS)
        2 -> safeGet(tier2Materials, DEFAULT_TIER_2_MATERIALS)
        3 -> safeGet(tier3Materials, DEFAULT_TIER_3_MATERIALS)
        4 -> safeGet(tier4Materials, DEFAULT_TIER_4_MATERIALS)
        else -> emptyList()
    }

    private fun materialList(name: String, defaults: List<String>): ForgeConfigSpec.ConfigValue<List<out String>> {
        return builder.comment("Allowed material IDs for tier ${name.removePrefix("tier").removeSuffix("Materials")}.")
            .defineList(name, defaults, { value -> value is String && value.contains(':') })
    }

    private fun <T> safeGet(value: ForgeConfigSpec.ConfigValue<out T>, fallback: T): T =
        runCatching { value.get() }.getOrDefault(fallback)
}
