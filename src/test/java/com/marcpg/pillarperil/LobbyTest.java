package com.marcpg.pillarperil;

import com.marcpg.libpg.data.time.Time;
import com.marcpg.pillarperil.game.Game;
import com.marcpg.pillarperil.game.util.GameInfo;
import com.marcpg.pillarperil.generation.generator.CircularPillarGen;
import com.marcpg.pillarperil.generation.platform.BlockPlatform;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import com.marcpg.pillarperil.game.Lobby;
import com.marcpg.pillarperil.game.util.LobbyManager;

import org.bukkit.inventory.ItemStack;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class LobbyTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        com.marcpg.pillarperil.generation.Platform.platformHeight = 20;
        com.marcpg.pillarperil.generation.Platform.deathHeight = 0;
        TestTranslations.ensure();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void lobbyCountdownStartsAndScoreboardIsApplied() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 1, 2, 5L, "test", DummyMode.class);
        PlayerMock player = server.addPlayer();

        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(player));

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
    void lobbyForceStartTransitionsToInGameState() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 1, 4, 5L, "test", DummyMode.class);
        PlayerMock player = server.addPlayer();
        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(player));

        assertTrue(LobbyManager.LOBBIES.contains(lobby));

        lobby.forceStart();

        assertTrue(LobbyManager.LOBBIES.contains(lobby), "Lobby should remain registered after starting a game");
        assertEquals(Lobby.LobbyState.IN_GAME, lobby.state(), "Lobby state should be IN_GAME after force start");
        assertNotNull(lobby.currentGame(), "Lobby should track the current game after starting");
    }

    @Test
    void waitingSpawnOverridesDefaultTeleport() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 1, 2, 5L, "test", DummyMode.class);
        Location waiting = center.clone().add(10, 1, -5);
        lobby.setWaitingSpawn(waiting);

        PlayerMock player = server.addPlayer();
        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(player));
        assertEquals(waiting.getBlockX(), player.getLocation().getBlockX());
        assertEquals(waiting.getBlockY(), player.getLocation().getBlockY());
        assertEquals(waiting.getBlockZ(), player.getLocation().getBlockZ());

        // End a fake game to ensure returns to waiting spawn
        lobby.forceStart();
        assertNotNull(lobby.currentGame());
        lobby.onGameEnded(lobby.currentGame());
        assertEquals(waiting.getBlockX(), player.getLocation().getBlockX());
        assertEquals(waiting.getBlockY(), player.getLocation().getBlockY());
        assertEquals(waiting.getBlockZ(), player.getLocation().getBlockZ());
    }

    @Test
    void lobbyHotbarRestoresInventoryOnLeave() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 1, 4, 5L, "test", DummyMode.class);
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, new ItemStack(Material.STONE));
        ItemStack[] beforeJoin = player.getInventory().getContents().clone();

        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(player));
        ItemStack leaveItem = player.getInventory().getItem(8);
        assertNotNull(leaveItem);
        assertEquals(Material.RED_DYE, leaveItem.getType());
        assertEquals(Lobby.HotbarAction.LEAVE, Lobby.hotbarAction(leaveItem));

        lobby.leave(player);
        assertArrayEquals(beforeJoin, player.getInventory().getContents());
    }

    @Test
    void readyQueueControlsCountdown() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 2, 4, 5L, "test", DummyMode.class);
        PlayerMock alice = server.addPlayer("alice");
        PlayerMock bob = server.addPlayer("bob");

        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(alice));
        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(bob));
        assertEquals(Material.LIME_DYE, alice.getInventory().getItem(7).getType());
        assertEquals(Lobby.HotbarAction.QUEUE, Lobby.hotbarAction(alice.getInventory().getItem(7)));

        assertEquals(Lobby.LobbyState.WAITING, lobby.state());
        assertTrue(lobby.toggleReady(alice));
        assertEquals(Lobby.LobbyState.WAITING, lobby.state(), "Countdown should not start until enough players are ready");

        assertTrue(lobby.toggleReady(bob));
        assertEquals(Lobby.LobbyState.COUNTDOWN, lobby.state(), "Countdown should start once minimum ready players reached");

        assertFalse(lobby.toggleReady(alice));
        assertEquals(Lobby.LobbyState.WAITING, lobby.state(), "Dropping below min ready players should cancel countdown");
    }

    @Test
    void countdownResetsWhenPlayerJoinsDuringCountdown() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 1, 4, 5L, "test", DummyMode.class);
        PlayerMock alice = server.addPlayer("alice2");
        PlayerMock bob = server.addPlayer("bob2");

        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(alice));
        assertTrue(lobby.toggleReady(alice));
        assertEquals(Lobby.LobbyState.COUNTDOWN, lobby.state());
        long initial = lobby.countdown().get();

        lobby.tick(0); // decrement countdown once
        assertTrue(lobby.countdown().get() < initial, "Countdown should have progressed");

        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(bob));
        assertEquals(lobby.countdownSeconds(), lobby.countdown().get(), "Countdown should reset when a new player joins during countdown");
    }

    public static class DummyMode extends Game {
        private static final GameInfo INFO = new GameInfo(
                "lobby-test",
                5,
                Time.parse("1min"),
                NamedTextColor.WHITE,
                CircularPillarGen.class,
                BlockPlatform.class,
                m -> true
        );

        public DummyMode(Location center, int startTick, java.util.List<Player> players) {
            super(center, startTick, players);
        }

        @Override
        public GameInfo info() {
            return INFO;
        }
    }
}
