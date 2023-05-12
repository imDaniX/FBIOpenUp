package me.imdanix.fbiopenup;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand;

public final class FbiPlugin extends JavaPlugin implements Listener {
    private final NamespacedKey key = Objects.requireNonNull(NamespacedKey.fromString("search_glass", this));
    private final Map<String, String> messages = new HashMap<>();

    private ItemStack searchGlass;
    private double maxDistanceSquared;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reload();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void reload() {
        reloadConfig();
        var cfg = getConfig();
        maxDistanceSquared = cfg.getDouble("distance", 2);
        maxDistanceSquared *= maxDistanceSquared;
        messages.clear();
        var messagesCfg = Optional.ofNullable(cfg.getConfigurationSection("messages")).orElseGet(() -> cfg.createSection("messages"));
        for (String key : messagesCfg.getKeys(false)) {
            messages.put(key, messagesCfg.getString(key, "&4ERROR"));
        }
        searchGlass = new ItemStack(Optional.ofNullable(
                Material.getMaterial(cfg.getString("item.type", "SPYGLASS").toUpperCase(Locale.ROOT))
        ).orElse(Material.SPYGLASS));
        searchGlass.editMeta((meta) -> {
            meta.displayName(legacyAmpersand().deserialize(cfg.getString("item.name", "&bЛупа")));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        try {
            onCommand(sender, command.getName(), args);
        } catch (CommandException ex) {
            sender.sendMessage(message(ex.getMessage()));
        }
        return true;
    }

    private void onCommand(CommandSender sender, String command, String[] args) {
        switch (command) {
            case "search" -> {
                if (args.length == 0) {
                    throw new CommandException("no-args");
                }
                new LockedView(
                        requirePlayer(sender, "console-disallowed"),
                        requirePlayer(getServer().getPlayerExact(args[0]), "player-missing")
                ).open();
            }
            case "getsearchitem" -> {
                requirePlayer(sender, "console-disallowed").getInventory().addItem(searchGlass);
            }
            case "fbiopenup" -> {
                reload();
                sender.sendMessage(message("reloaded"));
            }
        }
    }

    private @NotNull Player requirePlayer(CommandSender sender, String messageId) {
        if (sender instanceof Player player) return player;
        throw new CommandException(messageId);
    }

    private @NotNull Component message(String messageId) {
        return legacyAmpersand().deserialize(messages.getOrDefault(messageId, "&4ERROR"));
    }

    @EventHandler
    public void onClick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player clicked)) return;
        Player watcher = event.getPlayer();
        if (
                isSearchGlass(watcher.getInventory().getItem(event.getHand())) &&
                watcher.hasPermission("fbiopenup.search.item")
        ) {
            new LockedView(watcher, clicked).open();
        }
    }

    private boolean isSearchGlass(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(key);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent event) {
        if (event.getView() instanceof LockedView) {
            event.setCancelled(true);
        }
    }

    private class LockedView extends InventoryView {
        private final Player viewed;
        private final Player watcher;
        private String customTitle;

        private LockedView(Player watcher, Player viewed) {
            this.viewed = viewed;
            this.watcher = watcher;
        }

        public void open() {
            if (viewed.hasPermission("fbiopenup.exempt")) {
                watcher.sendMessage(message("player-exempted"));
                return;
            }
            if (watcher.hasPermission("fbiopenup.unlimited") || (
                    watcher.getWorld().equals(viewed.getWorld()) &&
                    watcher.getEyeLocation().distanceSquared(viewed.getEyeLocation()) <= maxDistanceSquared
            )) {
                watcher.closeInventory();
                watcher.openInventory(this);
            } else {
                watcher.sendMessage(message("too-far"));
            }
        }

        @Override
        public @NotNull Inventory getTopInventory() {
            return viewed.getInventory();
        }

        @Override
        public @NotNull Inventory getBottomInventory() {
            return watcher.getInventory();
        }

        @Override
        public @NotNull HumanEntity getPlayer() {
            return watcher;
        }

        @Override
        public @NotNull InventoryType getType() {
            return InventoryType.PLAYER;
        }

        @Override
        public @NotNull String getTitle() {
            return customTitle == null ? (customTitle = viewed.getName()) : customTitle;
        }

        @Override
        public void setTitle(@NotNull String title) {
            this.customTitle = title;
        }

        @Override
        public @NotNull String getOriginalTitle() {
            return viewed.getName();
        }
    }
}
