# Changelog

## Unreleased

### Fixed

- Made Affixed Part Caches select only part profiles and weighted material tiers that can produce a valid Tinkers part in the current physical origin.
- Kept failed cache openings non-destructive and added player-facing feedback for genuinely invalid material configurations.
- Expanded startup diagnostics to identify malformed, missing, hidden, incorrectly tiered, incompatible, and zero-weight reward pools.

### Testing

- Restored canonical-package unit coverage for reward selection, affix invariants, merging, stat multipliers, modifier ownership, origins, salvage, and currencies.
- Added Forge GameTests for cache consumption, reward metadata, offhand use, every physical origin, and selectable profile/material viability.

### Changed

- Standardized the project as **Tinkers Construct Affixes** with mod ID `tinkers_construct_affixes`, artifact `tinkers-construct-affixes`, and package `com.bettercontent.tinkersconstructaffixes`.
- Adopted Java 17 and Forge 1.20.1-47.4.13 as the build baseline without changing the project version.
- This is a clean break; legacy worlds, configurations, and integrations are not migrated.
