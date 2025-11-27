package com.marcpg.pillarperil.event;

import com.marcpg.libpg.lang.Translation;
import com.marcpg.pillarperil.PillarPeril;
import com.marcpg.pillarperil.game.Lobby;
import com.marcpg.pillarperil.game.util.LobbyManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class LobbyHotbarListener implements Listener {

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

        ItemStack stack = resolveInteractedItem(event);
        if (stack == null || stack.getType().isAir()) {
            return;
        }

        Lobby.HotbarAction hotbarAction = Lobby.hotbarAction(stack);
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

    private ItemStack resolveInteractedItem(PlayerInteractEvent event) {
        ItemStack stack = event.getItem();
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        EquipmentSlot hand = event.getHand();

        if (stack == null || stack.getType().isAir()) {
            if (hand == EquipmentSlot.OFF_HAND) {
                stack = inventory.getItemInOffHand();
            } else if (hand == EquipmentSlot.HAND) {
                stack = inventory.getItemInMainHand();
            }
        }

        if (stack == null || stack.getType().isAir()) {
            // Fall back to whichever hand currently holds a GUI item.
            ItemStack main = inventory.getItemInMainHand();
            if (Lobby.hotbarAction(main) != null) {
                stack = main;
            } else {
                ItemStack off = inventory.getItemInOffHand();
                if (Lobby.hotbarAction(off) != null) {
                    stack = off;
                }
            }
        }
        return stack;
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
