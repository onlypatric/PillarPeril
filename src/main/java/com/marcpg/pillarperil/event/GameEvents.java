package com.marcpg.pillarperil.event;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.marcpg.pillarperil.game.Game;
import com.marcpg.pillarperil.game.util.GameManager;
import com.marcpg.pillarperil.game.util.LobbyManager;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.jetbrains.annotations.NotNull;

public class GameEvents implements Listener {
    @EventHandler
    public void onServerTickEnd(ServerTickEndEvent event) {
        GameManager.GAMES.forEach(game -> game.tick(event.getTickNumber()));
        LobbyManager.LOBBIES.forEach(lobby -> lobby.tick(event.getTickNumber()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        Game game = GameManager.game(event.getPlayer(), true);
        if (game != null)
            game.addBlock(event.getBlockPlaced().getLocation(), event.getBlockReplacedState().getBlockData());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        Game game = GameManager.game(event.getPlayer(), true);
        if (game != null) {
            game.addBlock(event.getBlock().getLocation(), event.getBlock().getBlockData());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(@NotNull PlayerBucketEmptyEvent event) {
        Game game = GameManager.game(event.getPlayer(), true);
        if (game == null) {
            return;
        }

        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        game.addBlock(target.getLocation(), target.getBlockData());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(@NotNull BlockFromToEvent event) {
        if (GameManager.GAMES.isEmpty()) {
            return;
        }

        Block source = event.getBlock();
        Block target = event.getToBlock();

        Game nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Game game : GameManager.GAMES) {
            if (game.world != source.getWorld()) {
                continue;
            }
            double distance = game.center.distanceSquared(source.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = game;
            }
        }

        if (nearest != null) {
            nearest.addBlock(target.getLocation(), target.getBlockData());
        }
    }
}
