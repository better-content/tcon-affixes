package com.bettercontent.tinkersconstructaffixes

import io.netty.buffer.Unpooled
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TConAffixRewardsTest {
    @Test
    fun compatibleMaterialRollRenormalizesAcrossNonEmptyTiers() {
        val candidates = mapOf(
            1 to emptyList(),
            2 to listOf("iron"),
            3 to emptyList(),
            4 to listOf("manyullyn")
        )

        repeat(100) { seed ->
            val selected = TConAffixRewards.rollCompatibleMaterial(
                candidates,
                listOf(8000, 1700, 290, 0),
                RandomSource.create(seed.toLong())
            )
            assertEquals("iron", selected)
        }
    }

    @Test
    fun compatibleMaterialRollFailsClosedForEmptyOrZeroWeightedPools() {
        assertNull(
            TConAffixRewards.rollCompatibleMaterial(
                mapOf(1 to listOf("wood")),
                listOf(0, 0, 0, 0),
                RandomSource.create(1L)
            )
        )
        assertNull(
            TConAffixRewards.rollCompatibleMaterial<String>(
                emptyMap(),
                listOf(8000, 1700, 290, 10),
                RandomSource.create(2L)
            )
        )
    }

    @Test
    fun compatibleMaterialRollNeverChoosesAnEmptyTier() {
        val candidates = mapOf(
            1 to listOf("wood-a", "wood-b"),
            2 to emptyList(),
            3 to listOf("cobalt"),
            4 to emptyList()
        )
        repeat(2_000) { seed ->
            val selected = assertNotNull(
                TConAffixRewards.rollCompatibleMaterial(
                    candidates,
                    listOf(1, 1_000_000, 1, 1_000_000),
                    RandomSource.create(seed.toLong())
                )
            )
            assertTrue(selected == "wood-a" || selected == "wood-b" || selected == "cobalt")
        }
    }

    @Test
    fun affixCountBandsIncludeEveryBoundary() {
        val cases = listOf(
            0.0f to 6, 0.02999f to 6,
            0.03f to 5, 0.09999f to 5,
            0.10f to 4, 0.24999f to 4,
            0.25f to 3, 0.49999f to 3,
            0.50f to 2, 0.77999f to 2,
            0.78f to 1, 0.99999f to 1
        )
        cases.forEach { (roll, expected) -> assertEquals(expected, TConAffixRewards.affixCountForRoll(roll)) }
    }

    @Test
    fun fontAffixCountNeverProducesOneAffix() {
        assertEquals(6, TConAffixRewards.fontAffixCount(0.0f))
        assertEquals(5, TConAffixRewards.fontAffixCount(0.05f))
        assertEquals(4, TConAffixRewards.fontAffixCount(0.15f))
        assertEquals(3, TConAffixRewards.fontAffixCount(0.35f))
        assertEquals(2, TConAffixRewards.fontAffixCount(0.99f))
    }

    @Test
    fun affixAndPartDefinitionsAreUniqueAndComplete() {
        assertEquals(TConAffixRewards.allPartProfiles.size, TConAffixRewards.allPartProfiles.map { it.itemId }.distinct().size)
        assertEquals(TConAffixRewards.affixPool.size, TConAffixRewards.affixPool.map { it.id }.distinct().size)
        enumValues<TConAffixRewards.PartFamily>().forEach { family ->
            val definitions = TConAffixRewards.affixPool.filter { it.allows(family) }
            assertTrue(definitions.any { it.kind == TConAffixRewards.AffixKind.PREFIX }, family.name)
            assertTrue(definitions.any { it.kind == TConAffixRewards.AffixKind.SUFFIX }, family.name)
        }
        TConAffixRewards.affixPool.forEach { definition ->
            assertTrue(definition.id.isNotBlank())
            assertTrue(definition.name.isNotBlank(), definition.id)
            assertTrue(definition.group.isNotBlank(), definition.id)
            assertTrue(definition.weight > 0, definition.id)
            assertTrue(definition.families.isNotEmpty(), definition.id)
            assertTrue(definition.stats.isNotEmpty() || definition.modifiers.isNotEmpty(), definition.id)
            assertEquals(definition.tiers.size, definition.tiers.map { it.rank }.distinct().size, definition.id)
        }
    }

    @Test
    fun rolledAffixesRespectFamiliesGroupsAndSideCaps() {
        enumValues<TConAffixRewards.PartFamily>().forEachIndexed { familyIndex, family ->
            repeat(100) { sample ->
                val affixes = TConAffixRewards.rollAffixes(
                    "test:${family.name.lowercase()}",
                    family,
                    RandomSource.create(familyIndex * 10_000L + sample)
                )
                assertTrue(affixes.size in 1..6, family.name)
                assertTrue(affixes.count { it.getString("kind") == "prefix" } <= 3, family.name)
                assertTrue(affixes.count { it.getString("kind") == "suffix" } <= 3, family.name)
                assertEquals(affixes.size, affixes.map { it.getString("group") }.distinct().size, family.name)
                affixes.forEach { affix ->
                    val definition = assertNotNull(TConAffixRewards.definition(affix.getString("id")))
                    assertTrue(definition.allows(family), definition.id)
                    assertEquals(definition.kind.id, affix.getString("kind"))
                    assertEquals("test:${family.name.lowercase()}", affix.getString("source_part"))
                }
            }
        }
    }

    @Test
    fun fontRollsGuaranteeThemedAffixesAndRespectCaps() {
        val cases = listOf(
            AffixOrigin.NETHER to TConAffixRewards.PartFamily.MELEE_HEAD,
            AffixOrigin.AETHER to TConAffixRewards.PartFamily.BOW,
            AffixOrigin.UNDERGARDEN to TConAffixRewards.PartFamily.TOOL_HEAD,
            AffixOrigin.OTHERSIDE to TConAffixRewards.PartFamily.RANGED
        )
        cases.forEachIndexed { originIndex, (origin, family) ->
            repeat(100) { sample ->
                val affixes = TConAffixRewards.rollAffixes(
                    "test:${origin.id}", family, RandomSource.create(originIndex * 10_000L + sample), origin,
                    targetCount = TConAffixRewards.fontAffixCount(sample / 100f),
                    lucky = true,
                    guaranteeOriginAffix = true
                )
                assertTrue(affixes.size in 2..6)
                assertTrue(affixes.any { AffixOrigins.isExclusiveAffix(origin, it.getString("id")) }, origin.id)
                assertTrue(affixes.count { it.getString("kind") == "prefix" } <= 3)
                assertTrue(affixes.count { it.getString("kind") == "suffix" } <= 3)
            }
        }
    }

    @Test
    fun mergeReplacesOnlyTheMatchingSourcePartAndCopiesTags() {
        val oldHead = affix("tconstruct:attack_damage", 0.05, "tconstruct:pick_head")
        val oldHandle = affix("tconstruct:durability", 0.10, "tconstruct:tool_handle")
        val newHead = affix("tconstruct:mining_speed", 0.12, "tconstruct:pick_head")
        val merged = TConAffixRewards.mergeAffixes(listOf(oldHead, oldHandle), listOf(newHead))

        assertEquals(listOf("tconstruct:tool_handle", "tconstruct:pick_head"), merged.map { it.getString("source_part") })
        assertEquals(listOf("tconstruct:durability", "tconstruct:mining_speed"), merged.map { it.getString("stat") })
        newHead.putDouble("percent", 0.99)
        assertEquals(0.12, merged.last().getDouble("percent"))
    }

    @Test
    fun multiplierTagCompoundsIndependentStatLines() {
        val multipliers = TConAffixRewards.multiplierTag(
            listOf(
                affix("tconstruct:attack_damage", 0.10, "test:head"),
                affix("tconstruct:attack_damage", 0.20, "test:handle"),
                affix("tconstruct:mining_speed", 0.15, "test:head")
            )
        )
        assertEquals(1.32, multipliers.getFloat("tconstruct:attack_damage").toDouble(), 0.00001)
        assertEquals(1.15, multipliers.getFloat("tconstruct:mining_speed").toDouble(), 0.00001)
    }

    @Test
    fun modifierOwnershipPreservesOrdinaryLevelsAndRepairsStaleLedgers() {
        assertEquals(1, TConAffixRewards.ownedLevelDelta(2, 1, 2))
        assertEquals(-1, TConAffixRewards.ownedLevelDelta(3, 1, 0))
        assertEquals(0, TConAffixRewards.ownedLevelDelta(2, 0, 0))
        assertEquals(1, TConAffixRewards.ownedLevelDelta(0, 2, 1))
        assertEquals(0, TConAffixRewards.ownedLevelDelta(1, 2, 1))
    }

    @Test
    fun physicalOriginRejectsCrossFontPartMaterialCombinations() {
        assertEquals(AffixOrigin.NETHER, AffixOrigins.physicalOrigin("tinkersweaponry:great_blade", "tconstruct:manyullyn"))
        assertEquals(AffixOrigin.AETHER, AffixOrigins.physicalOrigin("tconstruct:small_blade", "tinkers_construct_affixes:zanite"))
        assertEquals(AffixOrigin.UNDERGARDEN, AffixOrigins.physicalOrigin("additionalweaponry:defensive_handle", "tinkers_construct_affixes:cloggrum"))
        assertEquals(AffixOrigin.GLOBAL, AffixOrigins.physicalOrigin("tinker_rapier:slender_blade", "tconstruct:manyullyn"))
    }

    @Test
    fun salvageQualityAndCurrencyTablesCoverTheirContracts() {
        val faint = listOf(affix("tconstruct:durability", 0.03, "test:part").apply { putInt("tier", 5) })
        val sovereign = List(6) { index -> affix("test:stat_$index", 0.20, "test:part").apply { putInt("tier", 1) } }
        assertEquals(1, AffixCrafting.salvageBand(faint, AffixOrigin.GLOBAL))
        assertEquals(4, AffixCrafting.salvageBand(sovereign, AffixOrigin.NETHER))

        val seen = buildSet {
            val random = RandomSource.create(918273L)
            repeat(10_000) { add(GlobalAffixLoot.rollKillCurrency(random)) }
        }
        assertEquals(AffixCurrencyType.entries.toSet(), seen)
    }

    @Test
    fun mergeWithNoReplacementDoesNotMutateExistingAffixes() {
        val existing = affix("tconstruct:durability", 0.10, "tconstruct:tool_handle")
        val merged = TConAffixRewards.mergeAffixes(listOf(existing), emptyList())
        existing.putDouble("percent", 0.50)
        assertFalse(merged.isEmpty())
        assertEquals(0.10, merged.single().getDouble("percent"))
    }

    @Test
    fun inventoryActionPacketRoundTripsEveryGuardField() {
        val original = AffixInventoryActionPacket(
            menuId = 17,
            sourceSlot = -1,
            targetSlot = 35,
            currencyType = AffixCurrencyType.PRESERVE_SUFFIXES.name,
            targetFingerprint = -1234567,
            nonce = Long.MAX_VALUE - 2,
            salvage = true
        )
        val buffer = FriendlyByteBuf(Unpooled.buffer())
        original.encode(buffer)
        assertEquals(original, AffixInventoryActionPacket.decode(buffer))
    }

    private fun affix(stat: String, percent: Double, sourcePart: String): CompoundTag =
        TConAffixRewards.createAffix(stat, percent, sourcePart)
}
