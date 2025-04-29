package org.cipher.moneyTitles.listeners;

/*
 * DEPRECATED - DO NOT USE!
 * 
 * This class is no longer needed and should be removed. It was causing duplicate GUI issues.
 * All functionality has been moved to org.cipher.moneyTitles.manager.InteractionManager
 * 
 * Issues fixed:
 * - Duplicate GUI opening when right-clicking players
 * - Player targeting is now properly implemented in InteractionManager
 */

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.cipher.moneyTitles.MoneyTitles;
import org.cipher.moneyTitles.config.ConfigManager;
import org.cipher.moneyTitles.gui.StatsGUI;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerInteractionListener implements Listener {

    private final MoneyTitles plugin;
    private final ConfigManager configManager;
    private final Map<UUID, BukkitTask> pingTasks = new HashMap<>();
    private final Map<UUID, UUID> targetMap = new HashMap<>();

    public PlayerInteractionListener(MoneyTitles plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        startCrosshairTracking();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        // Check if it's a main hand interaction
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        // Check if the player clicked on another player
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Player))
            return;

        Player player = event.getPlayer();
        Player target = (Player) clicked;

        // Check if player has an empty hand
        if (player.getInventory().getItemInMainHand().getType().isAir()) {
            // Check if stats are enabled in this world
            if (!configManager.isStatsEnabledInWorld(player.getWorld().getName())) {
                return; // Silently ignore in worlds where stats are disabled
            }

            // Open stats GUI for the target player
            new StatsGUI(plugin, player, target).open();
            event.setCancelled(true);
        }
    }

    private void startCrosshairTracking() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    // Skip if ping display is not enabled in this world
                    if (!configManager.isStatsEnabledInWorld(player.getWorld().getName())) {
                        continue;
                    }

                    // Get the entity the player is looking at (within 30 blocks)
                    Entity target = getLookedAtEntity(player, 30);

                    if (target instanceof Player) {
                        Player targetPlayer = (Player) target;
                        UUID playerUUID = player.getUniqueId();
                        UUID targetUUID = targetPlayer.getUniqueId();

                        // Player is targeting a different player than before
                        if (!targetUUID.equals(targetMap.get(playerUUID))) {
                            // Cancel previous task if exists
                            if (pingTasks.containsKey(playerUUID)) {
                                pingTasks.get(playerUUID).cancel();
                                pingTasks.remove(playerUUID);
                            }

                            // Start a new ping display task
                            BukkitTask task = new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (!player.isOnline() || !targetPlayer.isOnline()) {
                                        cancel();
                                        pingTasks.remove(playerUUID);
                                        return;
                                    }

                                    int ping = getPing(targetPlayer);
                                    String pingColor = getPingColor(ping);

                                    // Display ping in action bar
                                    player.spigot().sendMessage(
                                            ChatMessageType.ACTION_BAR,
                                            new TextComponent(ChatColor.GRAY + targetPlayer.getName() +
                                                    " - Ping: " + pingColor + ping + "ms"));
                                }
                            }.runTaskTimer(plugin, 0, 10); // Update every half second

                            pingTasks.put(playerUUID, task);
                            targetMap.put(playerUUID, targetUUID);
                        }
                    } else {
                        // Player is not looking at another player
                        UUID playerUUID = player.getUniqueId();
                        if (pingTasks.containsKey(playerUUID)) {
                            pingTasks.get(playerUUID).cancel();
                            pingTasks.remove(playerUUID);
                            targetMap.remove(playerUUID);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 5); // Check every 5 ticks (1/4 second)
    }

    private Entity getLookedAtEntity(Player player, int distance) {
        // Get the direction the player is looking
        Vector direction = player.getLocation().getDirection();
        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;

        // Search through nearby entities
        for (Entity entity : player.getNearbyEntities(distance, distance, distance)) {
            if (entity instanceof Player && !entity.equals(player)) {
                // Calculate the vector from the player to the entity
                Vector toEntity = entity.getLocation().toVector().subtract(player.getLocation().toVector());
                double distanceToEntity = toEntity.length();

                // Normalize both vectors to get direction only
                toEntity.normalize();

                // Calculate dot product to see if entity is in front of player
                double dot = toEntity.dot(direction);

                // Entity is within the player's line of sight (using a threshold angle of ~30
                // degrees)
                if (dot > 0.85 && distanceToEntity < closestDistance) {
                    // Check if there's a direct line of sight
                    if (player.hasLineOfSight(entity)) {
                        closest = entity;
                        closestDistance = distanceToEntity;
                    }
                }
            }
        }

        return closest;
    }

    private int getPing(Player player) {
        try {
            return player.getPing();
        } catch (Exception e) {
            return 0;
        }
    }

    private String getPingColor(int ping) {
        if (ping < 50) {
            return ChatColor.GREEN.toString();
        } else if (ping < 150) {
            return ChatColor.YELLOW.toString();
        } else if (ping < 300) {
            return ChatColor.GOLD.toString();
        } else {
            return ChatColor.RED.toString();
        }
    }
}