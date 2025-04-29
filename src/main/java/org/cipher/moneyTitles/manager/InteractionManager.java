package org.cipher.moneyTitles.manager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.cipher.moneyTitles.config.ConfigManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages player interactions such as crosshair hovering and player clicking.
 */
public class InteractionManager implements Listener {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final GuiManager guiManager;
    private final StatsManager statsManager;

    // Task for checking player crosshair targets
    private BukkitTask crosshairTask;

    // Track last target to avoid sending the same message repeatedly
    private final Map<UUID, UUID> lastTargets = new ConcurrentHashMap<>();

    // Track last action bar message time to avoid spam
    private final Map<UUID, Long> lastActionBarTime = new ConcurrentHashMap<>();
    private static final long ACTION_BAR_COOLDOWN = 500; // 0.5 seconds

    public InteractionManager(JavaPlugin plugin, ConfigManager configManager, GuiManager guiManager,
            StatsManager statsManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.guiManager = guiManager;
        this.statsManager = statsManager;

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Start crosshair checking task
        startCrosshairTask();
    }

    /**
     * Starts the task that checks what players are looking at.
     */
    private void startCrosshairTask() {
        if (!configManager.isShowPingOnCrosshair()) {
            return;
        }

        // Use runTaskTimer instead of runTaskTimerAsynchronously to stay on the main
        // thread
        crosshairTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                // If enabled for this world
                if (!configManager.isStatsEnabledInWorld(player.getWorld().getName())) {
                    continue;
                }

                // Save player location and direction for calculations
                Location playerLoc = player.getLocation();
                Vector direction = playerLoc.getDirection();

                // Process player targeting on the main thread
                processPlayerTargeting(player, playerLoc, direction);
            }
        }, 5L, 5L); // Every 5 ticks (0.25 seconds)
    }

    /**
     * Process a player's targeting to find which player they're looking at
     * 
     * @param player    The player to check
     * @param playerLoc The player's location
     * @param direction The player's looking direction
     */
    private void processPlayerTargeting(Player player, Location playerLoc, Vector direction) {
        // Check cooldown
        long now = System.currentTimeMillis();
        Long lastTime = lastActionBarTime.get(player.getUniqueId());
        if (lastTime != null && (now - lastTime) < ACTION_BAR_COOLDOWN) {
            return;
        }

        // Look for nearby players (on main thread)
        Player targetPlayer = null;
        double closestDistance = Double.MAX_VALUE;
        double maxDistance = 3.0; // Increase detection range to 3 blocks

        // Safe to call getNearbyEntities here as we're on the main thread
        for (Entity entity : player.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
            if (entity instanceof Player && !entity.equals(player)) {
                // Check if player is looking in this direction
                Vector toEntity = entity.getLocation().toVector().subtract(playerLoc.toVector());
                double distanceToEntity = toEntity.length();

                if (distanceToEntity > maxDistance) {
                    continue;
                }

                // Normalize to get direction
                Vector normalizedToEntity = toEntity.clone().normalize();

                // Calculate dot product to measure how closely player is looking at entity
                double dot = normalizedToEntity.dot(direction);

                // Entity is within player's field of view (roughly 30 degrees)
                if (dot > 0.85 && distanceToEntity < closestDistance) {
                    // Check line of sight
                    if (player.hasLineOfSight(entity)) {
                        closestDistance = distanceToEntity;
                        targetPlayer = (Player) entity;
                    }
                }
            }
        }

        if (targetPlayer != null) {
            // Check if it's the same target as before to avoid spam
            UUID playerUuid = player.getUniqueId();
            UUID targetUuid = targetPlayer.getUniqueId();
            UUID lastTarget = lastTargets.get(playerUuid);

            if (lastTarget != null && lastTarget.equals(targetUuid)) {
                return;
            }

            try {
                // Format and show ping message
                Player finalTarget = targetPlayer;
                String pingMessage = guiManager.getFormattedPing(finalTarget);

                // Send action bar message
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(pingMessage));

                // Update tracking
                lastTargets.put(playerUuid, targetUuid);
                lastActionBarTime.put(playerUuid, now);
            } catch (Exception e) {
                logger.warning("Error displaying ping message: " + e.getMessage());
            }
        } else {
            // Clear last target if not looking at a player
            lastTargets.remove(player.getUniqueId());
        }
    }

    /**
     * Handles player interactions with other players.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!configManager.isStatsGuiEnabled()) {
            return;
        }

        // Only handle right clicks, main hand, and not spectator mode
        if (event.getHand() != EquipmentSlot.HAND ||
                event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Player player = event.getPlayer();
        Entity target = event.getRightClicked();

        // Check if clicking a player with an empty hand
        if (target instanceof Player &&
                player.getInventory().getItemInMainHand().getType() == Material.AIR) {
            Player targetPlayer = (Player) target;

            // Check if stats GUI is enabled in this world
            if (!configManager.isStatsEnabledInWorld(player.getWorld().getName())) {
                return; // Silently do nothing
            }

            // Prevent rapid clicks by using a cooldown
            long now = System.currentTimeMillis();
            UUID playerUuid = player.getUniqueId();
            Long lastClick = lastActionBarTime.get(playerUuid);

            if (lastClick != null && (now - lastClick) < 500) { // 500ms cooldown
                event.setCancelled(true);
                return;
            }

            // Update last click time
            lastActionBarTime.put(playerUuid, now);

            // Open stats GUI
            guiManager.openStatsGui(player, targetPlayer);

            // Cancel the interaction to prevent any other plugin from handling it
            event.setCancelled(true);
        }
    }

    /**
     * Handles player join events.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        statsManager.playerJoin(event.getPlayer());
    }

    /**
     * Handles player quit events.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Clean up tracking maps
        lastTargets.remove(uuid);
        lastActionBarTime.remove(uuid);

        // Update stats
        statsManager.playerQuit(player);
    }

    /**
     * Stops all tasks and performs cleanup.
     */
    public void shutdown() {
        if (crosshairTask != null && !crosshairTask.isCancelled()) {
            crosshairTask.cancel();
        }

        lastTargets.clear();
        lastActionBarTime.clear();
    }
}