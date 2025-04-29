package org.cipher.moneyTitles.manager;

import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.cipher.moneyTitles.MoneyTitles;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatsManager {

    private final MoneyTitles plugin;
    private final File statsFile;
    private FileConfiguration statsConfig;
    private final Map<UUID, PlayerStats> playerStatsCache = new HashMap<>();

    public StatsManager(MoneyTitles plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        loadStats();
    }

    private void loadStats() {
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create stats file: " + e.getMessage());
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        plugin.getServer().getOnlinePlayers().forEach(this::loadPlayerStats);
    }

    private void saveStats() {
        try {
            for (Map.Entry<UUID, PlayerStats> entry : playerStatsCache.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerStats stats = entry.getValue();
                String path = uuid.toString();
                statsConfig.set(path + ".kills", stats.kills);
                statsConfig.set(path + ".deaths", stats.deaths);
                statsConfig.set(path + ".playtime", stats.playtimeMinutes);
                statsConfig.set(path + ".moneyGained", stats.moneyGained);
            }
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save stats file: " + e.getMessage());
        }
    }

    private void loadPlayerStats(Player player) {
        UUID uuid = player.getUniqueId();
        String path = uuid.toString();
        int kills = statsConfig.getInt(path + ".kills", 0);
        int deaths = statsConfig.getInt(path + ".deaths", 0);
        long playtime = statsConfig.getLong(path + ".playtime", 0);
        double moneyGained = statsConfig.getDouble(path + ".moneyGained", 0.0);
        playerStatsCache.put(uuid, new PlayerStats(kills, deaths, playtime, moneyGained));
    }

    /**
     * Called when a player joins the server.
     *
     * @param player The player who joined
     */
    public void playerJoin(Player player) {
        // Load the player's stats when they join
        loadPlayerStats(player);
    }

    /**
     * Called when a player quits the server.
     *
     * @param player The player who quit
     */
    public void playerQuit(Player player) {
        // Save the player's stats when they quit
        savePlayerStats(player);
    }

    /**
     * Called when a player kills another player.
     *
     * @param killer      The killer
     * @param victim      The victim
     * @param moneyGained Amount of money gained
     */
    public void playerKill(Player killer, Player victim, double moneyGained) {
        // Update killer stats
        UUID killerUuid = killer.getUniqueId();
        ensureLoaded(killer);
        playerStatsCache.get(killerUuid).kills++;
        playerStatsCache.get(killerUuid).addMoneyGained(moneyGained);

        // Update victim stats
        ensureLoaded(victim);
        playerStatsCache.get(victim.getUniqueId()).deaths++;
    }

    public void savePlayerStats(Player player) {
        if (playerStatsCache.containsKey(player.getUniqueId())) {
            saveStats();
        }
    }

    public void saveAllStats() {
        saveStats();
    }

    /**
     * Stops the auto-save task.
     */
    public void stopAutoSaveTask() {
        // No implementation needed in this simplified version
    }

    public int getKills(Player player) {
        ensureLoaded(player);
        return playerStatsCache.get(player.getUniqueId()).getKills();
    }

    public void addKill(Player player) {
        ensureLoaded(player);
        playerStatsCache.get(player.getUniqueId()).kills++;
    }

    public int getDeaths(Player player) {
        ensureLoaded(player);
        return playerStatsCache.get(player.getUniqueId()).getDeaths();
    }

    public void addDeath(Player player) {
        ensureLoaded(player);
        playerStatsCache.get(player.getUniqueId()).deaths++;
    }

    public long getPlaytimeMinutes(Player player) {
        ensureLoaded(player);
        return playerStatsCache.get(player.getUniqueId()).getPlaytime();
    }

    public double getCurrentMoney(Player player) {
        if (player == null || !player.isOnline()) {

        }
        try {
            BalanceManager balanceManager = plugin.getBalanceManager();
            if (balanceManager != null) {
                return balanceManager.getBalance(player);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting player balance: " + e.getMessage());
        }
        return 0.0;
    }

    private void ensureLoaded(Player player) {
        if (!playerStatsCache.containsKey(player.getUniqueId())) {
            loadPlayerStats(player);
        }
    }

    /**
     * Gets the player stats for a specific UUID.
     *
     * @param uuid The UUID of the player
     * @return The player's stats or null if not found
     */
    public PlayerStats getPlayerStats(UUID uuid) {
        if (playerStatsCache.containsKey(uuid)) {
            return playerStatsCache.get(uuid);
        }
        String path = uuid.toString();
        int kills = statsConfig.getInt(path + ".kills", 0);
        int deaths = statsConfig.getInt(path + ".deaths", 0);
        long playtime = statsConfig.getLong(path + ".playtime", 0);
        double moneyGained = statsConfig.getDouble(path + ".moneyGained", 0.0);
        PlayerStats stats = new PlayerStats(kills, deaths, playtime, moneyGained);
        playerStatsCache.put(uuid, stats);
        return stats;
    }

    // Internal class to hold player stats
    public static class PlayerStats {
        private int kills;
        private int deaths;
        private long playtimeMinutes;
        private double moneyGained;

        public PlayerStats(int kills, int deaths, long playtimeMinutes, double moneyGained) {
            this.kills = kills;
            this.deaths = deaths;
            this.playtimeMinutes = playtimeMinutes;
            this.moneyGained = moneyGained;
        }

        public int getKills() {
            return kills;
        }

        public int getDeaths() {
            return deaths;
        }

        public long getPlaytime() {
            return playtimeMinutes;
        }

        public String getFormattedPlaytime() {
            long hours = playtimeMinutes / 60;
            long minutes = playtimeMinutes % 60;
            if (hours > 0) {
                return hours + "h";
            } else {
                return minutes + "m";
            }
        }

        public double getMoneyGained() {
            return moneyGained;
        }

        public void addMoneyGained(double amount) {
            this.moneyGained += amount;
        }
    }
}
