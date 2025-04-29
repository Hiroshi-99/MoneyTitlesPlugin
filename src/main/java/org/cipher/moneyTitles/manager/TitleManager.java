package org.cipher.moneyTitles.manager;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.cipher.moneyTitles.config.ConfigManager;
import org.cipher.moneyTitles.util.MoneyFormatter;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the display of titles and sounds to players.
 * Optimized for performance and thread safety.
 */
public class TitleManager {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final MoneyFormatter moneyFormatter;

    /**
     * Creates a new TitleManager.
     *
     * @param plugin         Plugin instance
     * @param configManager  Configuration manager
     * @param moneyFormatter Money formatter
     */
    public TitleManager(JavaPlugin plugin, ConfigManager configManager, MoneyFormatter moneyFormatter) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.moneyFormatter = moneyFormatter;
    }

    /**
     * Sends a kill notification to a player.
     * Optimized to run on the main thread while minimizing impact.
     *
     * @param killer       The killer player
     * @param victim       The victim player
     * @param amountGained Amount of money gained
     */
    public void sendKillNotification(Player killer, Player victim, double amountGained) {
        if (killer == null || !killer.isOnline() || amountGained <= 0) {
            return;
        }

        // Format the amount and prepare the messages
        String formattedAmount = moneyFormatter.formatMoney(amountGained);
        String title = configManager.getKillTitle().replace("%amount%", formattedAmount);
        String subtitle = configManager.getKillSubtitle().replace("%victim%", victim.getName());

        // Execute on the main thread to ensure thread safety
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Send the title
                sendTitle(killer, title, subtitle);

                // Play the sound if enabled
                if (configManager.isKillSoundEnabled()) {
                    playSound(killer,
                            configManager.getKillSound(),
                            configManager.getKillSoundVolume(),
                            configManager.getKillSoundPitch());
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error sending kill notification to " + killer.getName(), e);
            }
        });
    }

    /**
     * Sends a death notification to a player.
     * Optimized to run on the main thread while minimizing impact.
     *
     * @param victim     The victim player
     * @param killer     The killer player
     * @param amountLost Amount of money lost
     */
    public void sendDeathNotification(Player victim, Player killer, double amountLost) {
        if (victim == null || !victim.isOnline() || amountLost <= 0) {
            return;
        }

        // Format the amount and prepare the messages
        String formattedAmount = moneyFormatter.formatMoney(amountLost);
        String title = configManager.getDeathTitle().replace("%amount%", formattedAmount);
        String subtitle = configManager.getDeathSubtitle().replace("%killer%", killer.getName());

        // Execute on the main thread to ensure thread safety
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Send the title
                sendTitle(victim, title, subtitle);

                // Play the sound if enabled
                if (configManager.isDeathSoundEnabled()) {
                    playSound(victim,
                            configManager.getDeathSound(),
                            configManager.getDeathSoundVolume(),
                            configManager.getDeathSoundPitch());
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error sending death notification to " + victim.getName(), e);
            }
        });
    }

    /**
     * Helper method to send a title to a player.
     *
     * @param player   Player to send title to
     * @param title    Title text
     * @param subtitle Subtitle text
     */
    private void sendTitle(Player player, String title, String subtitle) {
        try {
            player.sendTitle(
                    title,
                    subtitle,
                    configManager.getTitleFadeIn(),
                    configManager.getTitleStay(),
                    configManager.getTitleFadeOut());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to send title to " + player.getName(), e);
        }
    }

    /**
     * Helper method to play a sound to a player.
     *
     * @param player Player to play sound for
     * @param sound  Sound to play
     * @param volume Volume of the sound
     * @param pitch  Pitch of the sound
     */
    private void playSound(Player player, Sound sound, float volume, float pitch) {
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to play sound for " + player.getName(), e);
        }
    }
}