# TCon Affixes

Forge 1.20.1 mod that turns Tinkers' Construct parts into a global, long-term loot chase.

Affixed parts can drop from player-killed hostile mobs and appear in chest loot caches. Each reward has a compatible Tinkers material, rolled from configurable weighted tiers; tier 4 remains a deliberately rare jackpot. Percentage rolls persist through Tinkers stat rebuilds, and modifier grants are tracked separately from ordinary player-applied modifier levels.

The Nether, Aether, Undergarden, and Otherside act as distinct "Fonts" with themed materials, part pools, guaranteed regional affixes, and better 2–6-affix rolls. Physical part/material provenance wins over stored metadata, so moving or editing NBT cannot turn a global item into a Font reward.

Six currencies support an inventory-native crafting loop:

- Right-click a flux or seal in any player-inventory slot to arm it, then left-click a compatible Tinkers part. Shift-click repeats while the currency remains available.
- Recasting rerolls, Grafting adds a line, and Shearing removes one.
- Forerune and Afterrune preserve one populated affix side while rerolling the other.
- Ruinous Flux offers a confirmed, one-way mutation with powerful outcomes, permanent mutation lock, and a 20% destruction risk.
- Shift-right-click an affixed part with no currency armed to salvage it. Natural rewards keep an immutable salvage seed and quality band, preventing reroll or duplication exploits.

Master toolsmiths sell the two preservation seals. Fluxes, seals, caches, and affixed parts also enter the global combat and chest-loot economy at deliberately low rates.

Server worlds may tune hostile and Font drop rates, currency and cache rates, tier weights, and material allowlists in `tconaffixes-server.toml`. Global defaults are 1% hostile part/currency drops, 3% chest cache/currency rolls, and material-tier weights of 80% / 17% / 2.9% / 0.1%. Font hostile drops use 3% part and 4% currency rates.

## Build

Use Java 17 and run:

```sh
./gradlew test reobfJar stageRuntimeJar
```

The deployable reobfuscated jar is written to `build/libs/tconaffixes-<version>.jar`.
