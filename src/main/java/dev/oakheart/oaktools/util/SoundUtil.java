package dev.oakheart.oaktools.util;

import dev.oakheart.oaktools.OakTools;
import org.bukkit.SoundCategory;
import org.bukkit.SoundGroup;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Shared utility for playing vanilla-accurate block placement sounds.
 */
public class SoundUtil {

    /**
     * Play the vanilla block placement sound for the given block data.
     * Uses string-based playSound to bypass Paper's Sound enum registry mapping issues.
     */
    @SuppressWarnings("deprecation")
    public static void playPlaceSound(Block block, BlockData blockData, OakTools plugin) {
        try {
            SoundGroup soundGroup = blockData.getSoundGroup();
            org.bukkit.Sound bukkitSound = soundGroup.getPlaceSound();
            float volume = (soundGroup.getVolume() + 1.0f) / 2.0f;
            float pitch = soundGroup.getPitch() * 0.8f;
            String soundName = ((net.kyori.adventure.key.Keyed) bukkitSound).key().asString();
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
                    soundName, SoundCategory.BLOCKS, volume, pitch);
        } catch (Exception e) {
            plugin.debug("[Sound Debug] Error playing sound: " + e.getMessage());
        }
    }
}
