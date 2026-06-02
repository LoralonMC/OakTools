# OakTools

Player-friendly building utilities for survival Minecraft servers. Three specialized tools designed to enhance the building experience without breaking game balance.

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
- Configurable max blocks per use (default: 64)
- Consumes matching blocks from inventory (configurable per gamemode)

### Shared Features
- Custom durability system with configurable max durability per tool
- Anvil repair with configurable materials, tool combining with durability bonus
- Mending and Unbreaking enchantment support
- Configurable crafting recipes
- Custom model support (Vanilla CustomModelData, Item Model API, Nexo, ItemsAdder)
- Protection plugin integration (WorldGuard, GriefPrevention, Towny, etc.)
- CoreProtect logging for all edits and placements
- MiniMessage formatting for all messages
- Gamemode and world restrictions
- Recipe auto-discovery when ingredients are collected

## Requirements

- Paper 26.1.2+ (or compatible fork)
- Java 25+

## Installation

1. Download the latest `OakTools-X.X.X.jar` from [Releases](../../releases)
2. Place the JAR in your server's `plugins/` folder
3. Restart your server
4. Edit `plugins/OakTools/config.yml` to your liking
5. Reload with `/oaktools reload`

**Optional dependencies:** CoreProtect (block logging), Nexo or ItemsAdder (custom models), any protection plugin (build permission checks)

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/oaktools help` | Show command help | — |
| `/oaktools give <player> <tool> [durability]` | Give a tool to a player | `oaktools.give` |
| `/oaktools reload` | Reload configuration and recipes | `oaktools.reload` |
| `/oaktools info [player]` | View held tool information | `oaktools.info` |
| `/oaktools repair [player]` | Repair held tool to full durability | `oaktools.repair` |

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
| `oaktools.craft.file` | Craft the File tool | true |
| `oaktools.craft.trowel` | Craft the Trowel tool | true |
| `oaktools.craft.wand` | Craft the Builder's Wand tool | true |
| `oaktools.repair.anvil` | Repair tools in an anvil | true |
| `oaktools.bypass.protection` | Use tools in protected regions | false |

## Configuration

Key settings in `config.yml`:

- **Tool sections** (`tools.file`, `tools.trowel`, `tools.wand`) — Enable/disable, base material, model ID, durability, enchantments, recipe, display name/lore
- **General restrictions** — Gamemode behavior (allow use, consume blocks/durability), world whitelist/blacklist
- **Integration** — CoreProtect logging toggles per tool
- **Messages** — All player-facing messages in MiniMessage format with configurable delivery (chat, action bar, title)

Each tool's crafting recipe is fully configurable (shape, ingredients, category). See the generated `config.yml` for full details and documentation.
