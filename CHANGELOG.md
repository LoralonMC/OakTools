# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.4.0] - 2026-10-20

### Added

- `tools.file.excluded-blocks` accepts vanilla block tags: an entry such as `'#minecraft:doors'` (quoted) excludes every block in the tag.

### Changed

- Targets Paper 26.3 (`api-version: '26.3'`, built against paper-api 26.3). This build no longer loads on 26.1.2.
- The Wand and Trowel recognise beds and buttons by tag, so the straw bed and pale oak and poplar buttons count as interactive.
- The default `tools.file.excluded-blocks` uses block tags for doors, trapdoors, fence gates, buttons, pressure plates, beds, candle cakes, cauldrons and rails, so the File also leaves pale oak and poplar blocks and the straw bed alone. Existing configs keep their own list; switch it to the tag form by hand.

## [2.3.4] - 2026-10-19

### Fixed

- Sickle harvests credited quest objectives one crop short per swing, and a single-crop or sneak
  harvest credited nothing. The Sickle cancels the clicked crop's break event to harvest and
  replant it itself, and announced only the radius crops via `SickleHarvestEvent`, relying on the
  cancelled break event to cover the clicked one. OakQuests ignores cancelled breaks, so that crop
  was never counted. `SickleHarvestEvent` now fires for the clicked crop too.

## [2.3.3] - 2026-10-18

### Fixed

- `/oaktools reload` was refused ("Config validation failed. Keeping old cached values.") and
  every start logged "Configuration has fatal errors". The config validator still required recipe
  shapes to have exactly 3 rows after 2.3.0 allowed 1-3, so the two-row patina recipe failed it.
  The validator now applies the same rule as recipe registration: 1-3 rows of equal width. The
  recipe itself always registered; only validation and reload were affected.

## [2.3.2] - 2026-10-18

### Fixed

- Two worn tools of the same kind combined in a crafting grid into a plain item of the base material, losing the tool. Two Files came out as a Warped Fungus on a Stick: vanilla's repair recipe (`minecraft:repair_item`) matches on material alone and ignores the tool data. The recipe is now blocked for tools in crafting tables, the player's 2x2 grid and crafter blocks. Combining at an anvil is unchanged and is the supported way to merge durability.

## [2.3.1] - 2026-09-11

### Fixed

- Only operators could craft the patina tool. `oaktools.use.patina` was declared but `oaktools.craft.patina` was not, and `CraftingListener` derives that node from the tool type, so an undeclared node defaulted to op-only. Exactly the failure its own comment warns about for `vein_miner`. Adding a tool needs **both** nodes.

## [2.3.0] - 2026-09-11

### Added

- **Patina tool** (`tools.patina`, shipped as the Verdigris Can): right-click copper to weather it forward one stage. Vanilla offers no way to advance copper except waiting on random ticks — only scraping backward with an axe is instant — so this covers the missing direction. One block per click, to sit alongside the File as a precision tool.
  - Waxed copper is never affected. Waxing is how a player locks a stage; advancing it would defeat the only lock they have.
  - Covers all 13 copper families including doors, trapdoors, chests, bars, chains, lanterns, grates, bulbs and golem statues: 42 transitions in total. Stages are **derived from block names** rather than listed, so copper blocks added by future Minecraft versions work with no code or config change. The one exception is handled explicitly: the plain block is `COPPER_BLOCK` but its stages are `EXPOSED_COPPER` and friends.
  - Block state is carried across the change, so a stair keeps its shape, a slab its half and a door its hinge.
  - Configurable particle effect (`tools.patina.effect`: particle, count, spread; `NONE` to disable).
  - Respects claims via the existing protection service and logs to CoreProtect.

### Changed

- `DisplayService.updateDisplay` now refreshes the styled name and the item model, not just the lore. Previously an item kept whatever name and model it was stamped with when crafted, so renaming or re-skinning a tool in config only affected newly crafted ones and left older copies permanently inconsistent. Tools already in inventories now catch up on next use.
  - Safe against player renames: an anvil writes `customName()`, a separate field which displays over the `itemName()` this plugin sets.
  - An empty `model-id` is left alone rather than cleared, so a tool shipped without a model keeps its base material's look until one is configured.

## [2.2.2] - 2026-08-01

### Fixed

- The 2.2.1 Vulcan integration not actually suppressing anything, so the Builder's Wand still got players kicked for Fast Place. Vulcan's `Check#getName()` returns the check name lowercased with spaces stripped (`fastplace`), not the `@CheckInfo` spelling (`Fast Place`) the filter compared against, so no flag ever matched. Check names are now normalised before comparison and matched against either `getName()` or `getDisplayName()`. A flag arriving during a probe that does not match is now logged in debug mode, so a future name change can't fail silently again

## [2.2.1] - 2026-07-31

### Fixed

- Upgrading a plain diamond hoe in a smithing table producing a netherite Sickle instead of a netherite hoe. The netherite Sickle was a `SmithingTransformRecipe` whose base ingredient was a `MaterialChoice` of `DIAMOND_HOE`, and recipe ingredients match on material alone, so it matched every diamond hoe and shadowed the vanilla upgrade. The upgrade is now applied by restamping the result of the vanilla diamond-to-netherite upgrade, and only when the item that went in was actually a Sickle. As a result the upgrade now also preserves enchantments and durability, which the old recipe discarded

- Players being kicked by the Vulcan anticheat for Fast Place while using the Builder's Wand, and for Fast Break while using the Excavation Shovel, Vein Miner, Lumberjack's Axe, or Sickle. OakTools asks protection plugins for permission by firing a throwaway `BlockPlaceEvent`/`BlockBreakEvent` per candidate block, and Vulcan's Fast Place / Fast Break checks simply count those events — one wand click probes up to `tools.wand.max-blocks` positions, so a few clicks per second cleared Vulcan's 128-per-second threshold and punished the player for blocks they never placed. Those two flags are now cancelled while OakTools is probing, via a new soft-dependency Vulcan integration. Configurable under `integration.vulcan`; requires `enable-api: true` in Vulcan's own config (the default)

## [2.2.0] - 2026-07-22

### Added

- `OakToolBlockBreakEvent` fired once per extra block cleared by a multi-block operation (Lumberjack fell, Vein Miner, Excavator) from `BreakingAnimationManager`. Those cascade blocks are broken directly and fire no `BlockBreakEvent`, so downstream systems (quest objectives) can now credit a whole tree/vein/dig instead of just the clicked block. The initially clicked block is not re-announced (its own `BlockBreakEvent` already accounts for it).

## [2.1.0] - 2026-07-21

### Added

- `SickleHarvestEvent` fired per radius crop the Sickle clears (which otherwise fire no BlockBreakEvent), so downstream systems (quest objectives) can credit a full sickle sweep. The clicked crop is not re-announced (its pre-cancel BlockBreakEvent already accounts for it).

### Fixed

- Plugin failing to enable on servers without CoreProtect installed — the CoreProtect integration referenced a CoreProtect class during initialization regardless of whether the (soft-dependency) plugin was present, throwing `NoClassDefFoundError` and disabling all of OakTools. The API is now resolved only after confirming CoreProtect is loaded
- Leaves placed by the Trowel decaying and disappearing shortly after placement — trowel-placed leaves were left in the non-persistent (decay) state, unlike leaves placed by hand. They are now marked persistent so they never decay
- Builder's Wand block duplication when placing from the offhand override stack — offhand blocks were counted (and the placement limit calculated) twice, letting the wand place more blocks than were consumed. Inventory counting and consumption now scan only the main storage (slots 0-35), with the offhand handled exclusively by the override logic

### Added

- Trowel can now place Azalea, Flowering Azalea, and all carpets (16 dyed colors plus Moss Carpet and Pale Moss Carpet)
- Excavation Shovel harvesting tool — mines a 3x3 area of shovel-mineable blocks on the clicked face with animated one-by-one breaking
- Animated breaking system with per-block break particles, sound effects, and crack animation via PacketEvents
- Sneak while breaking to disable multi-block activation (mine single blocks normally)
- OakOverflow integration for inventory overflow — drops go to inventory first, overflow storage second, ground last
- Vein Miner Pickaxe harvesting tool — mines connected ore veins with deepslate variant grouping
- Lumberjack's Axe harvesting tool — chops entire generated trees with natural leaf validation and player-placed log detection
- PlacedBlockTracker to distinguish player-placed logs from generated trees (in-memory, same pattern as UltimateTimber)
- Sickle tool — tiered crop harvesting with auto-replant (wooden through netherite, vanilla durability/enchanting/repair). Replanting consumes a seed from the harvest drops, falling back to the player's inventory when the harvest rolls none; the sickle can't till soil into farmland and won't break immature crops (each behavior configurable under `tools.sickle`)
- Enchanting table blocked for "locked" tools (tools with empty allowed-enchantments)
- Per-tool `unbreakable` config option for infinite durability
- File tool now protects extended (powered) pistons from rotation by default, preventing piston head desync and block-duplication glitches. Configurable via `tools.file.protect-extended-pistons` (retracted pistons remain editable)
- Config validation now warns when `tools.excavator.grid-size` is set to an even number (it is rounded up to the next odd value)

### Changed

- Migrate to OakheartLib shared library (oakheart-core for config/messages/commands, oakheart-models for model providers; now 1.3.0 — comment changes in the default config are synced onto existing config files without touching admin-customized comments)
- Messages moved from config.yml to standalone messages.yml (auto-migrated on first load)
- Replace Bukkit FileConfiguration with OakheartLib ConfigManager (format-preserving YAML)
- Replace local ModelProviderManager with shared oakheart-models library
- Update to Paper 26.1.2 / Java 25
- Builder's Wand preview redesigned as a wireframe outline of the blocks that will be placed (replacing the glow highlight), drawn with client-only packet displays (no collision). The preview now requires PacketEvents — without it the wand still works, but no preview is shown. New options: `tools.wand.preview.line-thickness`, `tools.wand.preview.line-color` (replaces the preview's old `glow-color`; accepts a named color or a `#RRGGBB`/`#AARRGGBB` hex code), and `tools.wand.preview.beam-type` (`block` block-display beams, default, or `text` flat text-display ribbons). Text beams use the exact color and honor alpha translucency; block beams snap to the nearest concrete color.

### Removed

- Local MessageManager, ModelProvider, and model provider implementations (replaced by OakheartLib)

### Fixed

- Default permissions (use, craft, anvil repair) not registering with Paper's permission system, causing non-op players to get "no permission" errors even though permissions were set to `default: true`

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
