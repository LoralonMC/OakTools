package dev.oakheart.oaktools.listeners;

import dev.oakheart.oaktools.OakTools;
import dev.oakheart.oaktools.model.ToolType;
import dev.oakheart.oaktools.util.Constants;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Set;

public class AnvilListener implements Listener {

    private final OakTools plugin;

    public AnvilListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilResultFilter(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null) {
            return;
        }

        if (!plugin.getItemFactory().isTool(result)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(result);
        if (toolType == null) {
            return;
        }

        // Sickles use vanilla anvil behavior — skip custom enchant/repair filtering
        if (toolType == ToolType.SICKLE) {
            return;
        }

        HumanEntity viewer = event.getViewers().isEmpty() ? null : event.getViewers().getFirst();
        if (viewer != null && !viewer.hasPermission("oaktools.repair.anvil")) {
            event.setResult(null);
            return;
        }

        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return;
        }

        Map<Enchantment, Integer> enchants = meta.getEnchants();
        if (!enchants.isEmpty()) {
            Set<Enchantment> allowedEnchants = plugin.getConfigManager().getAllowedEnchantments(toolType);

            for (Enchantment enchant : enchants.keySet()) {
                if (!allowedEnchants.contains(enchant)) {
                    event.setResult(null);
                    plugin.debug("[Anvil Debug] Blocked result with disallowed enchantment: " + enchant.getKey());
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack first = inventory.getItem(0);
        ItemStack second = inventory.getItem(1);

        if (first == null || second == null) {
            return;
        }

        if (!plugin.getItemFactory().isTool(first)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(first);
        if (toolType == null) {
            return;
        }

        // Sickles use vanilla anvil behavior
        if (toolType == ToolType.SICKLE) {
            return;
        }

        HumanEntity viewer = event.getViewers().isEmpty() ? null : event.getViewers().getFirst();
        if (viewer != null && !viewer.hasPermission("oaktools.repair.anvil")) {
            return;
        }

        Material repairMaterial = plugin.getConfigManager().getRepairMaterial(toolType);
        if (repairMaterial == null) {
            return;
        }

        if (second.getType() == repairMaterial) {
            plugin.debug("[Anvil Debug] Repairing " + toolType + " with " + repairMaterial);
            handleMaterialRepair(event, first, second, toolType);
        } else if (plugin.getItemFactory().isTool(second) &&
                 plugin.getItemFactory().getToolType(second) == toolType) {
            plugin.debug("[Anvil Debug] Combining two " + toolType + " tools");
            handleToolCombine(event, first, second, toolType);
        }
    }

    private void handleMaterialRepair(PrepareAnvilEvent event, ItemStack tool, ItemStack material, ToolType toolType) {
        int repairPerItem = plugin.getConfigManager().getRepairAmount(toolType);

        int currentDamage = plugin.getDurabilityService().getCurrentDamage(tool);
        int maxDurability = plugin.getDurabilityService().getMaxDurability(tool);

        if (currentDamage <= 0) {
            plugin.debug("[Anvil Debug] Tool already at full durability, no repair needed");
            return;
        }

        int itemsUsed = Math.min(material.getAmount(), (int) Math.ceil((double) currentDamage / repairPerItem));
        int totalRepair = itemsUsed * repairPerItem;

        plugin.debug("[Anvil Debug] Current damage: " + currentDamage + "/" + maxDurability);
        plugin.debug("[Anvil Debug] Using " + itemsUsed + " x " + material.getType() +
            " to repair " + totalRepair + " durability");

        ItemStack result = tool.clone();
        plugin.getDurabilityService().repair(result, totalRepair);

        plugin.getDisplayService().updateDisplay(result);
        applyAnvilRename(event, tool, result);

        event.setResult(result);

        AnvilView anvilView = (AnvilView) event.getView();
        int repairCost = itemsUsed;
        if (hasAnvilRename(event, tool)) repairCost++;
        anvilView.setRepairCost(repairCost);

        plugin.debug("[Anvil Debug] Final damage: " + plugin.getDurabilityService().getCurrentDamage(result) +
            "/" + maxDurability + " (repair cost: " + repairCost + " levels)");
    }

    private void handleToolCombine(PrepareAnvilEvent event, ItemStack first, ItemStack second, ToolType toolType) {
        int firstDamage = plugin.getDurabilityService().getCurrentDamage(first);
        int secondDamage = plugin.getDurabilityService().getCurrentDamage(second);
        int firstMax = plugin.getDurabilityService().getMaxDurability(first);
        int secondMax = plugin.getDurabilityService().getMaxDurability(second);

        plugin.debug("[Anvil Debug] First tool: " + firstDamage + "/" + firstMax +
            " (remaining: " + (firstMax - firstDamage) + ")");
        plugin.debug("[Anvil Debug] Second tool: " + secondDamage + "/" + secondMax +
            " (remaining: " + (secondMax - secondDamage) + ")");

        int firstRemaining = firstMax - firstDamage;
        int secondRemaining = secondMax - secondDamage;
        int combinedRemaining = firstRemaining + secondRemaining + (int) (firstMax * 0.05);

        int resultMax = Math.max(firstMax, secondMax);
        int resultDamage = Math.max(0, resultMax - combinedRemaining);

        plugin.debug("[Anvil Debug] Combined remaining: " + combinedRemaining +
            " (with 5% bonus: " + (int) (firstMax * 0.05) + ")");
        plugin.debug("[Anvil Debug] Result: " + resultDamage + "/" + resultMax);

        // clone() copies all PDC data from the first tool
        ItemStack result = first.clone();
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) {
            return;
        }

        PersistentDataContainer resultPDC = resultMeta.getPersistentDataContainer();

        // Set combined durability values
        resultPDC.set(Constants.DURABILITY, PersistentDataType.INTEGER, resultDamage);
        resultPDC.set(Constants.MAX_DURABILITY, PersistentDataType.INTEGER, resultMax);

        result.setItemMeta(resultMeta);

        // Merge enchantments from the second tool
        int enchantCost = mergeEnchantments(result, second, toolType);

        plugin.getItemFactory().syncVanillaDurability(result);
        plugin.getDisplayService().updateDisplay(result);
        applyAnvilRename(event, first, result);

        event.setResult(result);

        // Base cost 2 (vanilla combine) + enchantment merge cost + rename bonus
        AnvilView anvilView = (AnvilView) event.getView();
        int repairCost = 2 + enchantCost;
        if (hasAnvilRename(event, first)) repairCost++;
        anvilView.setRepairCost(repairCost);
    }

    private int mergeEnchantments(ItemStack result, ItemStack second, ToolType toolType) {
        Set<Enchantment> allowedEnchants = plugin.getConfigManager().getAllowedEnchantments(toolType);

        ItemMeta resultMeta = result.getItemMeta();
        ItemMeta secondMeta = second.getItemMeta();
        if (resultMeta == null || secondMeta == null) {
            return 0;
        }

        Map<Enchantment, Integer> secondEnchants = secondMeta.getEnchants();
        int enchantCost = 0;

        for (Map.Entry<Enchantment, Integer> entry : secondEnchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            int secondLevel = entry.getValue();

            // Only merge enchantments on the allowed list
            if (!allowedEnchants.contains(enchant)) {
                continue;
            }

            // Check for conflicts with existing enchantments
            boolean conflicts = false;
            for (Enchantment existing : resultMeta.getEnchants().keySet()) {
                if (!existing.equals(enchant) && enchant.conflictsWith(existing)) {
                    conflicts = true;
                    break;
                }
            }
            if (conflicts) {
                plugin.debug("[Anvil Debug] Skipping conflicting enchantment: " + enchant.getKey());
                continue;
            }

            int currentLevel = resultMeta.getEnchantLevel(enchant);
            int newLevel;

            if (currentLevel == secondLevel) {
                // Same level → level + 1, capped at max
                newLevel = Math.min(currentLevel + 1, enchant.getMaxLevel());
            } else if (currentLevel > secondLevel) {
                // First tool has higher level → keep it
                continue;
            } else {
                // Second tool has higher level → take it
                newLevel = secondLevel;
            }

            resultMeta.addEnchant(enchant, newLevel, true);
            enchantCost++;
            plugin.debug("[Anvil Debug] Merged enchantment: " + enchant.getKey() +
                " (level " + currentLevel + " + " + secondLevel + " → " + newLevel + ")");
        }

        result.setItemMeta(resultMeta);
        return enchantCost;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }

        if (event.getRawSlot() != 2) {
            return;
        }

        ItemStack result = anvil.getItem(2);
        ItemStack first = anvil.getItem(0);
        ItemStack second = anvil.getItem(1);

        if (result == null || first == null || second == null) {
            return;
        }

        if (!plugin.getItemFactory().isTool(first)) {
            return;
        }

        ToolType toolType = plugin.getItemFactory().getToolType(first);
        if (toolType == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!player.hasPermission("oaktools.repair.anvil")) {
            return;
        }

        if (plugin.getItemFactory().isTool(second)) {
            return;
        }

        Material repairMaterial = plugin.getConfigManager().getRepairMaterial(toolType);
        if (repairMaterial == null) {
            return;
        }

        if (second.getType() != repairMaterial) {
            return;
        }

        int repairPerItem = plugin.getConfigManager().getRepairAmount(toolType);
        int currentDamage = plugin.getDurabilityService().getCurrentDamage(first);
        int itemsNeeded = Math.min(second.getAmount(), (int) Math.ceil((double) currentDamage / repairPerItem));

        if (itemsNeeded <= 0) {
            return;
        }

        int itemsToRefund = second.getAmount() - itemsNeeded;

        if (itemsToRefund > 0) {
            // Clicking the result slot does NOT mean the take succeeded: vanilla
            // rejects it for insufficient XP levels, an occupied cursor, or a
            // full inventory on shift-click — and this event still fires.
            // Refunding unconditionally mints free repair material on every
            // failed click. Precheck the predictable failures, then confirm
            // next tick that the anvil actually consumed the inputs and (for
            // survival players) charged the XP cost.
            AnvilView view = (AnvilView) event.getView();
            boolean creative = player.getGameMode() == GameMode.CREATIVE;
            if (!creative && player.getLevel() < view.getRepairCost()) {
                return;
            }
            if (!event.getCursor().isEmpty() && !event.getClick().isShiftClick()) {
                return;
            }

            int levelBefore = player.getLevel();

            plugin.debug("[Anvil Debug] Refunding " + itemsToRefund + " x " + repairMaterial +
                " (only needed " + itemsNeeded + " out of " + second.getAmount() + ")");

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!isEmptySlot(anvil.getItem(0)) || !isEmptySlot(anvil.getItem(1))) {
                    plugin.debug("[Anvil Debug] Refund skipped: anvil inputs not consumed (take failed)");
                    return;
                }
                // Closing the anvil returns leftover inputs to the player, so a
                // failed take followed by an instant close must not also refund.
                if (player.getOpenInventory().getTopInventory() != anvil) {
                    plugin.debug("[Anvil Debug] Refund skipped: anvil view no longer open");
                    return;
                }
                if (!creative && player.getLevel() >= levelBefore) {
                    plugin.debug("[Anvil Debug] Refund skipped: no XP cost charged (take failed)");
                    return;
                }

                ItemStack refund = new ItemStack(repairMaterial, itemsToRefund);
                player.getInventory().addItem(refund).forEach((index, leftover) -> {
                    player.getWorld().dropItem(player.getLocation(), leftover);
                });

                plugin.debug("[Anvil Debug] Refunded " + itemsToRefund + " x " + repairMaterial + " to player");
            });
        }
    }

    private static boolean isEmptySlot(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private boolean hasAnvilRename(PrepareAnvilEvent event, ItemStack originalTool) {
        String renameText = ((AnvilView) event.getView()).getRenameText();
        if (renameText == null || renameText.isEmpty()) {
            return false;
        }
        // OakTools items carry their styled name in itemName(), not customName().
        // The client pre-fills the anvil text box with the displayed name, so
        // comparing against customName() alone makes every ordinary repair look
        // like a rename and silently replaces the styled name with plain text.
        ItemMeta meta = originalTool.getItemMeta();
        Component currentName = null;
        if (meta != null) {
            if (meta.customName() != null) {
                currentName = meta.customName();
            } else if (meta.hasItemName()) {
                currentName = meta.itemName();
            }
        }
        if (currentName == null) {
            return !renameText.isEmpty();
        }
        String currentPlainName = PlainTextComponentSerializer.plainText().serialize(currentName);
        return !renameText.equals(currentPlainName);
    }

    private void applyAnvilRename(PrepareAnvilEvent event, ItemStack originalTool, ItemStack result) {
        if (!hasAnvilRename(event, originalTool)) {
            return;
        }
        String renameText = ((AnvilView) event.getView()).getRenameText();
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta != null) {
            resultMeta.customName(Component.text(renameText)
                    .decoration(TextDecoration.ITALIC, false));
            result.setItemMeta(resultMeta);
            plugin.debug("[Anvil Debug] Applied rename: " + renameText);
        }
    }

}
