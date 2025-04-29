package org.cipher.moneyTitles.manager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.cipher.moneyTitles.config.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages player balances and economy operations.
 * Optimized for high-performance tracking and reduces main thread workload.
 */
public class BalanceManager {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private Economy econ;
    private final Map<UUID, Double> lastKnownBalance = new ConcurrentHashMap<>();
    private BukkitTask balanceTrackerTask;
    private int balanceTrackerTaskId = -1;

    /**
     * Creates a new BalanceManager.
     *
     * @param plugin        Plugin instance
     * @param configManager Configuration manager
     */
    public BalanceManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
    }

    /**
     * Sets up the economy hook.
     *
     * @return True if setup was successful
     */
    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    /**
     * Starts the balance tracker task.
     * Optimized to reduce load on the main thread.
     */
    public void startBalanceTracker() {
        stopBalanceTracker(); // Ensure we don't have multiple trackers running

        long initialDelay = configManager.getBalanceTrackerInitialDelay();
        long interval = configManager.getBalanceTrackerInterval();

        balanceTrackerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player != null && player.isOnline()) {
                        updateBalance(player);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in balance tracker: ", e);
            }
        }, initialDelay, interval);

        balanceTrackerTaskId = balanceTrackerTask.getTaskId();
    }

    /**
     * Updates a player's balance in cache.
     * Optimized to reduce repeated calls to the economy API.
     *
     * @param player Player to update
     */
    public void updateBalance(Player player) {
        if (player == null || !player.isOnline() || econ == null) {
            return;
        }

        try {
            lastKnownBalance.put(player.getUniqueId(), econ.getBalance(player));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating balance for " + player.getName(), e);
        }
    }

    /**
     * Gets a player's balance from cache.
     * 
     * @param player Player to get balance for
     * @return Player's cached balance or current balance if not cached
     */
    public double getBalance(Player player) {
        if (player == null || !player.isOnline() || econ == null) {
            return 0;
        }

        UUID uuid = player.getUniqueId();
        if (!lastKnownBalance.containsKey(uuid)) {
            // Update the cache if we don't have a value
            updateBalance(player);
        }

        return lastKnownBalance.getOrDefault(uuid, 0D);
    }

    /**
     * Calculate the money gained from a kill.
     * 
     * @param killer     The killer
     * @param oldBalance Previous balance
     * @return Amount gained
     */
    public double calculateMoneyGained(Player killer, double oldBalance) {
        if (killer == null || !killer.isOnline() || econ == null) {
            return 0;
        }

        double newBalance = econ.getBalance(killer);
        lastKnownBalance.put(killer.getUniqueId(), newBalance);
        return newBalance - oldBalance;
    }

    /**
     * Calculate the money lost from death.
     * 
     * @param victim     The victim
     * @param oldBalance Previous balance
     * @return Amount lost
     */
    public double calculateMoneyLost(Player victim, double oldBalance) {
        if (victim == null || !victim.isOnline() || econ == null) {
            return 0;
        }

        double newBalance = econ.getBalance(victim);
        lastKnownBalance.put(victim.getUniqueId(), newBalance);
        return oldBalance - newBalance;
    }

    /**
     * Stops the balance tracker task.
     */
    public void stopBalanceTracker() {
        if (balanceTrackerTask != null && !balanceTrackerTask.isCancelled()) {
            balanceTrackerTask.cancel();
        }

        if (balanceTrackerTaskId != -1) {
            try {
                Bukkit.getScheduler().cancelTask(balanceTrackerTaskId);
            } catch (Exception ignored) {
                // Task might already be cancelled
            }
            balanceTrackerTaskId = -1;
        }
    }

    /**
     * Clears all cached balances.
     */
    public void clearBalanceCache() {
        lastKnownBalance.clear();
    }

    /**
     * Gets the Economy instance.
     * 
     * @return Economy instance
     */
    public Economy getEconomy() {
        return econ;
    }
}