package com.bettercontent.tinkersconstructaffixes

import net.minecraft.ChatFormatting
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.Container
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.tags.TagKey
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.ModList
import net.minecraftforge.registries.ForgeRegistries
import slimeknights.tconstruct.library.materials.MaterialRegistry
import slimeknights.tconstruct.library.materials.definition.IMaterial
import slimeknights.tconstruct.library.materials.definition.MaterialId
import slimeknights.tconstruct.library.modifiers.ModifierId
import slimeknights.tconstruct.library.modifiers.ModifierManager
import slimeknights.tconstruct.library.tools.nbt.ToolStack
import slimeknights.tconstruct.library.tools.part.ToolPartItem
import kotlin.math.roundToInt

object TConAffixRewards {
    private const val TCON_MODID = "tconstruct"
    internal const val AFFIXES_TAG = "tinkers_construct_affixes_affixes"
    private const val TIC_MATERIALS_TAG = "tic_materials"
    private const val TIC_STATS_TAG = "tic_stats"

    private const val MAX_PREFIXES = 3
    private const val MAX_SUFFIXES = 3
    private val uniqueModifierTier = Tier(0, "unique", 0.0, 0.0, 1)

    internal enum class PartFamily {
        MELEE_HEAD,
        TOOL_HEAD,
        HANDLE,
        BINDING,
        BOW,
        RANGED,
        ARMOR,
        SHIELD
    }

    internal enum class AffixKind(val id: String) {
        PREFIX("prefix"),
        SUFFIX("suffix"),
        IMPLICIT("implicit")
    }

    internal data class PartProfile(
        val itemId: String,
        val family: PartFamily,
        val weight: Int = 100
    )

    internal data class Tier(
        val rank: Int,
        val name: String,
        val minPercent: Double,
        val maxPercent: Double,
        val weight: Int
    )

    internal data class StatLine(
        val stat: String,
        val scale: Double = 1.0
    )

    internal data class ModifierGrant(
        val id: String,
        val level: Int = 1
    )

    internal data class AffixDefinition(
        val id: String,
        val name: String,
        val kind: AffixKind,
        val group: String,
        val weight: Int,
        val families: Set<PartFamily>,
        val stats: List<StatLine>,
        val modifiers: List<ModifierGrant>,
        val tiers: List<Tier>
    ) {
        fun allows(family: PartFamily): Boolean = family in families
    }

    private val allFamilies = enumValues<PartFamily>().toSet()
    private val weaponFamilies = setOf(PartFamily.MELEE_HEAD, PartFamily.TOOL_HEAD, PartFamily.HANDLE, PartFamily.BINDING)
    private val headFamilies = setOf(PartFamily.MELEE_HEAD, PartFamily.TOOL_HEAD)
    private val weaponAndBowFamilies = weaponFamilies + PartFamily.BOW
    private val rangedFamilies = setOf(PartFamily.BOW, PartFamily.RANGED)
    private val armorFamilies = setOf(PartFamily.ARMOR, PartFamily.SHIELD)

    internal val partProfiles = listOf(
        PartProfile("tconstruct:pick_head", PartFamily.TOOL_HEAD, 120),
        PartProfile("tconstruct:small_blade", PartFamily.MELEE_HEAD, 120),
        PartProfile("tconstruct:hammer_head", PartFamily.TOOL_HEAD, 80),
        PartProfile("tconstruct:small_axe_head", PartFamily.TOOL_HEAD, 100),
        PartProfile("tconstruct:broad_axe_head", PartFamily.TOOL_HEAD, 70),
        PartProfile("tconstruct:broad_blade", PartFamily.MELEE_HEAD, 90),
        PartProfile("tconstruct:adze_head", PartFamily.TOOL_HEAD, 95),
        PartProfile("tconstruct:large_plate", PartFamily.ARMOR, 55),
        PartProfile("tconstruct:tool_binding", PartFamily.BINDING, 90),
        PartProfile("tconstruct:tough_binding", PartFamily.BINDING, 75),
        PartProfile("tconstruct:tool_handle", PartFamily.HANDLE, 120),
        PartProfile("tconstruct:tough_handle", PartFamily.HANDLE, 80),
        PartProfile("tconstruct:bow_limb", PartFamily.BOW, 85),
        PartProfile("tconstruct:bow_grip", PartFamily.BOW, 85),
        PartProfile("tconstruct:bowstring", PartFamily.BOW, 70),
        PartProfile("tconstruct:arrow_head", PartFamily.RANGED, 82),
        PartProfile("tconstruct:arrow_shaft", PartFamily.RANGED, 82),
        PartProfile("tconstruct:fletching", PartFamily.RANGED, 78),
        PartProfile("tconstruct:helmet_plating", PartFamily.ARMOR, 70),
        PartProfile("tconstruct:chestplate_plating", PartFamily.ARMOR, 70),
        PartProfile("tconstruct:leggings_plating", PartFamily.ARMOR, 70),
        PartProfile("tconstruct:boots_plating", PartFamily.ARMOR, 70),
        PartProfile("tconstruct:maille", PartFamily.ARMOR, 60),
        PartProfile("tconstruct:shield_core", PartFamily.SHIELD, 72)
    )

    internal val exclusivePartProfiles = listOf(
        PartProfile("tinkersweaponry:great_blade", PartFamily.MELEE_HEAD, 100),
        PartProfile("tinker_rapier:slender_blade", PartFamily.MELEE_HEAD, 100),
        PartProfile("additionalweaponry:defensive_handle", PartFamily.HANDLE, 100),
        PartProfile("tinkers_things:shield_plating", PartFamily.SHIELD, 100),
        PartProfile("tinkersweaponry:spear_head", PartFamily.MELEE_HEAD, 100)
    )

    internal val allPartProfiles: List<PartProfile> = partProfiles + exclusivePartProfiles

    private val commonTiers = listOf(
        Tier(1, "sovereign", 0.18, 0.24, 6),
        Tier(2, "exalted", 0.14, 0.18, 14),
        Tier(3, "potent", 0.10, 0.14, 32),
        Tier(4, "tempered", 0.06, 0.10, 58),
        Tier(5, "faint", 0.03, 0.06, 100)
    )

    private val defenseTiers = listOf(
        Tier(1, "sovereign", 0.16, 0.22, 6),
        Tier(2, "exalted", 0.12, 0.16, 14),
        Tier(3, "potent", 0.09, 0.12, 32),
        Tier(4, "tempered", 0.05, 0.09, 58),
        Tier(5, "faint", 0.03, 0.05, 100)
    )

    private val speedTiers = listOf(
        Tier(1, "sovereign", 0.12, 0.17, 7),
        Tier(2, "exalted", 0.09, 0.12, 16),
        Tier(3, "potent", 0.065, 0.09, 36),
        Tier(4, "tempered", 0.04, 0.065, 64),
        Tier(5, "faint", 0.02, 0.04, 110)
    )

    private val hybridTiers = listOf(
        Tier(1, "sovereign", 0.12, 0.17, 5),
        Tier(2, "exalted", 0.09, 0.12, 12),
        Tier(3, "potent", 0.065, 0.09, 28),
        Tier(4, "tempered", 0.04, 0.065, 56),
        Tier(5, "faint", 0.02, 0.04, 100)
    )

    internal val affixPool = listOf(
        affix("vital_temper", "Vital Temper", AffixKind.PREFIX, "damage", 115, weaponFamilies, commonTiers, "tconstruct:attack_damage"),
        affix("keened_edge", "Keened Edge", AffixKind.PREFIX, "speed", 95, weaponAndBowFamilies, speedTiers, "tconstruct:attack_speed"),
        affix("delvers_cut", "Delver's Cut", AffixKind.PREFIX, "mining", 125, setOf(PartFamily.TOOL_HEAD, PartFamily.BINDING), commonTiers, "tconstruct:mining_speed"),
        affix("patient_core", "Patient Core", AffixKind.PREFIX, "durability", 145, allFamilies, commonTiers, "tconstruct:durability"),
        affix("drawn_pulse", "Drawn Pulse", AffixKind.PREFIX, "draw", 110, setOf(PartFamily.BOW, PartFamily.HANDLE), speedTiers, "tconstruct:draw_speed"),
        affix("true_flight", "True Flight", AffixKind.PREFIX, "projectile", 105, rangedFamilies + PartFamily.HANDLE, commonTiers, "tconstruct:projectile_damage"),
        affix("laminated_guard", "Laminated Guard", AffixKind.PREFIX, "armor", 130, setOf(PartFamily.ARMOR, PartFamily.BINDING, PartFamily.SHIELD), defenseTiers, "tconstruct:armor"),
        affix("tempered_marrow", "Tempered Marrow", AffixKind.PREFIX, "toughness", 95, setOf(PartFamily.ARMOR, PartFamily.HANDLE, PartFamily.SHIELD), defenseTiers, "tconstruct:armor_toughness"),
        affix("rooted_balance", "Rooted Balance", AffixKind.PREFIX, "knockback", 75, setOf(PartFamily.ARMOR, PartFamily.HANDLE, PartFamily.BINDING, PartFamily.SHIELD), defenseTiers, "tconstruct:knockback_resistance"),
        affix(
            "fontbound_assault",
            "Fontbound Assault",
            AffixKind.PREFIX,
            "damage_speed",
            42,
            weaponFamilies,
            hybridTiers,
            StatLine("tconstruct:attack_damage", 1.0),
            StatLine("tconstruct:attack_speed", 0.55)
        ),
        affix(
            "quarrywrights_patience",
            "Quarrywright's Patience",
            AffixKind.PREFIX,
            "mining_durability",
            48,
            setOf(PartFamily.TOOL_HEAD, PartFamily.BINDING),
            hybridTiers,
            StatLine("tconstruct:mining_speed", 0.9),
            StatLine("tconstruct:durability", 0.75)
        ),
        affix(
            "bowyers_lattice",
            "Bowyer's Lattice",
            AffixKind.PREFIX,
            "bow_hybrid",
            52,
            rangedFamilies,
            hybridTiers,
            StatLine("tconstruct:projectile_damage", 1.0),
            StatLine("tconstruct:draw_speed", 0.65)
        ),
        affix(
            "bastion_plate",
            "Bastion Plate",
            AffixKind.PREFIX,
            "armor_hybrid",
            46,
            armorFamilies + PartFamily.ARMOR,
            hybridTiers,
            StatLine("tconstruct:armor", 1.0),
            StatLine("tconstruct:armor_toughness", 0.65)
        ),
        affix("of_the_red_line", "of the Red Line", AffixKind.SUFFIX, "damage_suffix", 90, weaponFamilies, commonTiers, "tconstruct:attack_damage"),
        affix("of_quick_hands", "of Quick Hands", AffixKind.SUFFIX, "speed_suffix", 95, weaponAndBowFamilies, speedTiers, "tconstruct:attack_speed"),
        affix("of_deep_cuts", "of Deep Cuts", AffixKind.SUFFIX, "mining_suffix", 100, headFamilies, commonTiers, "tconstruct:mining_speed"),
        affix("of_long_service", "of Long Service", AffixKind.SUFFIX, "durability_suffix", 135, allFamilies, commonTiers, "tconstruct:durability"),
        affix("of_the_still_breath", "of the Still Breath", AffixKind.SUFFIX, "draw_suffix", 90, setOf(PartFamily.BOW), speedTiers, "tconstruct:draw_speed"),
        affix("of_sure_arcs", "of Sure Arcs", AffixKind.SUFFIX, "projectile_suffix", 95, rangedFamilies, commonTiers, "tconstruct:projectile_damage"),
        affix("of_interlocked_rings", "of Interlocked Rings", AffixKind.SUFFIX, "armor_suffix", 110, armorFamilies + PartFamily.ARMOR, defenseTiers, "tconstruct:armor"),
        affix("of_the_anchor", "of the Anchor", AffixKind.SUFFIX, "knockback_suffix", 85, setOf(PartFamily.ARMOR, PartFamily.HANDLE, PartFamily.SHIELD), defenseTiers, "tconstruct:knockback_resistance"),
        affix(
            "of_blood_and_breath",
            "of Blood and Breath",
            AffixKind.SUFFIX,
            "damage_durability_suffix",
            34,
            weaponFamilies,
            hybridTiers,
            StatLine("tconstruct:attack_damage", 0.8),
            StatLine("tconstruct:durability", 0.9)
        ),
        affix(
            "of_clean_geometry",
            "of Clean Geometry",
            AffixKind.SUFFIX,
            "mining_speed_suffix",
            38,
            setOf(PartFamily.TOOL_HEAD, PartFamily.BINDING),
            hybridTiers,
            StatLine("tconstruct:mining_speed", 0.85),
            StatLine("tconstruct:attack_speed", 0.45)
        ),
        affix(
            "of_bound_plates",
            "of Bound Plates",
            AffixKind.SUFFIX,
            "armor_knockback_suffix",
            40,
            setOf(PartFamily.ARMOR, PartFamily.SHIELD),
            hybridTiers,
            StatLine("tconstruct:armor", 0.75),
            StatLine("tconstruct:knockback_resistance", 0.9)
        ),
        affix(
            "of_the_taut_string",
            "of the Taut String",
            AffixKind.SUFFIX,
            "bow_speed_suffix",
            42,
            rangedFamilies,
            hybridTiers,
            StatLine("tconstruct:draw_speed", 0.8),
            StatLine("tconstruct:attack_speed", 0.55)
        ),
        affix("gravehook", "Gravehook", AffixKind.PREFIX, "magnetic", 58, setOf(PartFamily.TOOL_HEAD, PartFamily.HANDLE, PartFamily.BINDING), hybridTiers, modifier("tconstruct:magnetic")),
        affix("charward", "Charward", AffixKind.PREFIX, "autosmelt", 38, setOf(PartFamily.TOOL_HEAD), hybridTiers, modifier("tconstruct:autosmelt")),
        affix("waycleaver", "Waycleaver", AffixKind.PREFIX, "exchanging", 34, setOf(PartFamily.TOOL_HEAD, PartFamily.BINDING), hybridTiers, modifier("tconstruct:exchanging")),
        affix("sundercall", "Sundercall", AffixKind.PREFIX, "severing", 46, setOf(PartFamily.MELEE_HEAD), hybridTiers, modifier("tconstruct:severing")),
        affix("red_lantern", "Red Lantern", AffixKind.PREFIX, "sweeping", 42, setOf(PartFamily.MELEE_HEAD), hybridTiers, modifier("tconstruct:sweeping_edge")),
        affix("gorepoint", "Gorepoint", AffixKind.PREFIX, "piercing", 45, setOf(PartFamily.MELEE_HEAD, PartFamily.RANGED), hybridTiers, modifier("tconstruct:pierce")),
        affix("emberspite", "Emberspite", AffixKind.PREFIX, "fiery", 48, setOf(PartFamily.MELEE_HEAD, PartFamily.RANGED), hybridTiers, modifier("tconstruct:fiery")),
        affix("frostwrit", "Frostwrit", AffixKind.PREFIX, "freezing", 43, rangedFamilies, hybridTiers, modifier("tconstruct:freezing")),
        affix("graveglass", "Graveglass", AffixKind.PREFIX, "scope", 37, rangedFamilies, hybridTiers, modifier("tconstruct:scope")),
        affix("left_hand_lesson", "Left-Hand Lesson", AffixKind.PREFIX, "sinistral", 35, rangedFamilies, hybridTiers, modifier("tconstruct:sinistral")),
        affix("quarry_echo", "Quarry Echo", AffixKind.PREFIX, "momentum", 50, setOf(PartFamily.TOOL_HEAD, PartFamily.HANDLE), hybridTiers, modifier("tconstruct:momentum")),
        affix("deep_choir", "Deep Choir", AffixKind.PREFIX, "dwarven", 36, setOf(PartFamily.TOOL_HEAD), hybridTiers, modifier("tconstruct:dwarven")),
        affix("skywake", "Skywake", AffixKind.PREFIX, "double_jump", 30, setOf(PartFamily.ARMOR), hybridTiers, modifier("tconstruct:double_jump")),
        affix("gravebound", "Gravebound", AffixKind.PREFIX, "bouncy", 28, setOf(PartFamily.ARMOR), hybridTiers, modifier("tconstruct:bouncy")),
        affix("heelspur", "Heelspur", AffixKind.PREFIX, "soulspeed", 32, setOf(PartFamily.ARMOR), hybridTiers, modifier("tconstruct:soulspeed")),
        affix("mirror_ward", "Mirror Ward", AffixKind.PREFIX, "reflecting", 27, armorFamilies, hybridTiers, modifier("tconstruct:reflecting")),
        affix("bulwark_latch", "Bulwark Latch", AffixKind.PREFIX, "shield_strap", 40, setOf(PartFamily.SHIELD), hybridTiers, modifier("tconstruct:shield_strap")),
        affix("duelers_reflex", "Dueler's Reflex", AffixKind.PREFIX, "offhand", 33, setOf(PartFamily.HANDLE, PartFamily.SHIELD), hybridTiers, modifier("tconstruct:offhanded")),
        affix("of_the_bone_rack", "of the Bone Rack", AffixKind.SUFFIX, "tool_magnet_suffix", 54, setOf(PartFamily.TOOL_HEAD, PartFamily.HANDLE), hybridTiers, modifier("tconstruct:magnetic")),
        affix("of_live_coals", "of Live Coals", AffixKind.SUFFIX, "tool_fire_suffix", 41, setOf(PartFamily.MELEE_HEAD, PartFamily.RANGED), hybridTiers, modifier("tconstruct:fiery")),
        affix("of_the_white_rime", "of the White Rime", AffixKind.SUFFIX, "ranged_ice_suffix", 39, rangedFamilies, hybridTiers, modifier("tconstruct:freezing")),
        affix("of_split_routes", "of Split Routes", AffixKind.SUFFIX, "offhand_suffix", 31, setOf(PartFamily.HANDLE, PartFamily.BINDING), hybridTiers, modifier("tconstruct:offhanded")),
        affix("of_trophy_cables", "of Trophy Cables", AffixKind.SUFFIX, "ranged_scope_suffix", 34, rangedFamilies, hybridTiers, modifier("tconstruct:scope")),
        affix("of_the_ash_path", "of the Ash Path", AffixKind.SUFFIX, "tool_route_suffix", 29, setOf(PartFamily.TOOL_HEAD, PartFamily.BINDING), hybridTiers, modifier("tconstruct:exchanging")),
        affix("of_held_breath", "of Held Breath", AffixKind.SUFFIX, "armor_air_suffix", 26, setOf(PartFamily.ARMOR), hybridTiers, modifier("tconstruct:double_jump")),
        affix("of_the_vaulted_shoulder", "of the Vaulted Shoulder", AffixKind.SUFFIX, "armor_guard_suffix", 25, armorFamilies, hybridTiers, modifier("tconstruct:reflecting"))
    )

    private val statDisplayNames = mapOf(
        "tconstruct:durability" to "Durability",
        "tconstruct:attack_damage" to "Attack Damage",
        "tconstruct:attack_speed" to "Attack Speed",
        "tconstruct:mining_speed" to "Mining Speed",
        "tconstruct:projectile_damage" to "Projectile Damage",
        "tconstruct:draw_speed" to "Draw Speed",
        "tconstruct:armor" to "Armor",
        "tconstruct:armor_toughness" to "Armor Toughness",
        "tconstruct:knockback_resistance" to "Knockback Resistance"
    )

    private val modifierDisplayNames = mapOf(
        "tconstruct:magnetic" to "Magnetic",
        "tconstruct:autosmelt" to "Autosmelt",
        "tconstruct:exchanging" to "Exchanging",
        "tconstruct:severing" to "Severing",
        "tconstruct:sweeping_edge" to "Sweeping Edge",
        "tconstruct:pierce" to "Piercing",
        "tconstruct:fiery" to "Fiery",
        "tconstruct:freezing" to "Freezing",
        "tconstruct:scope" to "Scope",
        "tconstruct:sinistral" to "Sinistral",
        "tconstruct:momentum" to "Momentum",
        "tconstruct:dwarven" to "Dwarven",
        "tconstruct:double_jump" to "Double Jump",
        "tconstruct:bouncy" to "Bouncy",
        "tconstruct:soulspeed" to "Soul Speed",
        "tconstruct:reflecting" to "Reflecting",
        "tconstruct:shield_strap" to "Shield Strap",
        "tconstruct:offhanded" to "Offhanded"
    )

    internal fun rollAffixedPart(
        random: RandomSource,
        origin: AffixOrigin = AffixOrigin.GLOBAL,
        provenance: String = "unknown"
    ): ItemStack? {
        if (!ModList.get().isLoaded(TCON_MODID)) return null
        val exclusiveRoll = origin != AffixOrigin.GLOBAL && random.nextFloat() < 0.35f
        var candidates = allPartProfiles.filter { AffixOrigins.allowsPart(origin, it.itemId, exclusiveRoll) }.mapNotNull { profile ->
            val item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(profile.itemId) ?: return@mapNotNull null)
                ?.takeUnless { it.defaultInstance.isEmpty } as? ToolPartItem
                ?: return@mapNotNull null
            profile to item
        }
        if (candidates.isEmpty() && exclusiveRoll) {
            candidates = allPartProfiles.filter { AffixOrigins.allowsPart(origin, it.itemId, false) }.mapNotNull { profile ->
                val item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(profile.itemId) ?: return@mapNotNull null) as? ToolPartItem
                    ?: return@mapNotNull null
                profile to item
            }
        }
        if (candidates.isEmpty()) return null

        val (profile, item) = weightedPick(candidates, random) { it.first.weight } ?: return null
        val material = rollMaterial(item, random, origin) ?: return null
        val stack = item.withMaterial(material.identifier)
        if (item.getMaterial(stack) == IMaterial.UNKNOWN_ID) return null
        val sourcePart = ForgeRegistries.ITEMS.getKey(stack.item)?.toString() ?: profile.itemId
        val affixes = rollAffixes(
            sourcePart, profile.family, random, origin,
            targetCount = if (origin == AffixOrigin.GLOBAL) null else fontAffixCount(random.nextFloat()),
            lucky = origin != AffixOrigin.GLOBAL,
            guaranteeOriginAffix = origin != AffixOrigin.GLOBAL
        )
        writeToolAffixes(stack, affixes)
        AffixCrafting.stampNatural(stack, origin, provenance, random)
        return stack
    }

    @SubscribeEvent
    fun onItemCrafted(event: PlayerEvent.ItemCraftedEvent) {
        if (!ModList.get().isLoaded(TCON_MODID)) return
        val result = event.crafting
        if (!looksLikeTConTool(result)) return
        transferAffixes(result, collectInputStacks(event.inventory))
    }

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        val affixes = existingToolAffixes(event.itemStack)
        if (affixes.isEmpty()) return
        event.toolTip += Component.translatable("tooltip.tinkers_construct_affixes.affixes").withStyle(ChatFormatting.DARK_RED)
        affixes.forEach { affix ->
            event.toolTip += Component.literal(formatAffixLine(affix)).withStyle(
                when (affix.getString("kind")) {
                    AffixKind.PREFIX.id -> ChatFormatting.RED
                    AffixKind.IMPLICIT.id -> ChatFormatting.GOLD
                    else -> ChatFormatting.LIGHT_PURPLE
                }
            )
        }
        val tag = event.itemStack.tag ?: return
        tag.getString(AffixCrafting.PROVENANCE_TAG).takeIf(String::isNotBlank)?.let { provenance ->
            val origin = tag.getString(AffixCrafting.ORIGIN_TAG).ifBlank { AffixOrigin.GLOBAL.id }
            event.toolTip += Component.literal("Found in $origin · $provenance").withStyle(ChatFormatting.DARK_GRAY)
        }
        if (tag.getBoolean(AffixCrafting.MUTATION_LOCKED_TAG)) {
            event.toolTip += Component.literal("Mutation locked").withStyle(ChatFormatting.DARK_RED)
        } else if (tag.getBoolean(AffixCrafting.SALVAGE_SPENT_TAG)) {
            event.toolTip += Component.literal("Salvage spent").withStyle(ChatFormatting.DARK_GRAY)
        } else if (!tag.getBoolean(AffixCrafting.NATURAL_TAG)) {
            event.toolTip += Component.literal("Forged").withStyle(ChatFormatting.GRAY)
        }
        event.toolTip += Component.translatable("tooltip.tinkers_construct_affixes.salvage_hint").withStyle(ChatFormatting.DARK_GRAY)
    }

    internal fun rollAffixes(
        sourcePart: String,
        family: PartFamily,
        random: RandomSource,
        origin: AffixOrigin = AffixOrigin.GLOBAL,
        targetCount: Int? = null,
        lucky: Boolean = false,
        guaranteeOriginAffix: Boolean = false
    ): List<CompoundTag> {
        val desiredCount = targetCount?.coerceIn(1, 6) ?: affixCountForRoll(random.nextFloat())
        val chosen = mutableListOf<AffixDefinition>()
        var prefixCount = 0
        var suffixCount = 0

        if (guaranteeOriginAffix) {
            val exclusive = affixPool.filter { it.allows(family) && AffixOrigins.isExclusiveAffix(origin, it.id) }
            weightedPick(exclusive, random) { it.weight }?.let { definition ->
                chosen += definition
                when (definition.kind) {
                    AffixKind.PREFIX -> prefixCount++
                    AffixKind.SUFFIX -> suffixCount++
                    AffixKind.IMPLICIT -> Unit
                }
            }
        }

        repeat(desiredCount * 10) {
            if (chosen.size >= desiredCount) return@repeat
            val usedGroups = chosen.map { it.group }.toSet()
            val candidates = affixPool.filter { definition ->
                definition.allows(family) &&
                    AffixOrigins.allowsAffix(origin, definition.id) &&
                    definition.group !in usedGroups &&
                    when (definition.kind) {
                        AffixKind.PREFIX -> prefixCount < MAX_PREFIXES
                        AffixKind.SUFFIX -> suffixCount < MAX_SUFFIXES
                        AffixKind.IMPLICIT -> false
                    }
            }
            val definition = weightedPick(candidates, random) { it.weight } ?: return@repeat
            chosen += definition
            when (definition.kind) {
                AffixKind.PREFIX -> prefixCount++
                AffixKind.SUFFIX -> suffixCount++
                AffixKind.IMPLICIT -> Unit
            }
        }

        return chosen.map { rollDefinition(it, sourcePart, random, lucky) }
    }

    internal fun rollAdditionalAffix(
        sourcePart: String,
        family: PartFamily,
        existing: List<CompoundTag>,
        random: RandomSource,
        origin: AffixOrigin
    ): CompoundTag? {
        val prefixCount = existing.count { it.getString("kind") == AffixKind.PREFIX.id }
        val suffixCount = existing.count { it.getString("kind") == AffixKind.SUFFIX.id }
        val groups = existing.map { it.getString("group") }.filter(String::isNotBlank).toSet()
        val definitions = affixPool.filter { definition ->
            definition.allows(family) && AffixOrigins.allowsAffix(origin, definition.id) && definition.group !in groups &&
                when (definition.kind) {
                    AffixKind.PREFIX -> prefixCount < MAX_PREFIXES
                    AffixKind.SUFFIX -> suffixCount < MAX_SUFFIXES
                    AffixKind.IMPLICIT -> false
                }
        }
        return weightedPick(definitions, random) { it.weight }?.let { rollDefinition(it, sourcePart, random, false) }
    }

    internal fun rollAffixSide(
        sourcePart: String,
        family: PartFamily,
        kind: AffixKind,
        count: Int,
        preserved: List<CompoundTag>,
        random: RandomSource,
        origin: AffixOrigin
    ): List<CompoundTag> {
        val chosen = mutableListOf<AffixDefinition>()
        val usedGroups = preserved.map { it.getString("group") }.filter(String::isNotBlank).toMutableSet()
        repeat(count.coerceIn(1, 3) * 10) {
            if (chosen.size >= count.coerceIn(1, 3)) return@repeat
            val candidates = affixPool.filter { definition ->
                definition.kind == kind && definition.allows(family) && AffixOrigins.allowsAffix(origin, definition.id) && definition.group !in usedGroups
            }
            val picked = weightedPick(candidates, random) { it.weight } ?: return@repeat
            chosen += picked
            usedGroups += picked.group
        }
        return chosen.map { rollDefinition(it, sourcePart, random, false) }
    }

    internal fun rollConfiguredTier(random: RandomSource, weights: List<Int> = TConAffixConfig.tierWeights()): Int? {
        return weightedPick(weights.mapIndexed { index, weight -> (index + 1) to weight }, random) { it.second }?.first
    }

    internal fun configuredMaterialIds(tier: Int): List<MaterialId> {
        return TConAffixConfig.materialsForTier(tier).mapNotNull(MaterialId::tryParse)
    }

    private fun rollMaterial(part: ToolPartItem, random: RandomSource, origin: AffixOrigin): IMaterial? {
        if (!MaterialRegistry.isFullyLoaded()) return null
        val registry = MaterialRegistry.getInstance()
        val candidatesByTier = (1..4).associateWith { tier ->
            AffixOrigins.materialIds(origin, tier).mapNotNull(MaterialId::tryParse).mapNotNull { id ->
                registry.getMaterial(id).takeUnless { material ->
                    material == IMaterial.UNKNOWN || material.isHidden || !part.canUseMaterial(material)
                }
            }
        }
        val selectedTier = rollConfiguredTier(random, if (origin == AffixOrigin.GLOBAL) TConAffixConfig.tierWeights() else listOf(4500, 3500, 1700, 300)) ?: return null
        for (tier in selectedTier downTo 1) {
            val candidates = candidatesByTier[tier].orEmpty()
            if (candidates.isNotEmpty()) return candidates[random.nextInt(candidates.size)]
        }
        return null
    }

    internal fun affixCountForRoll(roll: Float): Int {
        return when {
            roll < 0.03f -> 6
            roll < 0.10f -> 5
            roll < 0.25f -> 4
            roll < 0.50f -> 3
            roll < 0.78f -> 2
            else -> 1
        }
    }

    internal fun fontAffixCount(roll: Float): Int = when {
        roll < 0.05f -> 6
        roll < 0.15f -> 5
        roll < 0.35f -> 4
        roll < 0.65f -> 3
        else -> 2
    }

    internal fun createAffix(stat: String, percent: Double, sourcePart: String): CompoundTag {
        return CompoundTag().apply {
            putString("id", "legacy:$stat")
            putString("name", statDisplayName(stat))
            putString("kind", AffixKind.PREFIX.id)
            putString("tier_name", "legacy")
            putInt("tier", 0)
            putString("source_part", sourcePart)
            putString("stat", stat)
            putDouble("percent", percent)
            put("rolls", rollList(listOf(stat to percent)))
        }
    }

    internal fun createAffix(definition: AffixDefinition, tier: Tier, sourcePart: String, random: RandomSource): CompoundTag {
        val rolls = definition.stats.map { line ->
            val percent = randomPercent(random, tier.minPercent, tier.maxPercent) * line.scale
            line.stat to percent
        }
        return CompoundTag().apply {
            putString("id", definition.id)
            putString("name", definition.name)
            putString("kind", definition.kind.id)
            putString("group", definition.group)
            putString("tier_name", tier.name)
            putInt("tier", tier.rank)
            putString("source_part", sourcePart)
            if (rolls.size == 1) {
                putString("stat", rolls.single().first)
                putDouble("percent", rolls.single().second)
            }
            put("rolls", rollList(rolls))
            if (definition.modifiers.isNotEmpty()) {
                put("modifier_grants", modifierList(definition.modifiers))
            }
        }
    }

    internal fun transferAffixes(result: ItemStack, inputs: Iterable<ItemStack>): Boolean {
        val existingAffixes = existingToolAffixes(result)
        val rawInputAffixes = inputs
            .filterNot(::looksLikeTConTool)
            .flatMap(::existingToolAffixes)
        if (rawInputAffixes.isEmpty()) return false
        val replacingParts = rawInputAffixes
            .mapNotNull { it.getString("source_part").takeIf(String::isNotBlank) }
            .toSet()
        val inputAffixes = rawInputAffixes.mapNotNull { filterAffixForTool(it, result) }

        val merged = mergeAffixes(existingAffixes, inputAffixes, replacingParts)
        writeToolAffixes(result, merged)
        applyAffixEffects(result, merged)
        return true
    }

    internal fun filterAffixForTool(affix: CompoundTag, result: ItemStack): CompoundTag? {
        val stats = result.tag?.getCompound(TIC_STATS_TAG) ?: return null
        val filteredRolls = rolls(affix).filter { (stat, _) -> stats.contains(stat, Tag.TAG_ANY_NUMERIC.toInt()) }
        val filteredModifiers = grantedModifiers(affix).filter { grant ->
            val id = ModifierId.tryParse(grant.id)
            id != null && ModifierManager.INSTANCE.contains(id) && modifierApplicable(grant.id, result)
        }
        if (filteredRolls.isEmpty() && filteredModifiers.isEmpty()) return null
        return affix.copy().apply {
            put("rolls", rollList(filteredRolls))
            if (filteredRolls.size == 1) {
                putString("stat", filteredRolls.single().first)
                putDouble("percent", filteredRolls.single().second)
            } else {
                remove("stat")
                remove("percent")
            }
            if (filteredModifiers.isEmpty()) remove("modifier_grants")
            else put("modifier_grants", modifierList(filteredModifiers))
        }
    }

    internal fun modifierApplicable(id: String, stack: ItemStack): Boolean {
        if (id == "tconstruct:sinistral") {
            return stack.`is`(itemTag("modifiable/ranged/crossbows")) && stack.`is`(itemTag("modifiable/interactable/left"))
        }
        val anyTags = when (id) {
            "tconstruct:magnetic" -> listOf("modifiable/melee/weapon", "modifiable/harvest", "modifiable/armor/worn")
            "tconstruct:autosmelt" -> listOf("modifiable/harvest", "modifiable/fishing_rods")
            "tconstruct:exchanging", "tconstruct:momentum", "tconstruct:dwarven" -> listOf("modifiable/harvest")
            "tconstruct:severing", "tconstruct:pierce" -> listOf("modifiable/melee", "modifiable/ranged/launcher")
            "tconstruct:sweeping_edge" -> listOf("modifiable/melee/sword")
            "tconstruct:fiery", "tconstruct:freezing" -> listOf(
                "modifiable/melee", "modifiable/ranged/bows", "modifiable/fishing_rods", "modifiable/armor/worn", "modifiable/shields"
            )
            "tconstruct:scope" -> listOf("modifiable/interactable/charge")
            "tconstruct:double_jump", "tconstruct:bouncy", "tconstruct:soulspeed" -> listOf("modifiable/armor/boots")
            "tconstruct:reflecting" -> listOf("modifiable/shields")
            "tconstruct:shield_strap" -> listOf("modifiable/armor/leggings")
            "tconstruct:offhanded" -> listOf("modifiable/interactable/charge/modifier")
            else -> return false
        }
        return anyTags.any { stack.`is`(itemTag(it)) }
    }

    private fun itemTag(path: String): TagKey<Item> = TagKey.create(Registries.ITEM, ResourceLocation("tconstruct", path))

    internal fun mergeAffixes(
        existing: List<CompoundTag>,
        input: List<CompoundTag>,
        replacingParts: Set<String> = input.mapNotNull { it.getString("source_part").takeIf(String::isNotBlank) }.toSet()
    ): List<CompoundTag> {
        return buildList {
            existing
                .filterNot { it.getString("source_part") in replacingParts }
                .forEach { add(it.copy()) }
            input.forEach { add(it.copy()) }
        }
    }

    internal fun multiplierTag(affixes: List<CompoundTag>): CompoundTag {
        return CompoundTag().apply {
            affixes
                .flatMap(::rolls)
                .groupBy { it.first }
                .forEach { (stat, statRolls) ->
                    val multiplier = statRolls.fold(1.0) { acc, (_, percent) -> acc * (1.0 + percent) }
                    putFloat(stat, multiplier.toFloat())
                }
        }
    }

    private fun collectInputStacks(inventory: Container): List<ItemStack> {
        val stacks = mutableListOf<ItemStack>()
        for (slot in 0 until inventory.containerSize) {
            stacks += inventory.getItem(slot)
        }
        return stacks
    }

    internal fun existingToolAffixes(stack: ItemStack): List<CompoundTag> {
        val tag = stack.tag ?: return emptyList()
        if (!tag.contains(AFFIXES_TAG, Tag.TAG_LIST.toInt())) return emptyList()
        val list = tag.getList(AFFIXES_TAG, Tag.TAG_COMPOUND.toInt())
        return buildList {
            for (index in 0 until list.size) {
                add(list.getCompound(index).copy())
            }
        }
    }

    internal fun writeToolAffixes(stack: ItemStack, affixes: List<CompoundTag>) {
        val list = ListTag()
        affixes.forEach { list.add(it.copy()) }
        stack.orCreateTag.put(AFFIXES_TAG, list)
    }

    internal fun grantedModifiers(affix: CompoundTag): List<ModifierGrant> {
        if (!affix.contains("modifier_grants", Tag.TAG_LIST.toInt())) return emptyList()
        val list = affix.getList("modifier_grants", Tag.TAG_COMPOUND.toInt())
        return buildList {
            for (index in 0 until list.size) {
                val entry = list.getCompound(index)
                val id = entry.getString("id")
                val level = entry.getInt("level")
                if (id.isNotBlank() && level > 0) {
                    add(ModifierGrant(id, level))
                }
            }
        }
    }

    internal fun aggregateGrantedModifierLevels(affixes: List<CompoundTag>): Map<String, Int> {
        return buildMap {
            affixes.flatMap(::grantedModifiers).forEach { grant ->
                put(grant.id, (get(grant.id) ?: 0) + grant.level)
            }
        }
    }

    internal fun applyAffixEffects(stack: ItemStack, nextAffixes: List<CompoundTag>) {
        if (!looksLikeTConTool(stack)) return
        val next = aggregateGrantedModifierLevels(nextAffixes).filterKeys { id ->
            val modifierId = ModifierId.tryParse(id)
            modifierId != null && modifierId != AffixModifiers.STAT_DRIVER.id && ModifierManager.INSTANCE.contains(modifierId)
        }
        val tool = ToolStack.from(stack)
        val previous = ownedModifierLevels(tool)
        val multipliers = multiplierTag(nextAffixes)
        if (multipliers.isEmpty) tool.persistentData.remove(AffixModifiers.MULTIPLIERS_KEY)
        else tool.persistentData.put(AffixModifiers.MULTIPLIERS_KEY, multipliers)
        writeOwnedModifierLevels(tool, next)

        (previous.keys + next.keys).forEach { id ->
            val modifierId = ModifierId.tryParse(id) ?: return@forEach
            if (!ModifierManager.INSTANCE.contains(modifierId)) return@forEach
            val currentLevel = tool.getUpgrades().getLevel(modifierId)
            val delta = ownedLevelDelta(currentLevel, previous[id] ?: 0, next[id] ?: 0)
            when {
                delta > 0 -> tool.addModifier(modifierId, delta)
                delta < 0 && currentLevel > 0 -> tool.removeModifier(modifierId, minOf(-delta, currentLevel))
            }
        }

        val statDriver = AffixModifiers.STAT_DRIVER.id
        val driverLevel = tool.getUpgrades().getLevel(statDriver)
        when {
            !multipliers.isEmpty && driverLevel == 0 -> tool.addModifier(statDriver, 1)
            multipliers.isEmpty && driverLevel > 0 -> tool.removeModifier(statDriver, driverLevel)
            else -> tool.rebuildStats()
        }
    }

    internal fun ownedLevelDelta(currentLevel: Int, storedOwnedLevel: Int, desiredOwnedLevel: Int): Int {
        val effectiveOwned = minOf(storedOwnedLevel.coerceAtLeast(0), currentLevel.coerceAtLeast(0))
        val baseLevel = currentLevel.coerceAtLeast(0) - effectiveOwned
        val targetLevel = baseLevel + desiredOwnedLevel.coerceAtLeast(0)
        return targetLevel - currentLevel.coerceAtLeast(0)
    }

    internal fun ownedModifierLevels(tool: ToolStack): Map<String, Int> {
        val tag = tool.persistentData.getCompound(AffixModifiers.OWNED_MODIFIERS_KEY)
        return tag.allKeys.mapNotNull { id -> tag.getInt(id).takeIf { it > 0 }?.let { id to it } }.toMap()
    }

    internal fun writeOwnedModifierLevels(tool: ToolStack, levels: Map<String, Int>) {
        if (levels.isEmpty()) {
            tool.persistentData.remove(AffixModifiers.OWNED_MODIFIERS_KEY)
            return
        }
        tool.persistentData.put(AffixModifiers.OWNED_MODIFIERS_KEY, CompoundTag().apply {
            levels.forEach { (id, level) -> if (level > 0) putInt(id, level) }
        })
    }

    internal fun rolls(affix: CompoundTag): List<Pair<String, Double>> {
        if (affix.contains("rolls", Tag.TAG_LIST.toInt())) {
            val list = affix.getList("rolls", Tag.TAG_COMPOUND.toInt())
            return buildList {
                for (index in 0 until list.size) {
                    val roll = list.getCompound(index)
                    add(roll.getString("stat") to roll.getDouble("percent"))
                }
            }
        }
        val stat = affix.getString("stat")
        if (stat.isBlank()) return emptyList()
        return listOf(stat to affix.getDouble("percent"))
    }

    internal fun definition(id: String): AffixDefinition? = affixPool.firstOrNull { it.id == id }

    internal fun partProfile(stack: ItemStack): PartProfile? {
        val id = ForgeRegistries.ITEMS.getKey(stack.item)?.toString() ?: return null
        return allPartProfiles.firstOrNull { it.itemId == id }
    }

    internal fun looksLikeTConTool(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val tag = stack.tag ?: return false
        return tag.contains(TIC_MATERIALS_TAG, Tag.TAG_LIST.toInt()) || tag.contains(TIC_STATS_TAG, Tag.TAG_COMPOUND.toInt())
    }

    private fun affix(
        id: String,
        name: String,
        kind: AffixKind,
        group: String,
        weight: Int,
        families: Set<PartFamily>,
        tiers: List<Tier>,
        stat: String
    ): AffixDefinition {
        return affix(id, name, kind, group, weight, families, tiers, StatLine(stat))
    }

    private fun affix(
        id: String,
        name: String,
        kind: AffixKind,
        group: String,
        weight: Int,
        families: Set<PartFamily>,
        tiers: List<Tier>,
        vararg stats: StatLine
    ): AffixDefinition {
        return affix(id, name, kind, group, weight, families, tiers, stats = stats.toList(), modifiers = emptyList())
    }

    private fun affix(
        id: String,
        name: String,
        kind: AffixKind,
        group: String,
        weight: Int,
        families: Set<PartFamily>,
        tiers: List<Tier>,
        vararg modifiers: ModifierGrant
    ): AffixDefinition {
        return affix(id, name, kind, group, weight, families, tiers, stats = emptyList(), modifiers = modifiers.toList())
    }

    private fun affix(
        id: String,
        name: String,
        kind: AffixKind,
        group: String,
        weight: Int,
        families: Set<PartFamily>,
        tiers: List<Tier>,
        stats: List<StatLine>,
        modifiers: List<ModifierGrant>
    ): AffixDefinition {
        require(weight > 0) { "Affix $id must have positive weight" }
        require(stats.isNotEmpty() || modifiers.isNotEmpty()) { "Affix $id must modify stats or grant modifiers" }
        require(tiers.isNotEmpty()) { "Affix $id must have tiers" }
        return AffixDefinition(id, name, kind, group, weight, families, stats, modifiers, tiers)
    }

    private fun modifier(id: String, level: Int = 1): ModifierGrant {
        require(level > 0) { "Modifier $id must have positive level" }
        return ModifierGrant(id, level)
    }

    internal fun rollList(rolls: List<Pair<String, Double>>): ListTag {
        return ListTag().apply {
            rolls.forEach { (stat, percent) ->
                add(CompoundTag().apply {
                    putString("stat", stat)
                    putDouble("percent", percent)
                })
            }
        }
    }

    private fun rollDefinition(definition: AffixDefinition, sourcePart: String, random: RandomSource, lucky: Boolean): CompoundTag {
        val tier = if (definition.stats.isEmpty()) uniqueModifierTier else {
            val tiers = if (lucky) definition.tiers.filter { it.rank <= 3 } else definition.tiers
            weightedPick(tiers, random) { tier -> if (lucky) (4 - tier.rank).coerceAtLeast(1) else tier.weight }
                ?: tiers.last()
        }
        return createAffix(definition, tier, sourcePart, random)
    }

    private fun modifierList(modifiers: List<ModifierGrant>): ListTag {
        return ListTag().apply {
            modifiers.forEach { grant ->
                add(CompoundTag().apply {
                    putString("id", grant.id)
                    putInt("level", grant.level)
                })
            }
        }
    }

    private fun rollsDisplay(affix: CompoundTag): String {
        val stats = rolls(affix).map { (stat, percent) ->
            "+${(percent * 100.0).roundToInt()}% ${statDisplayName(stat)}"
        }
        val modifiers = grantedModifiers(affix).map { grant ->
            "${modifierDisplayName(grant.id)} ${romanNumeral(grant.level)}"
        }
        return (stats + modifiers).joinToString(", ")
    }

    private fun formatAffixLine(affix: CompoundTag): String {
        val tier = affix.getInt("tier").takeIf { it > 0 }?.let { "T$it " } ?: ""
        val name = affix.getString("name").ifBlank { "Font Affix" }
        return "$tier$name: ${rollsDisplay(affix)}"
    }

    private fun statDisplayName(stat: String): String {
        return statDisplayNames[stat]
            ?: stat.substringAfter(':')
                .replace('_', ' ')
                .split(' ')
                .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
    }

    private fun modifierDisplayName(id: String): String {
        return modifierDisplayNames[id]
            ?: id.substringAfter(':')
                .replace('_', ' ')
                .split(' ')
                .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
    }

    private fun romanNumeral(value: Int): String {
        return when (value.coerceAtLeast(1)) {
            1 -> "I"
            2 -> "II"
            3 -> "III"
            4 -> "IV"
            5 -> "V"
            else -> value.toString()
        }
    }

    private fun randomPercent(random: RandomSource, min: Double, max: Double): Double {
        return min + (random.nextDouble() * (max - min))
    }

    private fun <T> weightedPick(values: List<T>, random: RandomSource, weight: (T) -> Int): T? {
        val totalWeight = values.sumOf { weight(it).coerceAtLeast(0) }
        if (totalWeight <= 0) return null
        var cursor = random.nextInt(totalWeight)
        values.forEach { value ->
            cursor -= weight(value).coerceAtLeast(0)
            if (cursor < 0) return value
        }
        return values.lastOrNull()
    }
}
