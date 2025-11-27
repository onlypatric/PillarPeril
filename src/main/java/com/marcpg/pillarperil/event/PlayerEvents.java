package com.marcpg.pillarperil.event;

import com.marcpg.pillarperil.PillarPlayer;
import com.marcpg.pillarperil.game.Game;
import com.marcpg.pillarperil.game.Lobby;
import com.marcpg.pillarperil.game.util.GameManager;
import com.marcpg.pillarperil.game.util.LobbyManager;
import com.marcpg.pillarperil.game.util.StatsManager;
import com.marcpg.pillarperil.generation.Platform;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerEvents implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        PillarPlayer player = GameManager.player(event.getPlayer(), false);
        if (player == null || player.game == null) {
            return;
        }

        Game game = player.game;
        // Only handle deaths for players that are currently alive in a game
        if (!game.players().contains(player)) {
            return;
        }

        if (player.player.getKiller() != null) {
            PillarPlayer killer = game.player(player.player.getKiller(), false);
            if (killer != null) {
                killer.addKill();
                StatsManager.recordKill(killer);
            }
        }

        game.eliminate(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        if (event.getTo().y() < Platform.deathHeight) {
            Game game = GameManager.game(event.getPlayer(), true);
            if (game != null)
                event.getPlayer().setHealth(0.0);
        }
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Lobby lobby = LobbyManager.lobby(event.getPlayer());
        if (lobby != null) {
            lobby.leave(event.getPlayer());
        }
    }
}
