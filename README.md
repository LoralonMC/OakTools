# OakTools

**Player-friendly building utilities for survival Minecraft servers**

OakTools adds two specialized tools designed to enhance the building experience in survival mode without breaking game balance. Edit block states on the fly with the File, or speed up repetitive building tasks with the Trowel.

---

## Features

### 🔧 File Tool
A precision tool for editing block states without breaking and replacing blocks. Perfect for fixing fence connections, rotating logs, adjusting stair shapes, and more.

**Supported Block Types:**
- **Fences, Glass Panes, Iron Bars** - Toggle individual connections
- **Walls** - Adjust wall heights and connections
- **Stairs** - Change shape (straight, inner/outer corner) and half (top/bottom)
- **Directional Blocks** - Rotate observers, pistons, dispensers, droppers, etc.
- **Logs & Pillars** - Cycle through X/Y/Z axis orientations
- **Slabs** - Toggle between top and bottom placement

**Features:**
- Intelligent cursor detection for precise editing
- Consumes durability per use
- Supports Mending and Unbreaking enchantments
- Repairable in anvils with configurable materials
- Protection plugin compatible (WorldGuard, GriefPrevention, etc.)
- CoreProtect logging for rollbacks

### 🧱 Trowel Tool
A smart building tool that randomly selects and places blocks from your inventory. Great for creating natural-looking textures and speeding up repetitive building.

**Features:**
- **Feed Source System** - Choose which inventory row to pull blocks from (Hotbar, Row 1, Row 2, Row 3)
- **Random Selection** - Automatically picks a random valid block from your chosen inventory row
- **Smart Block Filtering** - Only uses placeable single-block items (excludes doors, beds, signs, etc.)
- **Shift + Right-Click** - Cycle through feed sources
- **Right-Click Block** - Place a random block from the current feed source
- Block consumption in survival mode (configurable per gamemode)
- Protection plugin integration
- CoreProtect logging

### ⚙️ Configurable Everything
- **Custom Models** - Support for Vanilla CustomModelData, Nexo, and ItemsAdder
- **Durability Settings** - Customize max durability and repair amounts
- **Crafting Recipes** - Fully configurable shaped recipes
- **Messages** - All messages support MiniMessage formatting with placeholders
- **Permissions** - Granular control over crafting, usage, and commands
- **Gamemode Restrictions** - Configure behavior for Creative, Adventure, and Spectator modes
- **World Whitelist/Blacklist** - Control which worlds tools can be used in
- **Enchantments** - Choose which enchantments are allowed per tool

### 🛡️ Server Integration
- **Protection Plugins** - Seamless integration with WorldGuard, GriefPrevention, Towny, and other protection plugins
- **CoreProtect** - Full logging support for all block edits and placements
- **bStats** - Anonymous usage statistics (can be disabled)
- **Performance** - Optimized for production servers with minimal overhead

---

## Installation

1. **Requirements:**
   - Paper/Spigot 1.21+ (or compatible fork)
   - Java 21+

2. **Optional Dependencies:**
   - [Nexo](https://nexomc.com/) - For custom item models via Nexo
   - [ItemsAdder](https://www.spigotmc.org/resources/itemsadder.73355/) - For custom item models via ItemsAdder
   - [CoreProtect](https://www.spigotmc.org/resources/coreprotect.8631/) - For block edit/placement logging
   - Protection plugins (WorldGuard, GriefPrevention, etc.) - For build permission checks

3. **Installation Steps:**
   - Download the latest `OakTools-X.X.X.jar` from [Releases](../../releases)
   - Place the JAR file in your server's `plugins/` folder
   - Restart your server
   - Configure `plugins/OakTools/config.yml` to your liking
   - Reload with `/oaktools reload` or restart the server

---

## Commands

All commands support tab completion for convenience.

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/oaktools` | Show command help | `oaktools.admin` | OP |
| `/oaktools give <player> <tool> [durability]` | Give a tool to a player | `oaktools.give` | OP |
| `/oaktools reload` | Reload configuration and recipes | `oaktools.reload` | OP |
| `/oaktools info [player]` | View tool information | `oaktools.info` | OP |
| `/oaktools repair [player]` | Repair a tool to full durability | `oaktools.repair` | OP |

**Aliases:** `/otools`, `/ot`

---

## Permissions

### Admin Permissions
| Permission | Description | Default |
|------------|-------------|---------|
| `oaktools.admin` | Access to all admin commands | OP |
| `oaktools.give` | Can use `/oaktools give` | OP |
| `oaktools.reload` | Can reload configuration | OP |
| `oaktools.info` | Can view tool information | OP |
| `oaktools.repair` | Can repair tools via command | OP |

### User Permissions
| Permission | Description | Default |
|------------|-------------|---------|
| `oaktools.use.file` | Can use the File tool | True |
| `oaktools.use.trowel` | Can use the Trowel tool | True |
| `oaktools.craft.file` | Can craft the File tool | True |
| `oaktools.craft.trowel` | Can craft the Trowel tool | True |

### Special Permissions
| Permission | Description | Default |
|------------|-------------|---------|
| `oaktools.bypass.protection` | Can use tools in protected regions | False |

---

## Configuration

The plugin features extensive configuration options. Here's a quick overview of key sections:

### Tool Configuration
Each tool (File, Trowel) has its own configuration section:

```yaml
tools:
  file:
    enabled: true

    # Item appearance
    base_material: WARPED_FUNGUS_ON_A_STICK
    model_id: "nexo:file"  # or integer for vanilla CustomModelData

    # Display (MiniMessage format)
    display:
      enabled: true
      name: "<white>File</white>"
      lore: []

    # Durability
    durability:
      max: 250
      use_vanilla_damage_bar: true
      repair_material: IRON_INGOT
      repair_amount: 63

    # Enchantments
    allowed_enchantments:
      - UNBREAKING
      - MENDING

    # Crafting recipe
    recipe:
      enabled: true
      category: EQUIPMENT
      shape:
        - "  I"
        - " I "
        - "S  "
      ingredients:
        I: IRON_INGOT
        S: STICK

    # Features (File only)
    features:
      multiple_facing: true  # Fences, glass panes
      walls: true
      stairs: true
      directional: true
      axis_rotation: true
      slabs: true
```

### Model Providers
Choose how to apply custom models:

```yaml
# Vanilla CustomModelData (integer)
model_id: 1001

# Nexo (requires Nexo plugin)
model_id: "nexo:file"

# ItemsAdder (requires ItemsAdder plugin)
model_id: "itemsadder:oaktools:file"
```

### Messages
All messages support MiniMessage formatting:

```yaml
messages:
  protection_denied:
    enabled: true
    delivery:
      - actionbar
    content: "<red>You cannot build here</red>"

  commands:
    give:
      success_sender: "<green>Gave %tool% to %player%</green>"
      success_target: "<green>You received a %tool%!</green>"
```

### Gamemode Restrictions

```yaml
general:
  restrictions:
    gamemode:
      creative:
        allow_use: true
        consume_blocks: false
        consume_durability: true
      adventure:
        allow_use: false
      spectator:
        allow_use: false
```

### World Restrictions

```yaml
general:
  restrictions:
    worlds:
      mode: WHITELIST  # or BLACKLIST
      list:
        - world
        - world_nether
        - world_the_end
```

For full configuration details, see the generated `config.yml` file.

---

## Crafting Recipes

### File
```
   [Iron Ingot]
 [Iron Ingot]
[Stick]
```

### Trowel
```
   [Iron Ingot]
 [Iron Ingot] [Iron Ingot]
[Stick]
```

Both recipes are fully configurable in `config.yml`.

---

## How It Works

### File Tool Usage
1. Hold the File tool
2. Right-click on a supported block type
3. The block state will change based on:
   - Block type (stairs, fences, logs, etc.)
   - Where you clicked (for precise control)
   - Whether you're sneaking (for stairs half toggle)

**Examples:**
- Right-click a fence side to toggle that connection
- Right-click a stairs to change its shape
- Shift + Right-click stairs to toggle top/bottom half
- Right-click a log to rotate its axis

### Trowel Tool Usage
1. Fill an inventory row with different block types
2. Hold the Trowel tool
3. Shift + Right-click to select which inventory row to use (Hotbar, Row 1, Row 2, Row 3)
4. Right-click on a block to place a random block from your selected row
5. The tool consumes one block from your inventory per placement

**Pro Tips:**
- Mix different block types in one row for varied textures (e.g., stone, andesite, cobblestone)
- Use Hotbar for quick access to your most common blocks
- Switch to Row 1-3 for specialized palettes

### Anvil Repair
Tools can be repaired in anvils using:
- **Repair Material** - Configurable per tool (default: Iron Ingot)
- **Tool Combining** - Combine two tools to merge durability + 5% bonus
- **Enchanted Books** - Apply allowed enchantments (Mending, Unbreaking by default)

The plugin automatically:
- Calculates exact material consumption
- Refunds excess materials
- Blocks disallowed enchantments
- Preserves custom names

---

## Integration

### Protection Plugins
OakTools uses fake `BlockPlaceEvent` to check building permissions. This works automatically with:
- WorldGuard
- GriefPrevention
- Towny
- Lands
- Most other protection plugins

Players with `oaktools.bypass.protection` can bypass these checks.

### CoreProtect
When enabled, all File edits and Trowel placements are logged:
- **File edits**: Logged as block removal + placement for proper rollbacks
- **Trowel placements**: Logged as normal block placements

Enable in config:
```yaml
integration:
  coreprotect:
    enabled: true
    log_file_changes: true
    log_trowel_placements: true
```

### Custom Item Models
OakTools supports three methods for custom item models:

1. **Vanilla CustomModelData** (default)
2. **Nexo** - Full integration with Nexo's custom items
3. **ItemsAdder** - Full integration with ItemsAdder's custom items

When using Nexo or ItemsAdder, recipes are automatically delayed to ensure proper loading.

---

## For Developers

### Custom Events

OakTools fires custom Bukkit events that other plugins can listen to:

**FileUseEvent**
```java
@EventHandler
public void onFileUse(FileUseEvent event) {
    Player player = event.getPlayer();
    Block block = event.getBlock();
    BlockData oldData = event.getOldData();
    BlockData newData = event.getNewData();
    EditType editType = event.getEditType();

    // Cancel the edit if needed
    event.setCancelled(true);
}
```

**TrowelPlaceEvent**
```java
@EventHandler
public void onTrowelPlace(TrowelPlaceEvent event) {
    Player player = event.getPlayer();
    Block block = event.getBlock();
    ItemStack placedBlock = event.getPlacedBlock();
    FeedSource feedSource = event.getFeedSource();

    // Cancel the placement if needed
    event.setCancelled(true);
}
```

### API Access
Access OakTools services through the plugin instance:

```java
OakTools plugin = (OakTools) Bukkit.getPluginManager().getPlugin("OakTools");

// Check if an item is an OakTools tool
if (plugin.getItemFactory().isTool(itemStack)) {
    ToolType type = plugin.getItemFactory().getToolType(itemStack);
}

// Create a custom tool
ItemStack file = plugin.getItemFactory().createTool(ToolType.FILE);

// Manage durability
plugin.getDurabilityService().damage(itemStack, player, 1);
plugin.getDurabilityService().repair(itemStack, 50);
```

---

## Building from Source

```bash
# Clone the repository
git clone https://github.com/LoralonMC/OakTools.git
cd OakTools

# Build with Gradle
./gradlew clean build

# Output JAR will be in build/libs/
```

**Requirements:**
- JDK 21+
- Gradle 8.3+

---

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

Please ensure:
- Code follows existing style conventions
- Changes are tested on a Paper server
- Config changes are documented

---

## Support

- **Issues:** [GitHub Issues](../../issues)
- **Discussions:** [GitHub Discussions](../../discussions)

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- Built for [Paper](https://papermc.io/) servers
- Uses [MiniMessage](https://docs.adventure.kyori.net/minimessage/) for formatting
- Statistics powered by [bStats](https://bstats.org/)
- Special thanks to all contributors and testers!

---

**Made with ❤️ for the Minecraft building community**
