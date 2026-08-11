# TCon Affixes

Forge 1.20.1 mod that adds affixed Tinkers' Construct parts to the global loot pool.

Affixed parts can drop from hostile mobs and appear in chest loot caches. Each reward has a compatible Tinkers material, rolled from configurable weighted tiers; tier 4 remains a deliberately rare jackpot. Percentage rolls persist through Tinkers stat rebuilds, and modifier grants are tracked separately from ordinary player-applied modifier levels.

Server worlds may tune drop chances, tier weights, and material allowlists in `tconaffixes-server.toml`. Defaults are 1% for hostile drops, 3% for chest caches, and material-tier weights of 80% / 17% / 2.9% / 0.1%.

## Build

Use Java 17 and run:

```sh
./gradlew test reobfJar stageRuntimeJar
```

The deployable reobfuscated jar is written to `build/libs/tconaffixes-<version>.jar`.
