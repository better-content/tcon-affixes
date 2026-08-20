package com.bettercontent.tinkersconstructaffixes

import com.mojang.authlib.GameProfile
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraftforge.event.RegisterGameTestsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import slimeknights.tconstruct.library.materials.definition.IMaterial
import slimeknights.tconstruct.library.materials.definition.MaterialId
import slimeknights.tconstruct.library.materials.definition.MaterialVariant
import slimeknights.tconstruct.library.materials.MaterialRegistry
import slimeknights.tconstruct.library.tools.item.IModifiable
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT
import slimeknights.tconstruct.library.tools.nbt.ToolStack
import slimeknights.tconstruct.library.tools.part.ToolPartItem
import java.util.UUID

@GameTestHolder(TConAffixesMod.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = TConAffixesMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
object TConAffixGameTests {
    @SubscribeEvent
    @JvmStatic
    fun register(event: RegisterGameTestsEvent) {
        event.register(TConAffixGameTests::class.java)
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    @JvmStatic
    fun cacheUseProducesOneStampedPartAndConsumesOneCache(helper: GameTestHelper) {
        val player = testPlayer(helper, "cache-main-hand")
        val cache = ItemStack(AffixItems.CACHE.get(), 2)
        player.setItemInHand(InteractionHand.MAIN_HAND, cache)

        cache.use(helper.level, player, InteractionHand.MAIN_HAND)

        helper.assertTrue(cache.count == 1, "A successful cache use must consume exactly one cache")
        val rewards = inventoryParts(player)
        helper.assertTrue(rewards.size == 1, "A successful cache use must insert exactly one Tinkers part")
        assertStampedReward(helper, rewards.single(), AffixOrigin.GLOBAL)
        helper.succeed()
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    @JvmStatic
    fun cacheSupportsOffhandUse(helper: GameTestHelper) {
        val player = testPlayer(helper, "cache-offhand")
        val cache = ItemStack(AffixItems.CACHE.get(), 2)
        player.setItemInHand(InteractionHand.OFF_HAND, cache)

        cache.use(helper.level, player, InteractionHand.OFF_HAND)

        helper.assertTrue(cache.count == 1, "Offhand cache use must consume exactly one cache")
        val rewards = inventoryParts(player)
        helper.assertTrue(rewards.size == 1, "Offhand cache use must insert exactly one Tinkers part")
        assertStampedReward(helper, rewards.single(), AffixOrigin.GLOBAL)
        helper.succeed()
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    @JvmStatic
    fun everyOriginProducesValidRewardsAcrossDeterministicSamples(helper: GameTestHelper) {
        val origins = listOf(AffixOrigin.GLOBAL, AffixOrigin.NETHER, AffixOrigin.AETHER, AffixOrigin.UNDERGARDEN, AffixOrigin.OTHERSIDE)
        origins.forEachIndexed { originIndex, origin ->
            repeat(250) { sample ->
                val reward = TConAffixRewards.rollAffixedPart(
                    RandomSource.create(originIndex * 100_000L + sample),
                    origin,
                    "gametest"
                )
                helper.assertTrue(reward != null, "Origin ${origin.id} returned no reward for deterministic sample $sample")
                assertStampedReward(helper, reward!!, origin, "gametest")
            }
        }
        helper.succeed()
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    @JvmStatic
    fun everySelectableProfileHasAPositiveCompatibleMaterialTier(helper: GameTestHelper) {
        val origins = listOf(AffixOrigin.GLOBAL, AffixOrigin.NETHER, AffixOrigin.AETHER, AffixOrigin.UNDERGARDEN, AffixOrigin.OTHERSIDE)
        origins.forEach { origin ->
            val modes = if (origin == AffixOrigin.GLOBAL) listOf(false) else listOf(false, true)
            modes.forEach { exclusive ->
                TConAffixRewards.allPartProfiles
                    .filter { AffixOrigins.allowsPart(origin, it.itemId, exclusive) }
                    .forEach { profile ->
                        val item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                            net.minecraft.resources.ResourceLocation(profile.itemId)
                        ) as? ToolPartItem
                        if (item != null) {
                            val materials = TConAffixRewards.viableMaterialsByTier(item, origin)
                            helper.assertTrue(
                                TConAffixRewards.hasPositiveWeightedMaterials(materials, origin),
                                "${origin.id}/${if (exclusive) "exclusive" else "themed"}/${profile.itemId} has no positive compatible tier"
                            )
                        }
                    }
            }
        }
        helper.succeed()
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    @JvmStatic
    fun inventoryPacketsAreAtomicAndReplaySafe(helper: GameTestHelper) {
        val player = testPlayer(helper, "affix-packet")
        val target = TConAffixRewards.rollAffixedPart(RandomSource.create(781L), AffixOrigin.GLOBAL, "gametest")
        helper.assertTrue(target != null, "Could not create packet target")
        val oneAffix = TConAffixRewards.existingToolAffixes(target!!).take(1)
        TConAffixRewards.writeToolAffixes(target, oneAffix)
        val source = AffixItems.stack(AffixCurrencyType.REMOVE, 2)
        player.inventory.setItem(0, source)
        player.inventory.setItem(1, target)

        val targetFingerprint = AffixNetwork.fingerprint(target)
        val differentCount = target.copy().apply { count += 1 }
        val differentTag = target.copy().apply { orCreateTag.putString("fingerprint_test", "changed") }
        helper.assertTrue(targetFingerprint != AffixNetwork.fingerprint(differentCount), "Fingerprint must include stack count")
        helper.assertTrue(targetFingerprint != AffixNetwork.fingerprint(differentTag), "Fingerprint must include stack NBT")
        helper.assertTrue(targetFingerprint == AffixNetwork.fingerprint(target.copy()), "Fingerprint must be stable for an exact copy")

        val valid = AffixInventoryActionPacket(
            player.containerMenu.containerId,
            0,
            1,
            AffixCurrencyType.REMOVE.name,
            targetFingerprint,
            10L,
            false
        )
        AffixNetwork.handle(valid, player)
        helper.assertTrue(source.count == 1, "Applied packet did not consume exactly one currency")
        helper.assertTrue(TConAffixRewards.existingToolAffixes(target).isEmpty(), "Applied remove packet did not remove the affix")

        AffixNetwork.handle(valid, player)
        helper.assertTrue(source.count == 1, "Replayed packet consumed currency a second time")

        val staleFingerprint = valid.copy(
            currencyType = AffixCurrencyType.RECAST.name,
            targetFingerprint = valid.targetFingerprint + 1,
            nonce = 11L
        )
        AffixNetwork.handle(staleFingerprint, player)
        helper.assertTrue(source.count == 1, "Stale-target packet consumed currency")
        helper.assertTrue(TConAffixRewards.existingToolAffixes(target).isEmpty(), "Stale-target packet mutated its target")
        helper.succeed()
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    @JvmStatic
    fun currenciesAndSalvageRespectTransactionalGuards(helper: GameTestHelper) {
        val base = TConAffixRewards.rollAffixedPart(RandomSource.create(991L), AffixOrigin.GLOBAL, "gametest")
        helper.assertTrue(base != null, "Could not create currency target")
        val originalAffixes = TConAffixRewards.existingToolAffixes(base!!)
        helper.assertTrue(originalAffixes.isNotEmpty(), "Currency target has no affixes")

        val recast = base.copy()
        helper.assertTrue(
            AffixCrafting.apply(recast, AffixCurrencyType.RECAST, RandomSource.create(1L)) == AffixOperationResult.APPLIED,
            "Recasting did not apply"
        )

        val add = base.copy()
        TConAffixRewards.writeToolAffixes(add, originalAffixes.take(1))
        helper.assertTrue(
            AffixCrafting.apply(add, AffixCurrencyType.ADD, RandomSource.create(2L)) == AffixOperationResult.APPLIED,
            "Grafting did not add an affix"
        )
        helper.assertTrue(TConAffixRewards.existingToolAffixes(add).size == 2, "Grafting did not add exactly one affix")

        val profile = TConAffixRewards.partProfile(base)
        helper.assertTrue(profile != null, "Currency target has no registered part profile")
        val sixAffixes = TConAffixRewards.rollAffixes(
            profile!!.itemId,
            profile.family,
            RandomSource.create(21L),
            targetCount = 6
        )
        val full = base.copy()
        TConAffixRewards.writeToolAffixes(full, sixAffixes)
        helper.assertTrue(
            AffixCrafting.apply(full, AffixCurrencyType.ADD, RandomSource.create(22L)) == AffixOperationResult.FULL,
            "Grafting a six-affix part did not fail safely"
        )

        val preservePrefixes = base.copy()
        TConAffixRewards.writeToolAffixes(preservePrefixes, sixAffixes)
        helper.assertTrue(
            AffixCrafting.apply(preservePrefixes, AffixCurrencyType.PRESERVE_PREFIXES, RandomSource.create(23L)) == AffixOperationResult.APPLIED,
            "Prefix preservation did not apply to a populated two-sided part"
        )
        val preserveSuffixes = base.copy()
        TConAffixRewards.writeToolAffixes(preserveSuffixes, sixAffixes)
        helper.assertTrue(
            AffixCrafting.apply(preserveSuffixes, AffixCurrencyType.PRESERVE_SUFFIXES, RandomSource.create(24L)) == AffixOperationResult.APPLIED,
            "Suffix preservation did not apply to a populated two-sided part"
        )
        val oneSided = base.copy()
        TConAffixRewards.writeToolAffixes(oneSided, sixAffixes.filter { it.getString("kind") == "prefix" })
        helper.assertTrue(
            AffixCrafting.apply(oneSided, AffixCurrencyType.PRESERVE_PREFIXES, RandomSource.create(25L)) == AffixOperationResult.NEEDS_BOTH_SIDES,
            "Preservation did not reject a one-sided part"
        )

        val remove = base.copy()
        TConAffixRewards.writeToolAffixes(remove, originalAffixes.take(1))
        helper.assertTrue(
            AffixCrafting.apply(remove, AffixCurrencyType.REMOVE, RandomSource.create(3L)) == AffixOperationResult.APPLIED,
            "Shearing did not apply"
        )
        helper.assertTrue(
            AffixCrafting.apply(remove, AffixCurrencyType.REMOVE, RandomSource.create(4L)) == AffixOperationResult.NO_AFFIXES,
            "Shearing an empty part did not fail safely"
        )

        val locked = base.copy()
        locked.orCreateTag.putBoolean(AffixCrafting.MUTATION_LOCKED_TAG, true)
        helper.assertTrue(
            AffixCrafting.apply(locked, AffixCurrencyType.MUTATE, RandomSource.create(5L)) == AffixOperationResult.LOCKED,
            "Mutation lock did not prevent a second mutation"
        )

        val survivingMutation = base.copy()
        val survivalSeed = firstSeed { it < 0.80f }
        helper.assertTrue(
            AffixCrafting.apply(survivingMutation, AffixCurrencyType.MUTATE, RandomSource.create(survivalSeed)) == AffixOperationResult.APPLIED,
            "Surviving mutation did not apply"
        )
        helper.assertTrue(survivingMutation.tag?.getBoolean(AffixCrafting.MUTATION_LOCKED_TAG) == true, "Surviving mutation was not locked")

        val destroyedMutation = base.copy()
        val destructionSeed = firstSeed { it >= 0.80f }
        helper.assertTrue(
            AffixCrafting.apply(destroyedMutation, AffixCurrencyType.MUTATE, RandomSource.create(destructionSeed)) == AffixOperationResult.DESTROYED,
            "Destructive mutation did not report destruction"
        )
        helper.assertTrue(destroyedMutation.isEmpty, "Destructive mutation did not consume the part")

        val salvage = base.copy()
        val payout = AffixCrafting.salvage(salvage)
        helper.assertTrue(payout != null, "Natural affixed part could not be salvaged")
        helper.assertTrue(TConAffixRewards.existingToolAffixes(salvage).isEmpty(), "Salvage did not strip affixes")
        TConAffixRewards.writeToolAffixes(salvage, originalAffixes.take(1))
        helper.assertTrue(AffixCrafting.salvage(salvage).orEmpty().isEmpty(), "Spent salvage metadata paid out twice")
        helper.succeed()
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    @JvmStatic
    fun assembledToolReceivesCompatiblePartAffixesAndRebuildsStats(helper: GameTestHelper) {
        val item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
            net.minecraft.resources.ResourceLocation("tconstruct:pickaxe")
        ) as? IModifiable
        helper.assertTrue(item != null, "TConstruct pickaxe is unavailable")
        val wood = MaterialRegistry.getInstance().getMaterial(MaterialId("tconstruct", "wood"))
        helper.assertTrue(wood != IMaterial.UNKNOWN, "TConstruct wood material is unavailable")
        val tool = ToolStack.createTool(
            item!!.asItem(),
            item.toolDefinition,
            MaterialNBT(List(3) { MaterialVariant.of(wood) })
        ).also { it.rebuildStats() }.createStack()

        val head = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
            net.minecraft.resources.ResourceLocation("tconstruct:pick_head")
        ) as? ToolPartItem
        helper.assertTrue(head != null, "TConstruct pick head is unavailable")
        val affixedHead = head!!.withMaterial(wood.identifier)
        val damage = TConAffixRewards.createAffix("tconstruct:attack_damage", 0.10, "tconstruct:pick_head")
        val incompatible = TConAffixRewards.createAffix("tconstruct:projectile_damage", 0.50, "tconstruct:pick_head")
        TConAffixRewards.writeToolAffixes(affixedHead, listOf(damage, incompatible))

        helper.assertTrue(TConAffixRewards.transferAffixes(tool, listOf(affixedHead)), "Part affixes did not transfer to assembled tool")
        val transferred = TConAffixRewards.existingToolAffixes(tool)
        helper.assertTrue(transferred.size == 1, "Incompatible projectile affix was not filtered from pickaxe")
        helper.assertTrue(TConAffixRewards.rolls(transferred.single()).single().first == "tconstruct:attack_damage", "Expected damage affix was not retained")
        val toolStack = ToolStack.from(tool)
        helper.assertTrue(
            toolStack.persistentData.getCompound(AffixModifiers.MULTIPLIERS_KEY).getFloat("tconstruct:attack_damage") > 1.0f,
            "Transferred damage multiplier was not persisted"
        )
        helper.assertTrue(toolStack.getUpgrades().getLevel(AffixModifiers.STAT_DRIVER.id) == 1, "Affix stat driver was not installed")
        helper.succeed()
    }

    private fun firstSeed(predicate: (Float) -> Boolean): Long {
        for (seed in 0L until 100_000L) {
            if (predicate(RandomSource.create(seed).nextFloat())) return seed
        }
        error("Could not find deterministic mutation seed")
    }

    private fun testPlayer(helper: GameTestHelper, name: String): ServerPlayer = ServerPlayer(
        helper.level.server,
        helper.level,
        GameProfile(UUID.randomUUID(), name)
    )

    private fun inventoryParts(player: ServerPlayer): List<ItemStack> =
        (0 until player.inventory.containerSize)
            .map(player.inventory::getItem)
            .filter { it.item is ToolPartItem }

    private fun assertStampedReward(
        helper: GameTestHelper,
        reward: ItemStack,
        origin: AffixOrigin,
        provenance: String = "cache"
    ) {
        val part = reward.item as? ToolPartItem
        helper.assertTrue(part != null, "Reward is not a ToolPartItem")
        helper.assertTrue(part!!.getMaterial(reward) != IMaterial.UNKNOWN_ID, "Reward has an unknown material")
        helper.assertTrue(TConAffixRewards.existingToolAffixes(reward).isNotEmpty(), "Reward has no affixes")
        val tag = reward.tag
        helper.assertTrue(tag != null, "Reward has no metadata")
        helper.assertTrue(tag!!.getInt(AffixCrafting.DATA_VERSION_TAG) == AffixCrafting.DATA_VERSION, "Reward data version is not current")
        helper.assertTrue(tag.getString(AffixCrafting.ORIGIN_TAG) == origin.id, "Reward origin does not match ${origin.id}")
        helper.assertTrue(tag.getString(AffixCrafting.PROVENANCE_TAG) == provenance, "Reward provenance does not match $provenance")
        helper.assertTrue(tag.getBoolean(AffixCrafting.NATURAL_TAG), "Reward is not marked natural")
        helper.assertTrue(tag.contains(AffixCrafting.SALVAGE_SEED_TAG), "Reward has no immutable salvage seed")
        helper.assertTrue(tag.getInt(AffixCrafting.SALVAGE_BAND_TAG) in 1..4, "Reward salvage band is outside 1..4")
    }
}
