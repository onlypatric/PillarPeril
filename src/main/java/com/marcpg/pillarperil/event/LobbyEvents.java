package com.marcpg.pillarperil.event;

import com.marcpg.libpg.lang.Translation;
import com.marcpg.pillarperil.PillarPeril;
import com.marcpg.pillarperil.game.util.LobbyManager;
import com.marcpg.pillarperil.game.util.GameManager;
import com.marcpg.pillarperil.game.Lobby;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class LobbyEvents implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || !(clicked.getState() instanceof Sign sign)) {
            return;
        }

        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        String header = plain.serialize(sign.getSide(Side.FRONT).line(0));
        if (!"[PillarPeril]".equalsIgnoreCase(header)) {
            return;
        }

        String id = plain.serialize(sign.getSide(Side.FRONT).line(1));
        if (id == null || id.isBlank()) {
            return;
        }

        Player player = event.getPlayer();
        Locale l = PillarPeril.locale(player);

        if (GameManager.game(player, true) != null) {
            player.sendMessage(Translation.component(l, "games.lobby.join.in_game").color(NamedTextColor.RED));
            return;
        }

        Lobby lobby = LobbyManager.lobby(id.trim());
        if (lobby == null) {
            player.sendMessage(Translation.component(l, "games.lobby.join.not_found").color(NamedTextColor.RED));
            return;
        }

        if (LobbyManager.lobby(player) != null) {
            player.sendMessage(Translation.component(l, "games.lobby.join.already_in_lobby").color(NamedTextColor.RED));
            return;
        }

        Lobby.JoinResult result = lobby.join(player);
        switch (result) {
            case SUCCESS -> player.sendMessage(Translation.component(l, "games.lobby.join.success").color(NamedTextColor.GREEN));
            case FULL -> player.sendMessage(Translation.component(l, "games.lobby.join.full").color(NamedTextColor.RED));
            case DISABLED -> player.sendMessage(Translation.component(l, "games.lobby.join.disabled").color(NamedTextColor.RED));
            case IN_GAME -> player.sendMessage(Translation.component(l, "games.lobby.join.lobby_in_game").color(NamedTextColor.RED));
            case ALREADY_PRESENT -> player.sendMessage(Translation.component(l, "games.lobby.join.already_in_lobby").color(NamedTextColor.YELLOW));
        }
    }
}
