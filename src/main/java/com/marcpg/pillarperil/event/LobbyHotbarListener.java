package com.marcpg.pillarperil.event;

import com.marcpg.libpg.lang.Translation;
import com.marcpg.pillarperil.PillarPeril;
import com.marcpg.pillarperil.game.Lobby;
import com.marcpg.pillarperil.game.util.LobbyManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class LobbyHotbarListener implements Listener {

    private boolean isLobbyHotbarItem(ItemStack stack) {
        return Lobby.hotbarAction(stack) != null;
    }

    private void purgeLobbyHotbarItems(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isLobbyHotbarItem(contents[i])) {
                contents[i] = null;
            }
        }
        inv.setContents(contents);

        ItemStack offhand = inv.getItemInOffHand();
        if (isLobbyHotbarItem(offhand)) {
            inv.setItemInOffHand(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHotbarInteract(@NotNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            return;
        }

        Player player = event.getPlayer();
        Lobby lobby = LobbyManager.lobby(player);
        if (lobby == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack main = inventory.getItemInMainHand();
        ItemStack off = inventory.getItemInOffHand();

        Lobby.HotbarAction mainAction = Lobby.hotbarAction(main);
        Lobby.HotbarAction offAction = Lobby.hotbarAction(off);

        // If either hand holds the LEAVE item, always treat the action as LEAVE,
        // regardless of what was technically clicked.
        Lobby.HotbarAction hotbarAction;
        if (mainAction == Lobby.HotbarAction.LEAVE || offAction == Lobby.HotbarAction.LEAVE) {
            hotbarAction = Lobby.HotbarAction.LEAVE;
        } else if (mainAction != null) {
            hotbarAction = mainAction;
        } else {
            hotbarAction = offAction;
        }

        if (hotbarAction == null) {
            return;
        }

        event.setCancelled(true);
        Locale locale = PillarPeril.locale(player);

        switch (hotbarAction) {
            case START -> handleStart(player, lobby, locale);
            case LEAVE -> handleLeave(player, lobby, locale);
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onHotbarSwing(@NotNull PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        Lobby lobby = LobbyManager.lobby(player);
        if (lobby == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack main = inventory.getItemInMainHand();
        ItemStack off = inventory.getItemInOffHand();

        if (Lobby.hotbarAction(main) == Lobby.HotbarAction.LEAVE || Lobby.hotbarAction(off) == Lobby.HotbarAction.LEAVE) {
            Locale locale = PillarPeril.locale(player);
            handleLeave(player, lobby, locale);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean currentIsHotbar = isLobbyHotbarItem(current);
        boolean cursorIsHotbar = isLobbyHotbarItem(cursor);

        if (!currentIsHotbar && !cursorIsHotbar) {
            return;
        }

        Lobby lobby = LobbyManager.lobby(player);
        if (lobby != null) {
            event.setCancelled(true);
            if (cursorIsHotbar) {
                event.setCursor(null);
            }
        } else {
            event.setCancelled(true);
            if (cursorIsHotbar) {
                event.setCursor(null);
            }
            purgeLobbyHotbarItems(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean involvesHotbarItem = isLobbyHotbarItem(event.getOldCursor());
        if (!involvesHotbarItem) {
            for (ItemStack stack : event.getNewItems().values()) {
                if (isLobbyHotbarItem(stack)) {
                    involvesHotbarItem = true;
                    break;
                }
            }
        }

        if (!involvesHotbarItem) {
            return;
        }

        Lobby lobby = LobbyManager.lobby(player);
        event.setCancelled(true);
        player.setItemOnCursor(null);

        if (lobby == null) {
            purgeLobbyHotbarItems(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropHotbarItem(@NotNull PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = event.getItemDrop().getItemStack();
        if (!isLobbyHotbarItem(stack)) {
            return;
        }

        Lobby lobby = LobbyManager.lobby(player);
        event.setCancelled(true);
        event.getItemDrop().remove();

        if (lobby == null) {
            purgeLobbyHotbarItems(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickupHotbarItem(@NotNull EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }

        ItemStack stack = event.getItem().getItemStack();
        if (!isLobbyHotbarItem(stack)) {
            return;
        }

        event.setCancelled(true);
        event.getItem().remove();

        Lobby lobby = LobbyManager.lobby(player);
        if (lobby == null) {
            purgeLobbyHotbarItems(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(@NotNull PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack main = event.getMainHandItem();
        ItemStack off = event.getOffHandItem();

        boolean mainHotbar = isLobbyHotbarItem(main);
        boolean offHotbar = isLobbyHotbarItem(off);

        if (!mainHotbar && !offHotbar) {
            return;
        }

        Lobby lobby = LobbyManager.lobby(player);
        event.setCancelled(true);

        if (lobby == null) {
            purgeLobbyHotbarItems(player);
        }
    }

    private void handleStart(Player player, Lobby lobby, Locale locale) {
        if (!player.hasPermission("pillarperil.lobby.force-start") && !player.hasPermission("pillarperil.lobby.start")) {
            player.sendMessage(Translation.component(locale, "games.lobby.hotbar.no_permission").color(NamedTextColor.RED));
            return;
        }

        if (lobby.state() == Lobby.LobbyState.DISABLED || lobby.currentGame() != null) {
            player.sendMessage(Translation.component(locale, "games.lobby.hotbar.start.unavailable").color(NamedTextColor.RED));
            return;
        }

        if (lobby.players().size() < lobby.minPlayers()) {
            player.sendMessage(Translation.component(locale, "games.lobby.hotbar.start.not_enough").color(NamedTextColor.RED));
            return;
        }

        lobby.forceStart();
        player.sendMessage(Translation.component(locale, "games.lobby.hotbar.start.success").color(NamedTextColor.GREEN));
    }

    private void handleLeave(Player player, Lobby lobby, Locale locale) {
        lobby.leave(player);
        player.teleport(PillarPeril.endSpawn(player.getWorld()));
        player.sendMessage(Translation.component(locale, "games.lobby.leave.success").color(NamedTextColor.YELLOW));
    }
}
