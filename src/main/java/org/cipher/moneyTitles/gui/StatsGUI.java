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

import java.util.ArrayList;
import java.util.List;
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

        // Player head in the center top
        gui.addElement(new StaticGuiElement('a',
                createPlayerHead(),
                click -> {
                    return true; // Do nothing on click
                },
                ChatColor.GOLD + target.getName() + "'s Statistics",
                ChatColor.GRAY + "View detailed statistics for " + target.getName()));

        // Kills (Emerald)
        gui.addElement(new StaticGuiElement('b',
                new ItemStack(Material.DIAMOND_SWORD),
                click -> {
                    return true; // Do nothing on click
                },
                ChatColor.YELLOW + "Kills: " + ChatColor.WHITE + stats.getKills(),
                ChatColor.GRAY + "Total kills accumulated during PvP battles"));

        // Deaths (Bone)
        gui.addElement(new StaticGuiElement('c',
                new ItemStack(Material.BONE),
                click -> {
                    return true; // Do nothing on click
                },
                ChatColor.YELLOW + "Deaths: " + ChatColor.WHITE + stats.getDeaths(),
                ChatColor.GRAY + "Times fallen in battle against other players"));

        // Current money (Gold Ingot)
        gui.addElement(new StaticGuiElement('d',
                new ItemStack(Material.GOLD_INGOT),
                click -> {
                    return true; // Do nothing on click
                },
                ChatColor.YELLOW + "Current Money: " + ChatColor.GOLD
                        + moneyFormatter.formatMoney(statsManager.getCurrentMoney(target)),
                ChatColor.GRAY + "Current monetary wealth on this server"));

        // Money earned (Diamond)
        gui.addElement(new StaticGuiElement('e',
                new ItemStack(Material.DIAMOND),
                click -> {
                    return true; // Do nothing on click
                },
                ChatColor.YELLOW + "Money Earned: " + ChatColor.AQUA
                        + moneyFormatter.formatMoney(stats.getMoneyGained()),
                ChatColor.GRAY + "Money earned from killing other players"));

        // Playtime (Clock)
        gui.addElement(new StaticGuiElement('f',
                new ItemStack(Material.CLOCK),
                click -> {
                    return true; // Do nothing on click
                },
                ChatColor.YELLOW + "Playtime: " + ChatColor.WHITE + stats.getFormattedPlaytime(),
                ChatColor.GRAY + "Time spent on this server"));
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

    /**
     * Opens the GUI for the owner.
     */
    public void open() {
        if (gui != null) {
            gui.show(owner);
        }
    }
}