package dev.oakheart.oaktools.integration;

import dev.oakheart.oaktools.OakTools;
import me.frep.vulcan.api.check.Check;
import me.frep.vulcan.api.event.VulcanFlagEvent;
import me.frep.vulcan.api.event.VulcanPunishEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cancels Vulcan flags that were caused by OakTools' own protection probes.
 *
 * <p>Only loaded when Vulcan is installed — see {@link VulcanHook}.
 */
public class VulcanFlagListener implements Listener {

    /**
     * Checks driven by counting BlockPlaceEvent / BlockBreakEvent, which is
     * exactly what a protection probe looks like.
     *
     * <p>Stored in Vulcan's own normalised form: {@code Check#getName()} takes
     * the {@code @CheckInfo} name ("Fast Place") and returns it lowercased with
     * spaces stripped, while {@code getDisplayName()} returns it raw. Both are
     * normalised before comparison so either spelling matches.
     */
    private static final Set<String> PROBE_SENSITIVE_CHECKS = Set.of("fastplace", "fastbreak");

    /**
     * How long a suppressed flag keeps covering a matching punishment. Vulcan
     * applies punishments on a delay (`punishment-delay`), so the punishment
     * for a flag we just absorbed can arrive seconds later.
     */
    private static final long PUNISH_COVER_MILLIS = 30_000L;

    private final OakTools plugin;
    private final Map<UUID, Long> lastSuppressedAt = new HashMap<>();

    public VulcanFlagListener(OakTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlag(VulcanFlagEvent event) {
        Player player = event.getPlayer();
        if (!isProbeInduced(player, event.getCheck())) {
            return;
        }

        event.setCancelled(true);
        lastSuppressedAt.put(player.getUniqueId(), System.currentTimeMillis());
        // One probe burst excuses one flag. Further wand use re-marks it.
        plugin.getProtectionService().clearProbeMarker(player);

        plugin.debug("[Vulcan Debug] Suppressed " + describe(event.getCheck())
                + " flag for " + player.getName() + " (OakTools protection probe)");
    }

    /**
     * Belt and braces: if a future Vulcan build increments violations before
     * firing the cancellable flag event, the punishment is still blocked.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPunish(VulcanPunishEvent event) {
        Player player = event.getPlayer();
        Check check = event.getCheck();

        if (!isProbeSensitive(check)) {
            return;
        }

        Long suppressed = lastSuppressedAt.get(player.getUniqueId());
        boolean coveredBySuppressedFlag =
                suppressed != null && System.currentTimeMillis() - suppressed <= PUNISH_COVER_MILLIS;

        if (!coveredBySuppressedFlag && !isProbeInduced(player, check)) {
            return;
        }

        event.setCancelled(true);
        plugin.getLogger().info("Blocked Vulcan " + describe(check) + " punishment for "
                + player.getName() + " — caused by an OakTools protection probe, not the player.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastSuppressedAt.remove(event.getPlayer().getUniqueId());
    }

    private boolean isProbeInduced(Player player, Check check) {
        if (check == null) {
            return false;
        }

        // The flag usually fires synchronously inside our own probe dispatch.
        // The marker covers the case where Vulcan's bucket instead closes on
        // the player's next genuine block event, after the burst is over.
        boolean probing = plugin.getProtectionService().isFiringProbe()
                || plugin.getProtectionService().probedWithin(
                        player, plugin.getConfigManager().getVulcanProbeGraceMillis());

        if (!probing) {
            return false;
        }

        if (!isProbeSensitive(check)) {
            // Not a false positive we know how to attribute, so it stands. Logged
            // because a check name that stops matching is otherwise silent: the
            // integration would look healthy while suppressing nothing.
            plugin.debug("[Vulcan Debug] " + describe(check) + " flagged for " + player.getName()
                    + " during an OakTools probe, but is not a probe-sensitive check — leaving it alone");
            return false;
        }

        return true;
    }

    /**
     * Vulcan exposes the check name in two spellings ("fastplace" from
     * {@code getName()}, "Fast Place" from {@code getDisplayName()}), so
     * normalise before comparing rather than trusting either.
     */
    private static boolean isProbeSensitive(Check check) {
        return check != null
                && (matchesProbeSensitive(check.getName()) || matchesProbeSensitive(check.getDisplayName()));
    }

    private static boolean matchesProbeSensitive(String name) {
        if (name == null) {
            return false;
        }
        return PROBE_SENSITIVE_CHECKS.contains(name.toLowerCase(Locale.ROOT).replace(" ", ""));
    }

    private String describe(Check check) {
        String name = check.getDisplayName() != null ? check.getDisplayName() : check.getName();
        return name + " (Type " + check.getDisplayType() + ")";
    }
}
