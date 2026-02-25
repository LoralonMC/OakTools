# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.0] - 2026-02-25

### Added

- Vulcan anticheat integration — automatically exempts players from FastPlace detection during wand block placement
- Builder's Wand tool with Face (BFS flood-fill) and Line (directional) placement modes
- Wand offhand block override: hold a placeable block in the other hand to place that material instead of copying the clicked block
- Wand undo system: sneak + right-click air to undo the last wand operation (configurable history depth and expiry)
- Wand placement preview: glow outlines show where the wand will place before committing
- `WandPlaceEvent` custom cancellable event for third-party plugin integration
- CoreProtect logging for wand placements and undo operations
- Configurable `tools.file.excluded-blocks` list (replaces hardcoded block exclusion)
- `oaktools.repair.anvil` permission for anvil tool repair (default: true)
- Low durability warning with configurable threshold (`durability-warning-threshold`)
- `tool-broken`, `gamemode-denied`, and `craft-denied` config messages
- `paper-plugin.yml` as primary plugin descriptor
- `oaktools.*` wildcard permission with per-command children
- Recipe ingredient validation at startup
- `config-version` footer for future config migrations

### Fixed

- Anvil tool combining not merging enchantments from the second tool
- Base material vanilla behavior (strider attraction) activating on right-click air with tools
- Tools created via `give` command without durability argument had 0 remaining durability
- File and Trowel calling `updateDisplay()` on a broken tool after `damage()` destroys it
- Anvil tool combine copying unknown PDC keys with assumed STRING type
- Anvil repair result showing italic name
- Anvil full repair losing item lore/description
- File and Trowel tools cannot be renamed in an anvil
- Anvil repair/combine resetting player-given custom names
- Anvil repair/combine not applying rename when player also types a new name
- Give command durability parameter was inverted (200 meant 200 damage, not 200 remaining)
- Gamemode denial silently ignoring tool use without player feedback
- Adventure mode `consume-blocks` config option defined but never read
- Recipe validation errors not propagated to config validity check

### Changed

- **BREAKING**: Migrate from Configurate to Bukkit's FileConfiguration
- **BREAKING**: Rename all config keys from `snake_case` to `kebab-case` (e.g. `model_id` → `model-id`, `repair_amount` → `repair-amount`)
- **BREAKING**: Rewrite message format from `enabled`/`delivery`/`content` to `text`/`display` structure (empty `text: ""` disables a message)
- **BREAKING**: Apply hex color palette to all default messages
- **BREAKING**: Command message keys renamed from `snake_case` to `kebab-case` (e.g. `player_not_found` → `player-not-found`)
- **BREAKING**: Message placeholders changed from `%placeholder%` to MiniMessage `<placeholder>` format (e.g. `%player%` → `<player>`)
- Minimum Paper version set to 1.21.8
- Tool display names no longer italic by default (switched to `itemName` API)
- `nexo:id` and `itemsadder:id` model formats auto-detect whether the source item uses Item Model or CustomModelData
- Remove prefix from all action bar messages
- Remove `oaktools.admin` intermediate permission (per-command permissions listed directly under `oaktools.*`)
- Move trowel feed source display names to `tools.trowel.feed-source-names`

### Removed

- Configurate dependency (JAR size: 947 KB → 141 KB)

## [1.0.5] - 2026-02-09

### Added

- World restriction enforcement to File and Trowel listeners
- Shared utilities in BlockUtil (`isFlowerPot`, `canUseInGamemode`, `isInteractiveBlock`, `isWorldAllowed`)
- ConfigManager caching for debug flag and replaceable materials
- Init guard to Constants to prevent double-initialization
- Durability bounds validation to give command

### Changed

- Default world restriction mode changed from WHITELIST to BLACKLIST
- Reduce `getNearbyEntities` radius from 5 to 2

### Fixed

- Wall center post disappearing when cycling connections with File tool
- ConfigValidator not returning false for critical validation failures
- `ToolType.fromString()` not returning null for unknown types

### Removed

- Deprecated `Enchantment.getByName` calls (replaced with Registry API)
- Unnecessary `runTask` wrappers in CoreProtectLogger

## [1.0.4] - 2025-12-04

### Added

- ItemModelProvider for modern item models (1.21.4+)
- Support `model:namespace:key` format in config for item model assignment

### Fixed

- Potential memory leak in MendingListener (player quit cleanup)

## [1.0.3] - 2025-11-15

### Changed

- Exclude all coral fan variants (20 types) from File tool editing
- Exclude glow lichen and sculk vein from File tool editing
- Exclude amethyst growth stages (buds and clusters) from File tool editing

## [1.0.2] - 2025-11-15

### Added

- Custom MendingListener for correct durability scaling on OakTools items

### Fixed

- Trowel `can_replace` config not being respected (removed overly broad fallback that allowed replacing any non-solid block)
- Mending enchantment using base item durability (100) instead of configured durability (250), making Mending 2.5x cheaper than intended
- File and Trowel interference with flower pot interactions (ghost item bug)
- Trowel placing blocks when interacting with GUI blocks (stonecutters, crafting tables, anvils, etc.)

### Changed

- Event handling priority set to LOWEST to prevent interference with vanilla mechanics

## [1.0.1] - 2025-11-14

### Changed

- Exclude torches, levers, doors, ladders, vines, portals from File tool editing
- Exclude red mushroom blocks from File tool editing
- Exclude buttons, pressure plates, rails, trip wire hooks from File tool editing
- Exclude interactive GUI blocks (crafting tables, anvils, etc.) from File tool editing
- Exclude special blocks (dragon egg, bells, respawn anchors) from File tool editing
- Exclude eggs, spawn blocks, cauldrons, cakes, composters from File tool editing
- Trowel now detects and excludes all custom items with PDC data (ExecutableItems, ItemsAdder, Nexo)
- Exclude shulker boxes, tile entities, unobtainable blocks, TNT, and dragon egg from Trowel placement

### Fixed

- Compressed Emerald Blocks placeable via Trowel
- Claim Chunks turning into grass blocks via Trowel

## [1.0.0] - 2025-11-14

### Added

- Initial release
- File tool for editing block properties (facing, axis, shape, connections, waterlogging)
- Trowel tool for rapid block placement from inventory
- Custom durability system with configurable max durability per tool
- Configurable crafting recipes per tool
- Anvil repair with configurable repair material and amount
- Custom model support via Nexo, ItemsAdder, and vanilla CustomModelData
- Protection plugin integration (WorldGuard, GriefPrevention, Towny, etc.)
- CoreProtect logging for File edits and Trowel placements
- MiniMessage formatting for all player-facing messages
- Recipe discovery system (auto-unlock recipes when ingredients collected)
- Gamemode and world restrictions
- Permission nodes for tool usage, crafting, commands, and protection bypass
- Commands: `/oaktools give`, `/oaktools reload`, `/oaktools info`, `/oaktools repair`
