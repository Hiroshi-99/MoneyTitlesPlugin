package org.cipher.moneyTitles.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.cipher.moneyTitles.config.ConfigManager;
import org.cipher.moneyTitles.manager.StatsManager.PlayerStats;
import org.cipher.moneyTitles.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages GUI creation and handling for the plugin.
 */
public class GuiManager implements Listener {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final StatsManager statsManager;
    private final MoneyFormatter moneyFormatter;

    // Cached inventories to reduce creation overhead
    private final Map<UUID, Long> inventoryExpiration = new HashMap<>();
    private final Map<UUID, Inventory> statsInventories = new HashMap<>();

    // Constants
    private static final long INVENTORY_CACHE_TIME = 1000 * 10; // 10 seconds (reduced from 30 seconds)

    public GuiManager(JavaPlugin plugin, ConfigManager configManager, StatsManager statsManager,
            MoneyFormatter moneyFormatter) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.statsManager = statsManager;
        this.moneyFormatter = moneyFormatter;

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Start cleanup task for expired inventories
        startCleanupTask();
    }

    /**
     * Prevent players from moving items in the stats GUI.
     * 
     * @param event The inventory click event
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the inventory is one of our stats GUIs
        if (isStatsInventory(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent players from dragging items in the stats GUI.
     * 
     * @param event The inventory drag event
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Check if the inventory is one of our stats GUIs
        if (isStatsInventory(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    /**
     * Checks if the given inventory is a stats inventory.
     * 
     * @param inventory The inventory to check
     * @return true if it's a stats inventory, false otherwise
     */
    private boolean isStatsInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }

        // Check if this inventory is in our cache
        for (Inventory cachedInventory : statsInventories.values()) {
            if (inventory.equals(cachedInventory)) {
                return true;
            }
        }

        // Check if title matches our format
        try {
            // In newer versions, we need to use the viewer's locale
            String title = null;
            if (!inventory.getViewers().isEmpty()) {
                HumanEntity viewer = inventory.getViewers().get(0);
                title = viewer.getOpenInventory().getTitle();
            }

            if (title != null) {
                // Get the base title without player name
                String baseTitle = ChatColor.stripColor(configManager.getStatsGuiTitle().replace("%player%", ""));
                return ChatColor.stripColor(title).contains(baseTitle);
            }
        } catch (Exception e) {
            logger.warning("Error checking inventory title: " + e.getMessage());
        }

        return false;
    }

    /**
     * Starts a task to clean up expired inventory caches.
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            List<UUID> toRemove = inventoryExpiration.entrySet().stream()
                    .filter(entry -> entry.getValue() < now)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            if (!toRemove.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (UUID uuid : toRemove) {
                        inventoryExpiration.remove(uuid);
                        statsInventories.remove(uuid);
                    }
                });
            }
        }, 20 * 60, 20 * 60); // Run every minute
    }

    /**
     * Plays a sound for a player with proper error handling.
     * 
     * @param player The player to play the sound for
     * @param sound  The sound to play
     * @param volume The volume of the sound
     * @param pitch  The pitch of the sound
     */
    private void playSoundSafely(Player player, Sound sound, float volume, float pitch) {
        if (sound == null || !player.isOnline()) {
            return;
        }

        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            logger.warning(
                    "Failed to play sound " + sound.name() + " for player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Opens the stats GUI for a player.
     *
     * @param viewer The player viewing the GUI
     * @param target The player whose stats are being viewed
     */
    public void openStatsGui(Player viewer, Player target) {
        if (viewer == null || target == null || !viewer.isOnline()) {
            return;
        }

        UUID targetUuid = target.getUniqueId();
        Long expiration = inventoryExpiration.get(targetUuid);

        if (expiration != null && expiration > System.currentTimeMillis() && statsInventories.containsKey(targetUuid)) {
            // Use cached inventory
            Inventory cachedInventory = statsInventories.get(targetUuid);
            viewer.openInventory(cachedInventory);

            // Play sound after opening the inventory
            if (configManager.getStatsGuiOpenSound() != null) {
                playSoundSafely(
                        viewer,
                        configManager.getStatsGuiOpenSound(),
                        configManager.getStatsGuiOpenSoundVolume(),
                        configManager.getStatsGuiOpenSoundPitch());
            }
        } else {
            // Create new inventory
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // Get the stats
                PlayerStats stats = statsManager.getPlayerStats(targetUuid);
                double currentMoney = statsManager.getCurrentMoney(target);

                // Create inventory on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!viewer.isOnline()) {
                        return; // Player logged off while async task was running
                    }

                    Inventory inventory = createStatsInventory(target, stats, currentMoney);

                    // Cache the inventory
                    statsInventories.put(targetUuid, inventory);
                    inventoryExpiration.put(targetUuid, System.currentTimeMillis() + INVENTORY_CACHE_TIME);

                    // Open for viewer
                    viewer.openInventory(inventory);

                    // Play sound after opening the inventory
                    if (configManager.getStatsGuiOpenSound() != null) {
                        playSoundSafely(
                                viewer,
                                configManager.getStatsGuiOpenSound(),
                                configManager.getStatsGuiOpenSoundVolume(),
                                configManager.getStatsGuiOpenSoundPitch());
                    }
                });
            });
        }
    }

    /**
     * Creates the stats inventory for a player.
     *
     * @param target       The player whose stats are being viewed
     * @param stats        The stats to display
     * @param currentMoney The player's current money
     * @return The created inventory
     */
    private Inventory createStatsInventory(Player target, PlayerStats stats, double currentMoney) {
        if (target == null || stats == null) {
            logger.warning("Cannot create stats inventory: target or stats is null");
            return Bukkit.createInventory(null, 27, "§8Player Stats");
        }

        // Log the money value for debugging
        logger.info("Creating stats GUI for " + target.getName() + " with money: " + currentMoney);

        String title = configManager.getStatsGuiTitle().replace("%player%", target.getName());
        Inventory inventory = Bukkit.createInventory(null, 27, title);

        try {
            // Player head in center
            ItemStack head = createPlayerHead(target);
            inventory.setItem(4, head);

            // Format statistics with proper formatting
            String killsStr = String.valueOf(stats.getKills());
            String deathsStr = String.valueOf(stats.getDeaths());
            String playtimeStr = stats.getFormattedPlaytime();
            String moneyStr = moneyFormatter.formatMoney(currentMoney);
            String moneyGainedStr = moneyFormatter.formatMoney(stats.getMoneyGained());

            // Log formatted values for debugging
            logger.info("Formatted money value: " + moneyStr);

            // Kills (Emerald)
            ItemStack kills = createItem(
                    Material.EMERALD,
                    "&a&lKills",
                    Arrays.asList(
                            "&7Total kills: &a" + killsStr,
                            "&7",
                            "&7These are the kills you've",
                            "&7accumulated during PvP battles."));
            inventory.setItem(10, kills);

            // Deaths (Bone)
            ItemStack deaths = createItem(
                    Material.BONE,
                    "&c&lDeaths",
                    Arrays.asList(
                            "&7Total deaths: &c" + deathsStr,
                            "&7",
                            "&7These are the times you've fallen",
                            "&7in battle against other players."));
            inventory.setItem(12, deaths);

            // Current money (Gold Ingot)
            ItemStack money = createItem(
                    Material.GOLD_INGOT,
                    "&6&lCurrent Money",
                    Arrays.asList(
                            "&7Balance: &6" + moneyStr,
                            "&7",
                            "&7Your current monetary wealth",
                            "&7on this server."));
            inventory.setItem(14, money);

            // Money earned (Diamond)
            ItemStack moneyEarned = createItem(
                    Material.DIAMOND,
                    "&b&lMoney Earned",
                    Arrays.asList(
                            "&7Total earned: &b" + moneyGainedStr,
                            "&7",
                            "&7Money you've earned from",
                            "&7killing other players."));
            inventory.setItem(16, moneyEarned);

            // Playtime (Clock)
            ItemStack playtime = createItem(
                    Material.CLOCK,
                    "&e&lPlaytime",
                    Arrays.asList(
                            "&7Time played: &e" + playtimeStr,
                            "&7",
                            "&7The amount of time you've spent",
                            "&7on this server."));
            inventory.setItem(22, playtime);

            // Fill empty slots with gray glass panes
            ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
            for (int i = 0; i < inventory.getSize(); i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, filler);
                }
            }
        } catch (Exception e) {
            logger.warning("Error creating stats inventory: " + e.getMessage());
        }

        return inventory;
    }

    /**
     * Creates a player head ItemStack.
     *
     * @param player The player
     * @return ItemStack of the player's head
     */
    private ItemStack createPlayerHead(Player player) {
        if (player == null) {
            return new ItemStack(Material.PLAYER_HEAD);
        }

        try {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            if (meta != null) {
                meta.setOwningPlayer(player);
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&l" + player.getName()));

                // Format ping with color based on value
                int ping = getPing(player);
                String pingColor = getPingColor(ping);

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.translateAlternateColorCodes('&', "&7Ping: " + pingColor + ping + "ms"));
                lore.add(ChatColor.translateAlternateColorCodes('&', "&7"));
                lore.add(ChatColor.translateAlternateColorCodes('&', "&7Statistics for:"));
                lore.add(ChatColor.translateAlternateColorCodes('&', "&e" + player.getName()));

                meta.setLore(lore);
                head.setItemMeta(meta);
            }

            return head;
        } catch (Exception e) {
            logger.warning("Error creating player head: " + e.getMessage());
            return new ItemStack(Material.PLAYER_HEAD);
        }
    }

    /**
     * Get color formatting based on ping value.
     * 
     * @param ping The ping value in ms
     * @return Colored string for the ping
     */
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

    /**
     * Creates an ItemStack with the specified properties.
     *
     * @param material The material
     * @param name     The display name
     * @param lore     The lore lines
     * @return The created ItemStack
     */
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            if (lore != null) {
                List<String> coloredLore = lore.stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                meta.setLore(coloredLore);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Gets a player's current ping.
     *
     * @param player The player
     * @return The player's ping in milliseconds
     */
    public int getPing(Player player) {
        if (player == null) {
            return 0;
        }

        try {
            // Use built-in method first (1.16+)
            return player.getPing();
        } catch (Exception ignored) {
            // Fallback to reflection for older versions
            try {
                Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
                return (int) entityPlayer.getClass().getField("ping").get(entityPlayer);
            } catch (Exception e) {
                logger.warning("Failed to get ping for player " + player.getName() + ": " + e.getMessage());
                return 0;
            }
        }
    }

    /**
     * Creates a formatted ping message for a player.
     *
     * @param target The target player
     * @return Formatted ping message
     */
    public String getFormattedPing(Player target) {
        return configManager.getPingFormat()
                .replace("%player%", target.getName())
                .replace("%ping%", String.valueOf(getPing(target)));
    }

    /**
     * Handle inventory close event - play a sound when the player closes the GUI.
     * 
     * @param event The inventory close event
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Check if the inventory is one of our stats GUIs
        if (isStatsInventory(event.getInventory()) && event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();

            // Play a closing sound if enabled
            Sound closeSound = configManager.getStatsGuiOpenSound(); // Reuse the same sound for closing
            if (closeSound != null) {
                // Use a different pitch for closing sound to distinguish it
                float closePitch = Math.max(0.5f, configManager.getStatsGuiOpenSoundPitch() - 0.3f);
                playSoundSafely(player, closeSound, configManager.getStatsGuiOpenSoundVolume() * 0.8f, closePitch);
            }
        }
    }
}