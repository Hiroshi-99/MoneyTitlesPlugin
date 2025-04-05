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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // Sound settings
    private boolean killSoundEnabled;
    private Sound killSound;
    private float killSoundVolume;
    private float killSoundPitch;
    private boolean deathSoundEnabled;
    private Sound deathSound;
    private float deathSoundVolume;
    private float deathSoundPitch;

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

    private void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();

        titleFadeIn = config.getInt("title.fade-in", 5);
        titleStay = config.getInt("title.stay", 40);
        titleFadeOut = config.getInt("title.fade-out", 5);

        killTitle = config.getString("messages.kill-title", "&a&l+%amount% $");
        killSubtitle = config.getString("messages.kill-subtitle", "&f&lfor killing %victim%");
        deathTitle = config.getString("messages.death-title", "&c&l-%amount% $");
        deathSubtitle = config.getString("messages.death-subtitle", "&f&lKilled by %killer%");

        decimalPlaces = config.getInt("format.decimal-places", 2);

        // Load sound settings
        killSoundEnabled = config.getBoolean("sounds.kill.enabled", true);
        try {
            killSound = Sound.valueOf(config.getString("sounds.kill.sound", "ENTITY_PLAYER_LEVELUP"));
        } catch (IllegalArgumentException e) {
            killSound = Sound.ENTITY_PLAYER_LEVELUP;
            getLogger().warning("Invalid kill sound in config! Using default sound.");
        }
        killSoundVolume = (float) config.getDouble("sounds.kill.volume", 1.0);
        killSoundPitch = (float) config.getDouble("sounds.kill.pitch", 1.0);

        deathSoundEnabled = config.getBoolean("sounds.death.enabled", true);
        try {
            deathSound = Sound.valueOf(config.getString("sounds.death.sound", "ENTITY_VILLAGER_DEATH"));
        } catch (IllegalArgumentException e) {
            deathSound = Sound.ENTITY_VILLAGER_DEATH;
            getLogger().warning("Invalid death sound in config! Using default sound.");
        }
        deathSoundVolume = (float) config.getDouble("sounds.death.volume", 1.0);
        deathSoundPitch = (float) config.getDouble("sounds.death.pitch", 0.5);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("moneytitles")) {
            if (!sender.hasPermission("moneytitles.reload")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            loadConfig();
            sender.sendMessage(ChatColor.GREEN + "MoneyTitles configuration reloaded!");
            return true;
        }
        return false;
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
        return true;
    }

    private void startBalanceTracker() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                lastKnownBalance.put(player.getUniqueId(), econ.getBalance(player));
            }
        }, 5L, 5L);
    }

    private String formatMoney(double amount) {
        FileConfiguration config = getConfig();
        String format = "%." + decimalPlaces + "f";

        if (amount >= 1_000_000_000_000_000D && config.getBoolean("format.quadrillion.enabled", true)) {
            return String.format(format + "%s", amount / 1_000_000_000_000_000D,
                    config.getString("format.quadrillion.suffix", "Q")).replace(".00", "");
        } else if (amount >= 1_000_000_000_000D && config.getBoolean("format.trillion.enabled", true)) {
            return String.format(format + "%s", amount / 1_000_000_000_000D,
                    config.getString("format.trillion.suffix", "T")).replace(".00", "");
        } else if (amount >= 1_000_000_000D && config.getBoolean("format.billion.enabled", true)) {
            return String.format(format + "%s", amount / 1_000_000_000D,
                    config.getString("format.billion.suffix", "B")).replace(".00", "");
        } else if (amount >= 1_000_000D && config.getBoolean("format.million.enabled", true)) {
            return String.format(format + "%s", amount / 1_000_000D,
                    config.getString("format.million.suffix", "M")).replace(".00", "");
        } else if (amount >= 1_000D && config.getBoolean("format.thousand.enabled", true)) {
            return String.format(format + "%s", amount / 1_000D,
                    config.getString("format.thousand.suffix", "K")).replace(".00", "");
        }
        return String.format("%.0f", amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) return;

        double oldKillerBalance = lastKnownBalance.getOrDefault(killer.getUniqueId(), 0D);
        double newKillerBalance = econ.getBalance(killer);
        double oldVictimBalance = lastKnownBalance.getOrDefault(victim.getUniqueId(), 0D);
        double newVictimBalance = econ.getBalance(victim);

        double gained = newKillerBalance - oldKillerBalance;
        double lost = oldVictimBalance - newVictimBalance;

        // Update balances immediately
        lastKnownBalance.put(killer.getUniqueId(), newKillerBalance);
        lastKnownBalance.put(victim.getUniqueId(), newVictimBalance);

        if (gained > 0) {
            String display = formatMoney(gained);
            String title = ChatColor.translateAlternateColorCodes('&',
                    killTitle.replace("%amount%", display));
            String subtitle = ChatColor.translateAlternateColorCodes('&',
                    killSubtitle.replace("%victim%", victim.getName()));
            killer.sendTitle(title, subtitle, titleFadeIn, titleStay, titleFadeOut);

            // Play kill sound
            if (killSoundEnabled) {
                killer.playSound(killer.getLocation(), killSound, killSoundVolume, killSoundPitch);
            }
        }

        if (lost > 0) {
            String display = formatMoney(lost);
            String title = ChatColor.translateAlternateColorCodes('&',
                    deathTitle.replace("%amount%", display));
            String subtitle = ChatColor.translateAlternateColorCodes('&',
                    deathSubtitle.replace("%killer%", killer.getName()));
            victim.sendTitle(title, subtitle, titleFadeIn, titleStay, titleFadeOut);

            // Play death sound
            if (deathSoundEnabled) {
                victim.playSound(victim.getLocation(), deathSound, deathSoundVolume, deathSoundPitch);
            }
        }
    }
}