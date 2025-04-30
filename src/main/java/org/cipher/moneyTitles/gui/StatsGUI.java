package org.cipher.moneyTitles.gui;

// These imports will be automatically relocated to org.cipher.moneyTitles.libs.inventorygui
// during the build process via Maven Shade plugin
import de.themoep.inventorygui.GuiElement;
import de.themoep.inventorygui.InventoryGui;
import de.themoep.inventorygui.StaticGuiElement;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.cipher.moneyTitles.MoneyTitles;
import org.cipher.moneyTitles.config.ConfigManager;
import org.cipher.moneyTitles.manager.StatsManager;
import org.cipher.moneyTitles.manager.StatsManager.PlayerStats;
import org.cipher.moneyTitles.util.MoneyFormatter;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A GUI for displaying player statistics.
 * Uses the InventoryGui library (https://github.com/Phoenix616/InventoryGui)
 * which is shaded into the plugin jar with relocated packages.
 */
public class StatsGUI {

    private final MoneyTitles plugin;
    private final StatsManager statsManager;
    private final ConfigManager configManager;
    private final MoneyFormatter moneyFormatter;
    private final InventoryGui gui;
    private final Player owner;
    private final Player target;

    /**
     * Creates a new StatsGUI instance.
     *
     * @param plugin The plugin instance
     * @param owner  The player viewing the GUI
     * @param target The player whose stats are being viewed
     */
    public StatsGUI(MoneyTitles plugin, Player owner, Player target) {
        this.plugin = plugin;
        this.statsManager = plugin.getStatsManager();
        this.configManager = plugin.getConfigManager();
        this.moneyFormatter = plugin.getMoneyFormatter();
        this.owner = owner;
        this.target = target;

        // Check if stats GUI is allowed in this world
        if (!configManager.isStatsEnabledInWorld(owner.getWorld().getName())) {
            owner.sendMessage(ChatColor.RED + "Stats are not available in this world.");
            this.gui = null;
            return;
        }

        // Define the GUI layout - a 3x9 inventory
        String[] guiSetup = {
                "         ",
                "  a b c  ",
                "  d e f  "
        };

        // Create the GUI with the specified title
        String title = ChatColor.DARK_PURPLE + target.getName() + "'s Statistics";
        this.gui = new InventoryGui(plugin, owner, title, guiSetup);

        // Customize appearance
        gui.setFiller(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));

        // Create and add elements
        setupGuiElements();

        // Set close action
        gui.setCloseAction(close -> {
            // Nothing special to do on close
            return true;
        });
    }

    /**
     * Sets up the GUI elements.
     */
    private void setupGuiElements() {
        PlayerStats stats = statsManager.getPlayerStats(target.getUniqueId());
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

        // Player head in the center top
        gui.addElement(new StaticGuiElement('a',
                createPlayerHead(),
                click -> true,
                ChatColor.GOLD + "§l" + target.getName() + "'s Stats",
                ChatColor.GRAY + "§m--------------------------",
                ChatColor.GRAY + "View detailed statistics for " + ChatColor.YELLOW + target.getName()));

        // Kills (Emerald)
        gui.addElement(new StaticGuiElement('b',
                new ItemStack(Material.DIAMOND_SWORD),
                click -> true,
                ChatColor.GREEN + "§l[1m[32m» Kills: " + ChatColor.WHITE + numberFormat.format(stats.getKills()),
                ChatColor.DARK_GRAY + "Total kills in PvP battles"));

        // Deaths (Bone)
        gui.addElement(new StaticGuiElement('c',
                new ItemStack(Material.BONE),
                click -> true,
                ChatColor.RED + "§l[1m[31m» Deaths: " + ChatColor.WHITE + numberFormat.format(stats.getDeaths()),
                ChatColor.DARK_GRAY + "Times fallen in battle"));

        // Current money (Gold Ingot)
        gui.addElement(new StaticGuiElement('d',
                new ItemStack(Material.GOLD_INGOT),
                click -> true,
                ChatColor.GOLD + "§l[1m[33m» Current Money: " + ChatColor.YELLOW
                        + moneyFormatter.formatMoney(statsManager.getCurrentMoney(target)),
                ChatColor.DARK_GRAY + "Current monetary wealth"));

        // Money earned (Diamond)
        gui.addElement(new StaticGuiElement('e',
                new ItemStack(Material.DIAMOND),
                click -> true,
                ChatColor.AQUA + "§l[1m[36m» Money Earned: " + ChatColor.WHITE
                        + moneyFormatter.formatMoney(stats.getMoneyGained()),
                ChatColor.DARK_GRAY + "Money earned from PvP"));

        // Playtime (Clock)
        gui.addElement(new StaticGuiElement('f',
                new ItemStack(Material.CLOCK),
                click -> true,
                ChatColor.YELLOW + "§l[1m[33m» Playtime: " + ChatColor.WHITE + getPrettyPlaytime(stats.getPlaytime()),
                ChatColor.DARK_GRAY + "Time spent on this server"));
    }

    /**
     * Creates a player head ItemStack.
     *
     * @return ItemStack of the player's head
     */
    private ItemStack createPlayerHead() {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    // Helper for pretty playtime
    private String getPrettyPlaytime(long playtimeMinutes) {
        long hours = playtimeMinutes / 60;
        long minutes = playtimeMinutes % 60;
        if (hours > 0 && minutes > 0) {
            return hours + "h " + minutes + "m";
        } else if (hours > 0) {
            return hours + "h";
        } else {
            return minutes + "m";
        }
    }

    /**
     * Opens the GUI for the owner.
     */
    public void open() {
        if (gui != null) {
            gui.show(owner);
        }
    }
}