package com.marcpg.pillarperil.event;

import com.marcpg.libpg.lang.Translation;
import com.marcpg.pillarperil.PillarPeril;
import com.marcpg.pillarperil.game.Lobby;
import com.marcpg.pillarperil.game.util.LobbyManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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

        // Some client interactions (e.g., clicking air or using off-hand) report null items.
        var stack = event.getItem();
        if (stack == null) {
            stack = player.getInventory().getItemInMainHand();
            if (stack == null || stack.getType().isAir()) {
                stack = player.getInventory().getItemInOffHand();
            }
        }

        Lobby.HotbarAction hotbarAction = Lobby.hotbarAction(stack);
        if (hotbarAction == null) {
            return;
        }

        event.setCancelled(true);
        Locale locale = PillarPeril.locale(player);

        switch (hotbarAction) {
            case START -> handleStart(player, lobby, locale);
            case QUEUE -> handleQueue(player, lobby, locale);
            case LEAVE -> handleLeave(player, lobby, locale);
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

    private void handleQueue(Player player, Lobby lobby, Locale locale) {
        boolean ready = lobby.toggleReady(player);
        if (ready) {
            player.sendMessage(Translation.component(locale, "games.lobby.hotbar.queue.ready_confirm").color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Translation.component(locale, "games.lobby.hotbar.queue.unready_confirm").color(NamedTextColor.YELLOW));
        }
    }
}
