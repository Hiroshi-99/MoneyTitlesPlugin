package org.cipher.moneyTitles;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MoneyTitles extends JavaPlugin implements Listener {
    private Economy econ;
    private final Map<UUID, Double> lastKnownBalance = new ConcurrentHashMap<>();
    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;
    private String killTitle;
    private String killSubtitle;
    private String deathTitle;
    private String deathSubtitle;
    private int decimalPlaces;
    private DecimalFormat moneyFormat;
    
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
    private boolean formatQuadrillionEnabled;
    private String formatQuadrillionSuffix;
    private boolean formatTrillionEnabled;
    private String formatTrillionSuffix;
    private boolean formatBillionEnabled;
    private String formatBillionSuffix;
    private boolean formatMillionEnabled;
    private String formatMillionSuffix;
    private boolean formatThousandEnabled;
    private String formatThousandSuffix;
    
    // Task ID for cancellation when disabling
    private int balanceTrackerTaskId = -1;

    @Override
    public void onEnable() {
        // Show beautiful startup message with simpler characters
        getLogger().info("");
        getLogger().info(ChatColor.AQUA + "+-----------------------+");
        getLogger().info(ChatColor.AQUA + "|" + ChatColor.WHITE + ChatColor.BOLD + "     MoneyTitles     " + ChatColor.AQUA + "|");
        getLogger().info(ChatColor.AQUA + "|" + ChatColor.YELLOW + "    Version: " + getDescription().getVersion() + ChatColor.AQUA + "    |");
        getLogger().info(ChatColor.AQUA + "|" + ChatColor.WHITE + "  Developed with " + ChatColor.RED + "♥" + ChatColor.WHITE + " by" + ChatColor.AQUA + "  |");
        getLogger().info(ChatColor.AQUA + "|" + ChatColor.GOLD + "     Cipher88     " + ChatColor.AQUA + "|");
        getLogger().info(ChatColor.AQUA + "+-----------------------+");
        getLogger().info("");

        saveDefaultConfig();
        loadConfig();

        if (!setupEconomy()) {
            getLogger().severe(ChatColor.RED + "✘ " + ChatColor.WHITE + "Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);
        startBalanceTracker();

        // Show successful startup with simpler characters
        getLogger().info(ChatColor.GREEN + "» " + ChatColor.WHITE + "Plugin has been enabled successfully!");
        getLogger().info(ChatColor.GREEN + "» " + ChatColor.WHITE + "Economy hook: " + ChatColor.GREEN + "Enabled");
        getLogger().info("");
    }
    
    @Override
    public void onDisable() {
        if (balanceTrackerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(balanceTrackerTaskId);
        }
        lastKnownBalance.clear();
        getLogger().info(ChatColor.RED + "» " + ChatColor.WHITE + "Plugin has been disabled.");
    }

    private void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();

        titleFadeIn = config.getInt("title.fade-in", 5);
        titleStay = config.getInt("title.stay", 40);
        titleFadeOut = config.getInt("title.fade-out", 5);

        killTitle = ChatColor.translateAlternateColorCodes('&', 
                config.getString("messages.kill-title", "&a&l+%amount% $"));
        killSubtitle = ChatColor.translateAlternateColorCodes('&', 
                config.getString("messages.kill-subtitle", "&f&lfor killing %victim%"));
        deathTitle = ChatColor.translateAlternateColorCodes('&', 
                config.getString("messages.death-title", "&c&l-%amount% $"));
        deathSubtitle = ChatColor.translateAlternateColorCodes('&', 
                config.getString("messages.death-subtitle", "&f&lKilled by %killer%"));

        decimalPlaces = config.getInt("format.decimal-places", 2);
        moneyFormat = new DecimalFormat("#,##0" + (decimalPlaces > 0 ? "." + "0".repeat(decimalPlaces) : ""));
        
        // Load format settings
        formatQuadrillionEnabled = config.getBoolean("format.quadrillion.enabled", true);
        formatQuadrillionSuffix = config.getString("format.quadrillion.suffix", "Q");
        formatTrillionEnabled = config.getBoolean("format.trillion.enabled", true);
        formatTrillionSuffix = config.getString("format.trillion.suffix", "T");
        formatBillionEnabled = config.getBoolean("format.billion.enabled", true);
        formatBillionSuffix = config.getString("format.billion.suffix", "B");
        formatMillionEnabled = config.getBoolean("format.million.enabled", true);
        formatMillionSuffix = config.getString("format.million.suffix", "M");
        formatThousandEnabled = config.getBoolean("format.thousand.enabled", true);
        formatThousandSuffix = config.getString("format.thousand.suffix", "K");

        // Load sound settings
        loadSoundSettings(config);
    }
    
    private void loadSoundSettings(FileConfiguration config) {
        killSoundEnabled = config.getBoolean("sounds.kill.enabled", true);
        String killSoundName = config.getString("sounds.kill.sound", "ENTITY_PLAYER_LEVELUP");
        try {
            killSound = Sound.valueOf(killSoundName);
        } catch (IllegalArgumentException e) {
            killSound = Sound.ENTITY_PLAYER_LEVELUP;
            getLogger().warning("Invalid kill sound in config: " + killSoundName + "! Using default sound.");
        }
        killSoundVolume = (float) config.getDouble("sounds.kill.volume", 1.0);
        killSoundPitch = (float) config.getDouble("sounds.kill.pitch", 1.0);

        deathSoundEnabled = config.getBoolean("sounds.death.enabled", true);
        String deathSoundName = config.getString("sounds.death.sound", "ENTITY_VILLAGER_DEATH");
        try {
            deathSound = Sound.valueOf(deathSoundName);
        } catch (IllegalArgumentException e) {
            deathSound = Sound.ENTITY_VILLAGER_DEATH;
            getLogger().warning("Invalid death sound in config: " + deathSoundName + "! Using default sound.");
        }
        deathSoundVolume = (float) config.getDouble("sounds.death.volume", 1.0);
        deathSoundPitch = (float) config.getDouble("sounds.death.pitch", 0.5);
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
            loadConfig();
            sender.sendMessage(ChatColor.GREEN + "MoneyTitles configuration reloaded!");
            return true;
        }
        
        // Show help if no arguments provided
        sender.sendMessage(ChatColor.AQUA + "MoneyTitles " + ChatColor.GRAY + "v" + getDescription().getVersion());
        sender.sendMessage(ChatColor.GRAY + "/moneytitles reload " + ChatColor.WHITE + "- Reload the configuration");
        return true;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    private void startBalanceTracker() {
        balanceTrackerTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player != null && player.isOnline()) {
                        lastKnownBalance.put(player.getUniqueId(), econ.getBalance(player));
                    }
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error in balance tracker: ", e);
            }
        }, 5L, 5L).getTaskId();
    }

    private String formatMoney(double amount) {
        if (amount >= 1_000_000_000_000_000D && formatQuadrillionEnabled) {
            return formatLargeNumber(amount, 1_000_000_000_000_000D, formatQuadrillionSuffix);
        } else if (amount >= 1_000_000_000_000D && formatTrillionEnabled) {
            return formatLargeNumber(amount, 1_000_000_000_000D, formatTrillionSuffix);
        } else if (amount >= 1_000_000_000D && formatBillionEnabled) {
            return formatLargeNumber(amount, 1_000_000_000D, formatBillionSuffix);
        } else if (amount >= 1_000_000D && formatMillionEnabled) {
            return formatLargeNumber(amount, 1_000_000D, formatMillionSuffix);
        } else if (amount >= 1_000D && formatThousandEnabled) {
            return formatLargeNumber(amount, 1_000D, formatThousandSuffix);
        }
        return moneyFormat.format(amount);
    }
    
    private String formatLargeNumber(double amount, double divisor, String suffix) {
        double value = amount / divisor;
        if (decimalPlaces == 0 || value == Math.floor(value)) {
            return ((int) value) + suffix;
        } else {
            return moneyFormat.format(value) + suffix;
        }
    }
    
    private void sendMoneyTitle(Player player, String title, String subtitle) {
        try {
            player.sendTitle(title, subtitle, titleFadeIn, titleStay, titleFadeOut);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to send title to " + player.getName(), e);
        }
    }
    
    private void playSound(Player player, Sound sound, float volume, float pitch) {
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to play sound for " + player.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.equals(victim)) {
            return;
        }

        double oldKillerBalance = lastKnownBalance.getOrDefault(killer.getUniqueId(), 0D);
        double newKillerBalance = econ.getBalance(killer);
        double oldVictimBalance = lastKnownBalance.getOrDefault(victim.getUniqueId(), 0D);
        double newVictimBalance = econ.getBalance(victim);

        double gained = newKillerBalance - oldKillerBalance;
        double lost = oldVictimBalance - newVictimBalance;

        // Update balances immediately
        lastKnownBalance.put(killer.getUniqueId(), newKillerBalance);
        lastKnownBalance.put(victim.getUniqueId(), newVictimBalance);

        // Process killer rewards
        if (gained > 0) {
            String formattedAmount = formatMoney(gained);
            String title = killTitle.replace("%amount%", formattedAmount);
            String subtitle = killSubtitle.replace("%victim%", victim.getName());
            
            // Send title and sound async to reduce main thread load
            Bukkit.getScheduler().runTask(this, () -> {
                sendMoneyTitle(killer, title, subtitle);
                
                if (killSoundEnabled) {
                    playSound(killer, killSound, killSoundVolume, killSoundPitch);
                }
            });
        }

        // Process victim penalties
        if (lost > 0) {
            String formattedAmount = formatMoney(lost);
            String title = deathTitle.replace("%amount%", formattedAmount);
            String subtitle = deathSubtitle.replace("%killer%", killer.getName());
            
            // Send title and sound async to reduce main thread load
            Bukkit.getScheduler().runTask(this, () -> {
                sendMoneyTitle(victim, title, subtitle);
                
                if (deathSoundEnabled) {
                    playSound(victim, deathSound, deathSoundVolume, deathSoundPitch);
                }
            });
        }
    }
}