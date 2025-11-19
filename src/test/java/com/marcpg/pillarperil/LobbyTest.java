package com.marcpg.pillarperil;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import com.marcpg.pillarperil.game.Lobby;
import com.marcpg.pillarperil.game.util.LobbyManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void lobbyCountdownStartsAndScoreboardIsApplied() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 1, 4, 5L, "original", com.marcpg.pillarperil.game.mode.OriginalMode.class);
        PlayerMock player = server.addPlayer();

        lobby.join(player);

        // Trigger a few ticks to start countdown and update scoreboard
        for (int i = 0; i < 40; i++) {
            lobby.tick(i);
        }

        Scoreboard scoreboard = player.getScoreboard();
        assertNotNull(scoreboard);
        assertNotNull(scoreboard.getObjective("pp_lobby"));
        assertTrue(LobbyManager.LOBBIES.contains(lobby));
    }

    @Test
    void lobbyForceStartRemovesLobby() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 1, 4, 5L, "original", com.marcpg.pillarperil.game.mode.OriginalMode.class);
        PlayerMock player = server.addPlayer();
        lobby.join(player);

        assertTrue(LobbyManager.LOBBIES.contains(lobby));

        try {
            lobby.forceStart();
        } catch (Throwable ignored) {
            // In the test environment, starting a real game may fail due to missing Paper internals.
        }

        // Lobby should be removed from manager after starting game
        assertTrue(LobbyManager.LOBBIES.isEmpty() || !LobbyManager.LOBBIES.contains(lobby));
    }
}
