package org.cipher.moneyTitles;

import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.cipher.moneyTitles.config.ConfigManager;
import org.cipher.moneyTitles.manager.BalanceManager;
import org.cipher.moneyTitles.manager.GuiManager;
import org.cipher.moneyTitles.manager.InteractionManager;
import org.cipher.moneyTitles.manager.StatsManager;
import org.cipher.moneyTitles.manager.TitleManager;
import org.cipher.moneyTitles.util.MoneyFormatter;
import org.cipher.moneyTitles.util.LicenseVerifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Main plugin class for MoneyTitles.
 * Optimized for high-performance processing of player kills and deaths.
 */
public class MoneyTitles extends JavaPlugin implements Listener {
    private ConfigManager configManager;
    private BalanceManager balanceManager;
    private TitleManager titleManager;
    private MoneyFormatter moneyFormatter;
    private StatsManager statsManager;
    private GuiManager guiManager;
    private InteractionManager interactionManager;
    private LicenseVerifier licenseVerifier;

    // Stats tracking
    private final AtomicInteger killsTracked = new AtomicInteger(0);
    private final AtomicInteger moneyTransferred = new AtomicInteger(0);
    private static final int BSTATS_PLUGIN_ID = 25383;

    @Override
    public void onEnable() {
        // License check (Lukittu)
        saveDefaultConfig();
        String licenseKey = getConfig().getString("license.key");
        if (licenseKey == null || licenseKey.equals("YOUR_LICENSE_KEY") || licenseKey.isEmpty()) {
            sendColoredMessage("");
            sendColoredMessage(ChatColor.RED + "╔═════════════════════════════════════╗");
            sendColoredMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + ChatColor.BOLD
                    + "         LICENSE ERROR         " + ChatColor.RED + " ║");
            sendColoredMessage(ChatColor.RED + "╠═════════════════════════════════════╣");
            sendColoredMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "  Invalid or missing license key!  "
                    + ChatColor.RED + " ║");
            sendColoredMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "  Please set a valid license key   "
                    + ChatColor.RED + " ║");
            sendColoredMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "  in your config.yml file.         "
                    + ChatColor.RED + " ║");
            sendColoredMessage(ChatColor.RED + "╚═════════════════════════════════════╝");
            sendColoredMessage("");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        licenseVerifier = new LicenseVerifier(licenseKey, this);
        if (!licenseVerifier.verify()) {
            sendColoredMessage("");
            sendColoredMessage(ChatColor.RED + "╔═════════════════════════════════════╗");
            sendColoredMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + ChatColor.BOLD
                    + "      LICENSE VERIFICATION      " + ChatColor.RED + " ║");
            sendColoredMessage(ChatColor.RED + "╠═════════════════════════════════════╣");
            sendColoredMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "  License verification failed!     "
                    + ChatColor.RED + " ║");
            sendColoredMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "  Please check your license key    "
                    + ChatColor.RED + " ║");
            sendColoredMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "  or contact Cipher88.              "
                    + ChatColor.RED + " ║");
            sendColoredMessage(ChatColor.RED + "╚═════════════════════════════════════╝");
            sendColoredMessage("");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (licenseVerifier.isOfflineMode()) {
            sendColoredMessage("");
            sendColoredMessage(ChatColor.YELLOW + "╔═════════════════════════════════════╗");
            sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.RED + ChatColor.BOLD + "        OFFLINE MODE        "
                    + ChatColor.YELLOW + " ║");
            sendColoredMessage(ChatColor.YELLOW + "╠═════════════════════════════════════╣");
            sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "  Running with offline validation!  "
                    + ChatColor.YELLOW + " ║");
            sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "  License verification skipped      "
                    + ChatColor.YELLOW + " ║");
            sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "  due to network connectivity       "
                    + ChatColor.YELLOW + " ║");
            sendColoredMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "  issues.                           "
                    + ChatColor.YELLOW + " ║");
            sendColoredMessage(ChatColor.YELLOW + "╚═════════════════════════════════════╝");
            sendColoredMessage("");
        } else {
            sendColoredMessage("");
            sendColoredMessage(ChatColor.GREEN + "╔═════════════════════════════════════╗");
            sendColoredMessage(ChatColor.GREEN + "║ " + ChatColor.WHITE + ChatColor.BOLD
                    + "      LICENSE VERIFICATION      " + ChatColor.GREEN + " ║");
            sendColoredMessage(ChatColor.GREEN + "╠═════════════════════════════════════╣");
            sendColoredMessage(ChatColor.GREEN + "║ " + ChatColor.WHITE + "  License successfully verified!   "
                    + ChatColor.GREEN + " ║");
            sendColoredMessage(ChatColor.GREEN + "║ " + ChatColor.WHITE + "  Thank you for using         "
                    + ChatColor.GREEN + " ║");
            sendColoredMessage(ChatColor.GREEN + "║ " + ChatColor.WHITE + "  MoneyTitles!                     "
                    + ChatColor.GREEN + " ║");
            sendColoredMessage(ChatColor.GREEN + "╚═════════════════════════════════════╝");
            sendColoredMessage("");
        }

        // Schedule periodic license checks
        startLicenseHeartbeat();

        // Show beautiful startup message
        displayStartupBanner();

        // Initialize managers
        initializeManagers();

        // Setup economy hook
        if (!balanceManager.setupEconomy()) {
            sendColoredMessage(ChatColor.RED + "✘ " + ChatColor.WHITE + "Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Register the InteractionManager (comment out the following line as it's now
        // handled by InteractionManager)
        // getServer().getPluginManager().registerEvents(new
        // PlayerInteractionListener(this), this);

        // Start tracking player balances
        balanceManager.startBalanceTracker();

        // Setup metrics
        setupMetrics();

        // Show successful startup
        sendColoredMessage("");
        sendColoredMessage(ChatColor.GREEN + "╔═════════════════════════════════════╗");
        sendColoredMessage(ChatColor.GREEN + "║ " + ChatColor.WHITE + ChatColor.BOLD + "        PLUGIN ENABLED        "
                + ChatColor.GREEN + " ║");
        sendColoredMessage(ChatColor.GREEN + "╠═════════════════════════════════════╣");
        sendColoredMessage(ChatColor.GREEN + "║ " + ChatColor.WHITE + "  Active features:                 "
                + ChatColor.GREEN + " ║");

        StringBuilder economy = new StringBuilder(ChatColor.GREEN + "║ " + ChatColor.WHITE + "  » Economy hook: ");
        economy.append(ChatColor.YELLOW + "Enabled");
        economy.append(ChatColor.GREEN + "            ║");
        sendColoredMessage(economy.toString());

        if (configManager.isStatsEnabled()) {
            StringBuilder stats = new StringBuilder(ChatColor.GREEN + "║ " + ChatColor.WHITE + "  » Player stats: ");
            stats.append(ChatColor.YELLOW + "Enabled");
            stats.append(ChatColor.GREEN + "            ║");
            sendColoredMessage(stats.toString());
        }

        if (configManager.isShowPingOnCrosshair()) {
            StringBuilder ping = new StringBuilder(ChatColor.GREEN + "║ " + ChatColor.WHITE + "  » Ping display: ");
            ping.append(ChatColor.YELLOW + "Enabled");
            ping.append(ChatColor.GREEN + "            ║");
            sendColoredMessage(ping.toString());
        }

        if (configManager.isStatsGuiEnabled()) {
            StringBuilder gui = new StringBuilder(ChatColor.GREEN + "║ " + ChatColor.WHITE + "  » Stats GUI: ");
            gui.append(ChatColor.YELLOW + "Enabled");
            gui.append(ChatColor.GREEN + "                ║");
            sendColoredMessage(gui.toString());
        }

        sendColoredMessage(ChatColor.GREEN + "║ " + ChatColor.WHITE + "                                   "
                + ChatColor.GREEN + " ║");
        sendColoredMessage(ChatColor.GREEN + "║ " + ChatColor.WHITE + "  Plugin is " + ChatColor.YELLOW + "ready "
                + ChatColor.WHITE + "and " + ChatColor.YELLOW + "running" + ChatColor.WHITE + "!     " + ChatColor.GREEN
                + " ║");
        sendColoredMessage(ChatColor.GREEN + "╚═════════════════════════════════════╝");
        sendColoredMessage("");
    }

    @Override
    public void onDisable() {
        sendColoredMessage("");
        sendColoredMessage(ChatColor.RED + "╔═════════════════════════════════════╗");
        sendColoredMessage(ChatColor.RED + "║ " + ChatColor.GOLD + ChatColor.BOLD + "       MoneyTitles Shutdown       "
                + ChatColor.RED + " ║");
        sendColoredMessage(ChatColor.RED + "╠═════════════════════════════════════╣");

        // Cleanup
        if (statsManager != null) {
            sendColoredMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "» " + ChatColor.YELLOW
                    + "Saving player statistics..." + ChatColor.RED + "       ║");
            statsManager.saveAllStats();
            statsManager.stopAutoSaveTask();
        }

        if (balanceManager != null) {
            sendColoredMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "» " + ChatColor.YELLOW
                    + "Stopping balance tracker..." + ChatColor.RED + "       ║");
            balanceManager.stopBalanceTracker();
            balanceManager.clearBalanceCache();
        }

        if (moneyFormatter != null) {
            sendColoredMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "» " + ChatColor.YELLOW
                    + "Clearing format cache..." + ChatColor.RED + "           ║");
            moneyFormatter.clearCache();
        }

        if (interactionManager != null) {
            sendColoredMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "» " + ChatColor.YELLOW
                    + "Shutting down interaction manager..." + ChatColor.RED + "║");
            interactionManager.shutdown();
        }

        sendColoredMessage(ChatColor.RED + "╠═════════════════════════════════════╣");
        sendColoredMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "Plugin has been " + ChatColor.RED + "disabled"
                + ChatColor.WHITE + " successfully!" + ChatColor.RED + " ║");
        sendColoredMessage(ChatColor.RED + "╚═════════════════════════════════════╝");
        sendColoredMessage("");
    }

    /**
     * Returns the StatsManager instance.
     * 
     * @return The StatsManager instance
     */
    public StatsManager getStatsManager() {
        return statsManager;
    }

    /**
     * Returns the ConfigManager instance.
     * 
     * @return The ConfigManager instance
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Returns the BalanceManager instance.
     * 
     * @return The BalanceManager instance
     */
    public BalanceManager getBalanceManager() {
        return balanceManager;
    }

    /**
     * Returns the MoneyFormatter instance.
     * 
     * @return The MoneyFormatter instance
     */
    public MoneyFormatter getMoneyFormatter() {
        return moneyFormatter;
    }

    /**
     * Initializes all managers used by the plugin.
     */
    private void initializeManagers() {
        // Create config manager first as other managers depend on it
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // Create money formatter
        Map<String, Object> formatSettings = configManager.getFormatSettings();
        moneyFormatter = new MoneyFormatter(
                configManager.getDecimalPlaces(),
                (boolean) formatSettings.get("quadrillionEnabled"),
                (String) formatSettings.get("quadrillionSuffix"),
                (boolean) formatSettings.get("trillionEnabled"),
                (String) formatSettings.get("trillionSuffix"),
                (boolean) formatSettings.get("billionEnabled"),
                (String) formatSettings.get("billionSuffix"),
                (boolean) formatSettings.get("millionEnabled"),
                (String) formatSettings.get("millionSuffix"),
                (boolean) formatSettings.get("thousandEnabled"),
                (String) formatSettings.get("thousandSuffix"));

        // Create other managers
        balanceManager = new BalanceManager(this, configManager);
        titleManager = new TitleManager(this, configManager, moneyFormatter);

        // Create stats manager if enabled
        if (configManager.isStatsEnabled()) {
            statsManager = new StatsManager(this);
        }

        // Create GUI manager if stats GUI is enabled
        if (configManager.isStatsGuiEnabled() && statsManager != null) {
            guiManager = new GuiManager(this, configManager, statsManager, moneyFormatter);
        }

        // Create interaction manager if any player interaction features are enabled
        if (configManager.isShowPingOnCrosshair() || configManager.isStatsGuiEnabled()) {
            interactionManager = new InteractionManager(this, configManager, guiManager, statsManager);
        }
    }

    /**
     * Displays the plugin startup banner.
     */
    private void displayStartupBanner() {
        sendColoredMessage("");
        sendColoredMessage(ChatColor.DARK_AQUA + "╔═════════════════════════════════════╗");
        sendColoredMessage(ChatColor.DARK_AQUA + "║ " + ChatColor.AQUA + ChatColor.BOLD
                + "           MoneyTitles           " + ChatColor.DARK_AQUA + " ║");
        sendColoredMessage(ChatColor.DARK_AQUA + "║ " + ChatColor.YELLOW + "      Version: " + ChatColor.WHITE
                + getDescription().getVersion() + ChatColor.DARK_AQUA + "             ║");
        sendColoredMessage(ChatColor.DARK_AQUA + "╠═════════════════════════════════════╣");
        sendColoredMessage(ChatColor.DARK_AQUA + "║ " + ChatColor.GREEN + "Features:" + ChatColor.DARK_AQUA
                + "                           ║");
        sendColoredMessage(ChatColor.DARK_AQUA + "║ " + ChatColor.WHITE + "» " + ChatColor.GOLD
                + "Kill & Death Money Rewards" + ChatColor.DARK_AQUA + "      ║");
        sendColoredMessage(ChatColor.DARK_AQUA + "║ " + ChatColor.WHITE + "» " + ChatColor.GOLD
                + "Player Statistics Tracking" + ChatColor.DARK_AQUA + "      ║");
        sendColoredMessage(ChatColor.DARK_AQUA + "║ " + ChatColor.WHITE + "» " + ChatColor.GOLD
                + "Interactive GUI & Ping Display" + ChatColor.DARK_AQUA + " ║");
        sendColoredMessage(ChatColor.DARK_AQUA + "╠═════════════════════════════════════╣");
        sendColoredMessage(ChatColor.DARK_AQUA + "║ " + ChatColor.WHITE + "  Developed with " + ChatColor.RED + "♥"
                + ChatColor.WHITE + " by " + ChatColor.GOLD + "Cipher88" + ChatColor.DARK_AQUA + "   ║");
        sendColoredMessage(ChatColor.DARK_AQUA + "╚═════════════════════════════════════╝");
        sendColoredMessage("");
    }

    /**
     * Sets up bStats metrics tracking.
     */
    private void setupMetrics() {
        // Check if metrics are enabled in config
        if (!configManager.isMetricsEnabled()) {
            sendColoredMessage(ChatColor.YELLOW + "» " + ChatColor.WHITE + "bStats metrics: " + ChatColor.RED
                    + "Disabled by config");
            return;
        }

        // Initialize bStats metrics
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        sendColoredMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "bStats metrics: " + ChatColor.GREEN + "Enabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("moneytitles")) {
            return false;
        }

        if (!sender.hasPermission("moneytitles.reload")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            try {
                configManager.loadConfig();

                // Reload the balance tracker with new settings
                balanceManager.stopBalanceTracker();
                balanceManager.startBalanceTracker();

                // Clear caches to ensure latest settings are used
                moneyFormatter.clearCache();

                sender.sendMessage(ChatColor.GREEN + "MoneyTitles configuration reloaded!");
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error reloading configuration: " + e.getMessage());
                getLogger().log(Level.SEVERE, "Error reloading configuration", e);
            }
            return true;
        }

        // Show help if no arguments provided
        sender.sendMessage(ChatColor.AQUA + "MoneyTitles " + ChatColor.GRAY + "v" + getDescription().getVersion());
        sender.sendMessage(ChatColor.GRAY + "/moneytitles reload " + ChatColor.WHITE + "- Reload the configuration");
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Ignore if there's no killer or if player killed themselves
        if (killer == null || killer.equals(victim)) {
            return;
        }

        try {
            // Get the balances before the kill
            double oldKillerBalance = balanceManager.getBalance(killer);
            double oldVictimBalance = balanceManager.getBalance(victim);

            // Calculate money gained/lost
            double gained = balanceManager.calculateMoneyGained(killer, oldKillerBalance);
            double lost = balanceManager.calculateMoneyLost(victim, oldVictimBalance);

            // Track stats
            if (configManager.isMetricsEnabled()) {
                killsTracked.incrementAndGet();
                moneyTransferred.addAndGet((int) Math.abs(gained));
            }

            // Update player stats if enabled
            if (configManager.isStatsEnabled() && statsManager != null) {
                statsManager.playerKill(killer, victim, gained);
            }

            // Process rewards and penalties
            if (gained > 0) {
                titleManager.sendKillNotification(killer, victim, gained);
            }

            if (lost > 0) {
                titleManager.sendDeathNotification(victim, killer, lost);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error processing player death event", e);
        }
    }

    /**
     * Starts a scheduled task to periodically verify the license
     * This prevents users from bypassing the license check after plugin startup
     */
    private void startLicenseHeartbeat() {
        // Check license every 30 minutes (20 ticks * 60 seconds * 30)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            // First try the heartbeat endpoint
            if (!licenseVerifier.sendHeartbeat()) {
                // If heartbeat fails, fall back to full verification
                if (!licenseVerifier.verify()) {
                    getLogger().severe("License validation failed during runtime check! Disabling plugin.");
                    getServer().getScheduler().runTask(this, () -> {
                        getServer().getPluginManager().disablePlugin(this);
                    });
                } else if (licenseVerifier.isOfflineMode() && !licenseVerifier.isOfflineModeWarningShown()) {
                    getLogger().warning(
                            "Switched to offline mode during runtime. License verification will be skipped until server restart.");
                }
            }
        }, 20 * 60 * 30, 20 * 60 * 30);
    }

    // Add this method to send colored messages reliably to console
    private void sendColoredMessage(String message) {
        getServer().getConsoleSender().sendMessage(message);
    }
}