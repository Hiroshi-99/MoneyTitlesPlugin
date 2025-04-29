package org.cipher.moneyTitles.config;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages configuration settings for MoneyTitles plugin.
 * Centralizes all config operations for better maintainability.
 */
public class ConfigManager {
    private final JavaPlugin plugin;
    private final Logger logger;

    // Title animation settings
    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;

    // Message templates
    private String killTitle;
    private String killSubtitle;
    private String deathTitle;
    private String deathSubtitle;

    // Sound settings
    private boolean killSoundEnabled;
    private Sound killSound;
    private float killSoundVolume;
    private float killSoundPitch;
    private boolean deathSoundEnabled;
    private Sound deathSound;
    private float deathSoundVolume;
    private float deathSoundPitch;

    // Format settings
    private int decimalPlaces;
    private final Map<String, Object> formatSettings = new HashMap<>();

    // Metrics settings
    private boolean metricsEnabled;

    // Balance tracker settings
    private long balanceTrackerInitialDelay;
    private long balanceTrackerInterval;

    // Stats settings
    private long statsSaveInterval;
    private boolean statsEnabled;
    private List<String> statsEnabledWorlds;
    private boolean statsAllWorldsEnabled;

    // Player interaction settings
    private boolean showPingOnCrosshair;
    private String pingFormat;
    private boolean statsGuiEnabled;
    private String statsGuiTitle;
    private Sound statsGuiOpenSound;
    private float statsGuiOpenSoundVolume;
    private float statsGuiOpenSoundPitch;

    /**
     * Creates a new ConfigManager.
     *
     * @param plugin Plugin instance
     */
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Loads or reloads configuration from disk.
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // Load title animation settings
        titleFadeIn = config.getInt("title.fade-in", 5);
        titleStay = config.getInt("title.stay", 40);
        titleFadeOut = config.getInt("title.fade-out", 5);

        // Load message templates
        killTitle = ChatColor.translateAlternateColorCodes('&',
                config.getString("messages.kill-title", "&a&l+%amount% $"));
        killSubtitle = ChatColor.translateAlternateColorCodes('&',
                config.getString("messages.kill-subtitle", "&f&lfor killing %victim%"));
        deathTitle = ChatColor.translateAlternateColorCodes('&',
                config.getString("messages.death-title", "&c&l-%amount% $"));
        deathSubtitle = ChatColor.translateAlternateColorCodes('&',
                config.getString("messages.death-subtitle", "&f&lKilled by %killer%"));

        // Load format settings
        decimalPlaces = config.getInt("format.decimal-places", 2);

        formatSettings.put("quadrillionEnabled", config.getBoolean("format.quadrillion.enabled", true));
        formatSettings.put("quadrillionSuffix", config.getString("format.quadrillion.suffix", "Q"));
        formatSettings.put("trillionEnabled", config.getBoolean("format.trillion.enabled", true));
        formatSettings.put("trillionSuffix", config.getString("format.trillion.suffix", "T"));
        formatSettings.put("billionEnabled", config.getBoolean("format.billion.enabled", true));
        formatSettings.put("billionSuffix", config.getString("format.billion.suffix", "B"));
        formatSettings.put("millionEnabled", config.getBoolean("format.million.enabled", true));
        formatSettings.put("millionSuffix", config.getString("format.million.suffix", "M"));
        formatSettings.put("thousandEnabled", config.getBoolean("format.thousand.enabled", true));
        formatSettings.put("thousandSuffix", config.getString("format.thousand.suffix", "K"));

        // Load sound settings
        loadSoundSettings(config);

        // Load metrics settings
        metricsEnabled = config.getBoolean("metrics.enabled", true);

        // Load balance tracker settings
        balanceTrackerInitialDelay = config.getLong("performance.balance-tracker.initial-delay", 5L);
        balanceTrackerInterval = config.getLong("performance.balance-tracker.interval", 5L);

        // Load stats settings
        statsEnabled = config.getBoolean("stats.enabled", true);
        statsSaveInterval = config.getLong("stats.save-interval", 300); // Default to 5 minutes

        // Load stats world settings
        statsEnabledWorlds = new ArrayList<>();
        List<String> worldsList = config.getStringList("stats.enabled-worlds");
        statsAllWorldsEnabled = false;

        for (String world : worldsList) {
            if (world.equalsIgnoreCase("all")) {
                statsAllWorldsEnabled = true;
                break;
            }
            statsEnabledWorlds.add(world);
        }

        if (worldsList.isEmpty()) {
            // Default to all worlds if not specified
            statsAllWorldsEnabled = true;
        }

        // Load player interaction settings
        showPingOnCrosshair = config.getBoolean("player-interaction.show-ping-on-crosshair", true);
        pingFormat = ChatColor.translateAlternateColorCodes('&',
                config.getString("player-interaction.ping-format", "&e%player% &7- &aPing: &f%ping%ms"));

        statsGuiEnabled = config.getBoolean("player-interaction.stats-gui.enabled", true);
        statsGuiTitle = ChatColor.translateAlternateColorCodes('&',
                config.getString("player-interaction.stats-gui.title", "&8Player Stats: &e%player%"));

        // Load Stats GUI sound settings
        String soundName = config.getString("player-interaction.stats-gui.open-sound", "BLOCK_CHEST_OPEN");
        try {
            statsGuiOpenSound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            statsGuiOpenSound = Sound.BLOCK_CHEST_OPEN;
            logger.warning("Invalid stats GUI open sound in config: '" + soundName
                    + "'. Using default sound: BLOCK_CHEST_OPEN. " +
                    "Valid sounds can be found at: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html");
        }
        statsGuiOpenSoundVolume = (float) Math.max(0.0,
                Math.min(1.0, config.getDouble("player-interaction.stats-gui.open-sound-volume", 0.5)));
        statsGuiOpenSoundPitch = (float) Math.max(0.0,
                Math.min(2.0, config.getDouble("player-interaction.stats-gui.open-sound-pitch", 1.0)));
    }

    /**
     * Loads sound settings from configuration.
     *
     * @param config FileConfiguration instance
     */
    private void loadSoundSettings(FileConfiguration config) {
        // Kill sound settings
        killSoundEnabled = config.getBoolean("sounds.kill.enabled", true);
        String killSoundName = config.getString("sounds.kill.sound", "ENTITY_PLAYER_LEVELUP");
        try {
            killSound = Sound.valueOf(killSoundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            killSound = Sound.ENTITY_PLAYER_LEVELUP;
            logger.warning("Invalid kill sound in config: '" + killSoundName
                    + "'. Using default sound: ENTITY_PLAYER_LEVELUP. " +
                    "Valid sounds can be found at: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html");
        }
        killSoundVolume = (float) Math.max(0.0, Math.min(1.0, config.getDouble("sounds.kill.volume", 1.0)));
        killSoundPitch = (float) Math.max(0.0, Math.min(2.0, config.getDouble("sounds.kill.pitch", 1.0)));

        // Death sound settings
        deathSoundEnabled = config.getBoolean("sounds.death.enabled", true);
        String deathSoundName = config.getString("sounds.death.sound", "ENTITY_VILLAGER_DEATH");
        try {
            deathSound = Sound.valueOf(deathSoundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            deathSound = Sound.ENTITY_VILLAGER_DEATH;
            logger.warning("Invalid death sound in config: '" + deathSoundName
                    + "'. Using default sound: ENTITY_VILLAGER_DEATH. " +
                    "Valid sounds can be found at: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html");
        }
        deathSoundVolume = (float) Math.max(0.0, Math.min(1.0, config.getDouble("sounds.death.volume", 1.0)));
        deathSoundPitch = (float) Math.max(0.0, Math.min(2.0, config.getDouble("sounds.death.pitch", 0.5)));
    }

    /**
     * Checks if stats display is enabled for a specific world.
     * 
     * @param worldName The name of the world to check
     * @return true if stats are enabled in this world, false otherwise
     */
    public boolean isStatsEnabledInWorld(String worldName) {
        if (!statsEnabled || !statsGuiEnabled) {
            return false;
        }

        if (statsAllWorldsEnabled) {
            return true;
        }

        return statsEnabledWorlds.contains(worldName);
    }

    // Getters for all configuration values

    public int getTitleFadeIn() {
        return titleFadeIn;
    }

    public int getTitleStay() {
        return titleStay;
    }

    public int getTitleFadeOut() {
        return titleFadeOut;
    }

    public String getKillTitle() {
        return killTitle;
    }

    public String getKillSubtitle() {
        return killSubtitle;
    }

    public String getDeathTitle() {
        return deathTitle;
    }

    public String getDeathSubtitle() {
        return deathSubtitle;
    }

    public boolean isKillSoundEnabled() {
        return killSoundEnabled;
    }

    public Sound getKillSound() {
        return killSound;
    }

    public float getKillSoundVolume() {
        return killSoundVolume;
    }

    public float getKillSoundPitch() {
        return killSoundPitch;
    }

    public boolean isDeathSoundEnabled() {
        return deathSoundEnabled;
    }

    public Sound getDeathSound() {
        return deathSound;
    }

    public float getDeathSoundVolume() {
        return deathSoundVolume;
    }

    public float getDeathSoundPitch() {
        return deathSoundPitch;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public Map<String, Object> getFormatSettings() {
        return formatSettings;
    }

    public long getBalanceTrackerInitialDelay() {
        return balanceTrackerInitialDelay;
    }

    public long getBalanceTrackerInterval() {
        return balanceTrackerInterval;
    }

    public boolean isStatsEnabled() {
        return statsEnabled;
    }

    public long getStatsSaveInterval() {
        return statsSaveInterval;
    }

    public boolean isShowPingOnCrosshair() {
        return showPingOnCrosshair;
    }

    public String getPingFormat() {
        return pingFormat;
    }

    public boolean isStatsGuiEnabled() {
        return statsGuiEnabled;
    }

    public String getStatsGuiTitle() {
        return statsGuiTitle;
    }

    public Sound getStatsGuiOpenSound() {
        return statsGuiOpenSound;
    }

    public float getStatsGuiOpenSoundVolume() {
        return statsGuiOpenSoundVolume;
    }

    public float getStatsGuiOpenSoundPitch() {
        return statsGuiOpenSoundPitch;
    }
}