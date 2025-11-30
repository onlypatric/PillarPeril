package com.marcpg.pillarperil.event;

import com.marcpg.pillarperil.PillarPeril;
import com.marcpg.pillarperil.PillarPlayer;
import com.marcpg.pillarperil.game.Game;
import com.marcpg.pillarperil.game.Lobby;
import com.marcpg.pillarperil.game.util.GameManager;
import com.marcpg.pillarperil.game.util.LobbyManager;
import com.marcpg.pillarperil.game.util.StatsManager;
import com.marcpg.pillarperil.generation.Platform;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        Game game = GameManager.game(event.getPlayer(), false);
        if (game == null) {
            return;
        }

        // Always respawn players participating in a game at the game center,
        // ignoring any bed or anchor spawn they might have set elsewhere.
        event.setRespawnLocation(game.center);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Player bukkitPlayer = event.getPlayer();
        Game game = GameManager.game(bukkitPlayer, true);
        if (game == null) {
            return;
        }

        if (event.getTo().y() < Platform.deathHeight) {
            bukkitPlayer.setHealth(0.0);
            return;
        }

        Location min = PillarPeril.arenaMin();
        Location max = PillarPeril.arenaMax();
        if (min == null || max == null) {
            return;
        }
        if (!min.getWorld().equals(bukkitPlayer.getWorld())) {
            return;
        }

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        Location to = event.getTo();
        int x = to.getBlockX();
        int y = to.getBlockY();
        int z = to.getBlockZ();

        boolean outside =
                x < minX || x > maxX ||
                y < minY || y > maxY ||
                z < minZ || z > maxZ;

        if (outside) {
            PillarPlayer pp = game.player(bukkitPlayer, true);
            if (pp != null) {
                game.eliminate(pp);
            }
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
