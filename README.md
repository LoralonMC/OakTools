# OakTools

Player-friendly building and harvesting utilities for survival Minecraft servers. Seven specialized tools designed to enhance the survival experience without breaking game balance.

## Features

### File Tool
A precision tool for editing block states without breaking and replacing blocks.

- **Fences, Glass Panes, Iron Bars** — Toggle individual connections
- **Walls** — Cycle wall heights (none, low, tall)
- **Stairs** — Change shape (straight, inner/outer corner) and half (top/bottom)
- **Directional Blocks** — Rotate observers, pistons, dispensers, etc.
- **Logs & Pillars** — Cycle through X/Y/Z axis orientations
- **Slabs** — Toggle between top and bottom placement
- Intelligent cursor detection for precise editing
- Extended (powered) pistons are protected from rotation by default

### Trowel Tool
A smart building tool that randomly selects and places blocks from your inventory. Great for creating natural-looking textures.

- **Feed Source System** — Choose which inventory row to pull blocks from (Hotbar, Row 1-3)
- Randomly picks a placeable block from the selected row
- **Shift + Right-Click** to cycle feed sources
- Filters out multi-block items, tile entities, and custom plugin items

### Builder's Wand
An extension tool that places copies of existing blocks in bulk. Two placement modes:

- **Face Mode** (default) — Flood-fill the clicked face. Finds all connected same-type blocks on that surface and extends them one layer out.
- **Line Mode** — Extend a single line in the player's look direction. Quick precision work.
- **Shift + Right-Click** to cycle between modes
- Wireframe preview of the pending placement (requires PacketEvents)
- Undo support with configurable history and expiry
- Configurable max blocks per use (default: 64)
- Consumes matching blocks from inventory (configurable per gamemode)

### Excavation Shovel
Mines an area of shovel-mineable blocks (dirt, sand, gravel, etc.) on the clicked face.

- Configurable grid size (3x3 by default)
- Animated one-by-one breaking with crack animation, particles, and sounds

### Lumberjack's Axe
Chops entire trees from a single log break.

- Natural-tree validation (minimum leaf count) so log builds are never eaten
- Tracks player-placed logs and leaves them alone
- Configurable max blocks per tree

### Vein Miner Pickaxe
Mines a whole connected ore vein at once.

- Deepslate variants count as the same vein (e.g. iron ore + deepslate iron ore)
- Configurable max blocks per vein

**Shared by the three harvesting tools above:** they activate on block break (sneak while breaking to mine a single block normally), drops go straight to your inventory (overflowing to OakOverflow if installed, the ground as a last resort), and each supports mining-speed and harvest-level overrides, optional unbreakable mode, and lockable enchantments.

### Sickle
A tiered crop-harvesting tool (wooden through netherite) built on the vanilla hoe — vanilla durability, enchanting, and anvil repair, fully craftable.

- Harvests all mature crops in a radius around the broken one (radius grows with tier; sneak to harvest a single crop)
- **Auto-replant** — consumes one seed from the harvest drops, falling back to seeds in your inventory when the harvest rolls none
- **Harvest-only** — won't break immature crops, and won't till soil into farmland (both configurable)
- Supports wheat, carrots, potatoes, beetroot, nether wart, cocoa, sweet berries, torchflower, and pitcher crops
- Also clears grass, ferns, and flowers in the same radius

### Shared Features
- Custom durability system with configurable max durability per tool (sickles use vanilla durability)
- Anvil repair with configurable materials, tool combining with durability bonus
- Mending and Unbreaking enchantment support
- Configurable crafting recipes with auto-discovery when ingredients are collected
- Custom model support (Vanilla CustomModelData, Item Model API, Nexo, ItemsAdder)
- Protection plugin integration (WorldGuard, GriefPrevention, Towny, etc.)
- CoreProtect logging for all edits, placements, and harvests
- MiniMessage formatting for all messages
- Gamemode and world restrictions

## Requirements

- Paper 26.1.2+ (or compatible fork)
- Java 25+

## Installation

1. Download the latest `OakTools-X.X.X.jar` from [Releases](../../releases)
2. Place the JAR in your server's `plugins/` folder
3. Restart your server
4. Edit `plugins/OakTools/config.yml` to your liking
5. Reload with `/oaktools reload`

**Optional dependencies:** CoreProtect (block logging), PacketEvents (wand preview, break animations), OakOverflow (inventory overflow), Nexo or ItemsAdder (custom models), any protection plugin (build permission checks)

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/oaktools help` | Show command help | — |
| `/oaktools give <player> <tool> [durability] [silent]` | Give a tool to a player | `oaktools.give` |
| `/oaktools reload` | Reload configuration and recipes | `oaktools.reload` |
| `/oaktools info [player]` | View held tool information | `oaktools.info` |
| `/oaktools repair [player]` | Repair held tool to full durability | `oaktools.repair` |

Tool names for `/oaktools give`: `file`, `trowel`, `wand`, `excavator`, `lumberjack`, `vein_miner`, and `sickle_<tier>` (e.g. `sickle_iron`).

Aliases: `/otools`, `/ot`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `oaktools.*` | All OakTools permissions | OP |
| `oaktools.give` | Give tools to players | OP |
| `oaktools.reload` | Reload configuration | OP |
| `oaktools.info` | View tool information | OP |
| `oaktools.repair` | Repair tools via command | OP |
| `oaktools.use.file` | Use the File tool | true |
| `oaktools.use.trowel` | Use the Trowel tool | true |
| `oaktools.use.wand` | Use the Builder's Wand tool | true |
| `oaktools.use.excavator` | Use the Excavation Shovel | true |
| `oaktools.use.lumberjack` | Use the Lumberjack's Axe | true |
| `oaktools.use.veinminer` | Use the Vein Miner Pickaxe | true |
| `oaktools.use.sickle` | Use the Sickle | true |
| `oaktools.craft.file` | Craft the File tool | true |
| `oaktools.craft.trowel` | Craft the Trowel tool | true |
| `oaktools.craft.wand` | Craft the Builder's Wand tool | true |
| `oaktools.craft.excavator` | Craft the Excavation Shovel | true |
| `oaktools.craft.lumberjack` | Craft the Lumberjack's Axe | true |
| `oaktools.craft.veinminer` | Craft the Vein Miner Pickaxe | true |
| `oaktools.craft.sickle` | Craft the Sickle | true |
| `oaktools.repair.anvil` | Repair tools in an anvil | true |
| `oaktools.bypass.protection` | Use tools in protected regions | false |

## Configuration

Key settings in `config.yml`:

- **Tool sections** (`tools.file`, `tools.trowel`, `tools.wand`, `tools.excavator`, `tools.lumberjack`, `tools.vein-miner`, `tools.sickle`) — Enable/disable, base material, model ID, durability, enchantments, recipe, display name/lore
- **Harvesting tools** — Grid size (excavator), max blocks and minimum leaves (lumberjack), max blocks and deepslate grouping (vein miner), break animation speed and progress messages
- **Sickle** — Per-tier harvest radius and recipe, replant-from-inventory fallback, tilling prevention, immature-crop protection, clearable vegetation list
- **General restrictions** — Gamemode behavior (allow use, consume blocks/durability), world whitelist/blacklist
- **Integration** — CoreProtect logging toggles per tool
- **Messages** — All player-facing messages in `messages.yml`, MiniMessage format with configurable delivery (chat, action bar)

Each tool's crafting recipe is fully configurable (shape, ingredients, category). See the generated `config.yml` for full details and documentation.
