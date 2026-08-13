package io.github.tconaffixes

import net.minecraft.resources.ResourceLocation

internal enum class AffixOrigin(val id: String) {
    GLOBAL("global"),
    NETHER("nether"),
    AETHER("aether"),
    UNDERGARDEN("undergarden"),
    OTHERSIDE("otherside"),
    MUTATION("mutation")
}

internal object AffixOrigins {
    private val dimensions = mapOf(
        ResourceLocation.tryParse("minecraft:the_nether")!! to AffixOrigin.NETHER,
        ResourceLocation.tryParse("aether:the_aether")!! to AffixOrigin.AETHER,
        ResourceLocation.tryParse("undergarden:undergarden")!! to AffixOrigin.UNDERGARDEN,
        ResourceLocation.tryParse("deeperdarker:otherside")!! to AffixOrigin.OTHERSIDE
    )

    val exclusiveParts = mapOf(
        "tinkersweaponry:great_blade" to AffixOrigin.NETHER,
        "tinker_rapier:slender_blade" to AffixOrigin.AETHER,
        "additionalweaponry:defensive_handle" to AffixOrigin.UNDERGARDEN,
        "tinkers_things:shield_plating" to AffixOrigin.UNDERGARDEN,
        "tinkersweaponry:spear_head" to AffixOrigin.OTHERSIDE
    )

    private val materials = buildMap {
        assign(AffixOrigin.NETHER,
            "tconstruct:blaze", "tconstruct:blazewood", "tconstruct:blazing_bone", "tconstruct:cinderslime",
            "tconstruct:cobalt", "tconstruct:fiery", "tconstruct:magma", "tconstruct:manyullyn", "tconstruct:nahuatl",
            "tconstruct:necronium", "tconstruct:necrotic_bone", "tconstruct:pig_iron", "tconstruct:scorched_stone",
            "tconstruct:twisting_vine", "tconstruct:weeping_vine", "tinkers_things:magmaskin")
        assign(AffixOrigin.AETHER,
            "tconstruct:skyslime", "tconstruct:skyslime_vine", "tconstruct:feather", "tconstruct:phantom",
            "tconstruct:whitestone", "tconstruct:ice", "tconaffixes:skyroot", "tconaffixes:holystone",
            "tconaffixes:zanite", "tconaffixes:ambrosium", "tconaffixes:gravitite")
        assign(AffixOrigin.UNDERGARDEN,
            "tconstruct:earthslime", "tconstruct:venombone", "tconstruct:ichor", "tconstruct:ichorskin",
            "tconstruct:ancient_hide", "tconstruct:magnetite", "tconaffixes:cloggrum", "tconaffixes:regalium",
            "tconaffixes:froststeel", "tconaffixes:utherium", "tconaffixes:forgotten")
        assign(AffixOrigin.OTHERSIDE,
            "tconstruct:darkthread", "tconstruct:chorus", "tconstruct:end_rod", "tconstruct:ender_pearl",
            "tconstruct:enderslime", "tconstruct:enderslime_vine", "tconstruct:shulker", "tconstruct:dragon_scale",
            "tconaffixes:echo_wood", "tconaffixes:sculk_bone", "tconaffixes:resonarium",
            "tconaffixes:reinforced_echo", "tconaffixes:warden_carapace")
    }

    private val exclusiveAffixes = buildMap {
        assign(AffixOrigin.NETHER, "charward", "sundercall", "emberspite", "of_live_coals", "fontbound_assault")
        assign(AffixOrigin.AETHER, "skywake", "of_held_breath", "drawn_pulse", "true_flight", "of_the_still_breath", "of_sure_arcs", "bowyers_lattice")
        assign(AffixOrigin.UNDERGARDEN, "quarry_echo", "deep_choir", "gravebound", "mirror_ward", "bulwark_latch", "of_deep_cuts", "of_the_anchor", "quarrywrights_patience", "bastion_plate")
        assign(AffixOrigin.OTHERSIDE, "gravehook", "frostwrit", "graveglass", "left_hand_lesson", "duelers_reflex", "of_the_bone_rack", "of_the_white_rime", "of_split_routes", "of_trophy_cables")
    }

    private val themedGlobalParts = mapOf(
        AffixOrigin.NETHER to setOf("tconstruct:small_blade", "tconstruct:broad_blade", "tconstruct:hammer_head", "tconstruct:broad_axe_head", "tconstruct:tool_handle", "tconstruct:tough_handle"),
        AffixOrigin.AETHER to setOf("tconstruct:bow_limb", "tconstruct:bow_grip", "tconstruct:bowstring", "tconstruct:arrow_head", "tconstruct:arrow_shaft", "tconstruct:fletching", "tconstruct:boots_plating"),
        AffixOrigin.UNDERGARDEN to setOf("tconstruct:pick_head", "tconstruct:hammer_head", "tconstruct:tool_binding", "tconstruct:tough_binding", "tconstruct:large_plate", "tconstruct:helmet_plating", "tconstruct:chestplate_plating", "tconstruct:leggings_plating", "tconstruct:boots_plating", "tconstruct:maille", "tconstruct:shield_core"),
        AffixOrigin.OTHERSIDE to setOf("tconstruct:small_blade", "tconstruct:arrow_head", "tconstruct:arrow_shaft", "tconstruct:bow_limb", "tconstruct:bow_grip", "tconstruct:tool_handle", "tconstruct:tough_handle")
    )

    fun fromDimension(dimension: ResourceLocation): AffixOrigin = dimensions[dimension] ?: AffixOrigin.GLOBAL

    fun materialOrigin(id: String): AffixOrigin = materials[id] ?: AffixOrigin.GLOBAL

    fun classifiedMaterialIds(): Set<String> = materials.keys

    fun physicalOrigin(partId: String, materialId: String): AffixOrigin {
        val part = exclusiveParts[partId] ?: AffixOrigin.GLOBAL
        val material = materialOrigin(materialId)
        return when {
            part == AffixOrigin.GLOBAL -> material
            material == AffixOrigin.GLOBAL || material == part -> part
            else -> AffixOrigin.GLOBAL
        }
    }

    fun allowsAffix(origin: AffixOrigin, affixId: String): Boolean {
        val exclusive = exclusiveAffixes[affixId]
        return exclusive == null || exclusive == origin
    }

    fun isExclusiveAffix(origin: AffixOrigin, affixId: String): Boolean = exclusiveAffixes[affixId] == origin

    fun allowsPart(origin: AffixOrigin, partId: String, exclusiveRoll: Boolean): Boolean {
        if (origin == AffixOrigin.GLOBAL) return partId !in exclusiveParts
        return if (exclusiveRoll) exclusiveParts[partId] == origin
        else partId in themedGlobalParts[origin].orEmpty()
    }

    fun materialIds(origin: AffixOrigin, tier: Int): List<String> {
        val configured = TConAffixConfig.materialsForTier(tier)
        if (origin == AffixOrigin.GLOBAL) return configured.filter { materialOrigin(it) == AffixOrigin.GLOBAL }
        val reserved = materials.filterValues { it == origin }.keys
        return (configured.filter { it in reserved } + localMaterialIds(origin, tier)).distinct()
    }

    private fun localMaterialIds(origin: AffixOrigin, tier: Int): List<String> = when (origin to tier) {
        AffixOrigin.AETHER to 1 -> listOf("tconaffixes:skyroot", "tconaffixes:holystone")
        AffixOrigin.AETHER to 2 -> listOf("tconaffixes:zanite")
        AffixOrigin.AETHER to 3 -> listOf("tconaffixes:ambrosium")
        AffixOrigin.AETHER to 4 -> listOf("tconaffixes:gravitite")
        AffixOrigin.UNDERGARDEN to 2 -> listOf("tconaffixes:cloggrum", "tconaffixes:regalium")
        AffixOrigin.UNDERGARDEN to 3 -> listOf("tconaffixes:froststeel", "tconaffixes:utherium")
        AffixOrigin.UNDERGARDEN to 4 -> listOf("tconaffixes:forgotten")
        AffixOrigin.OTHERSIDE to 1 -> listOf("tconaffixes:echo_wood")
        AffixOrigin.OTHERSIDE to 2 -> listOf("tconaffixes:sculk_bone")
        AffixOrigin.OTHERSIDE to 3 -> listOf("tconaffixes:resonarium", "tconaffixes:reinforced_echo")
        AffixOrigin.OTHERSIDE to 4 -> listOf("tconaffixes:warden_carapace")
        else -> emptyList()
    }

    private fun MutableMap<String, AffixOrigin>.assign(origin: AffixOrigin, vararg ids: String) {
        ids.forEach { put(it, origin) }
    }
}
