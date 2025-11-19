package com.marcpg.pillarperil.game.util;

import com.marcpg.pillarperil.game.Lobby;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LobbyManager {
    public static final List<Lobby> LOBBIES = new ArrayList<>();

    public static @Nullable Lobby lobby(String id) {
        for (Lobby lobby : LOBBIES) {
            if (lobby.id().equals(id)) return lobby;
        }
        return null;
    }

    public static @Nullable Lobby lobby(Player player) {
        for (Lobby lobby : LOBBIES) {
            if (lobby.players().contains(player)) return lobby;
        }
        return null;
    }
}

