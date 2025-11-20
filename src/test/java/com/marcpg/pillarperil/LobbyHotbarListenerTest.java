package com.marcpg.pillarperil;

import com.marcpg.libpg.data.time.Time;
import com.marcpg.pillarperil.event.LobbyHotbarListener;
import com.marcpg.pillarperil.game.Game;
import com.marcpg.pillarperil.game.Lobby;
import com.marcpg.pillarperil.game.util.GameInfo;
import com.marcpg.pillarperil.generation.generator.CircularPillarGen;
import com.marcpg.pillarperil.generation.platform.BlockPlatform;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LobbyHotbarListenerTest {
    private ServerMock server;
    private LobbyHotbarListener listener;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        com.marcpg.pillarperil.generation.Platform.platformHeight = 20;
        com.marcpg.pillarperil.generation.Platform.deathHeight = 0;
        TestTranslations.ensure();
        listener = new LobbyHotbarListener();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void startHotbarItemLaunchesGameWhenPlayerHasPermission() {
        World world = server.addSimpleWorld("world_start");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 1, 4, 5L, "test", DummyMode.class);
        PlayerMock player = server.addPlayer("HotbarStart");
        player.setOp(true);
        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(player));

        ItemStack startItem = player.getInventory().getItem(4);
        assertNotNull(startItem, "Start item not applied to player");

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, startItem, null, BlockFace.SELF);
        listener.onHotbarInteract(event);

        assertTrue(event.isCancelled(), "Hotbar interaction should be cancelled");
        assertEquals(Lobby.LobbyState.IN_GAME, lobby.state(), "Lobby should be IN_GAME after start item use");
        assertNotNull(lobby.currentGame(), "Game should have been created from start item");

        lobby.currentGame().end(Game.EndingCause.FORCE, List.of());
        assertEquals(Lobby.LobbyState.WAITING, lobby.state(), "Lobby should return to WAITING after cleanup");
    }

    @Test
    void queueHotbarItemMarksPlayersReadyAndStartsCountdown() {
        World world = server.addSimpleWorld("world_queue");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 2, 4, 5L, "test", DummyMode.class);
        PlayerMock alice = server.addPlayer("QueueAlice");
        PlayerMock bob = server.addPlayer("QueueBob");
        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(alice));
        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(bob));

        ItemStack aliceQueue = alice.getInventory().getItem(7);
        ItemStack bobQueue = bob.getInventory().getItem(7);
        assertNotNull(aliceQueue);
        assertNotNull(bobQueue);

        listener.onHotbarInteract(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, aliceQueue, null, BlockFace.SELF));
        assertEquals(Lobby.LobbyState.WAITING, lobby.state(), "Countdown should not start until minimum ready players");

        listener.onHotbarInteract(new PlayerInteractEvent(bob, Action.RIGHT_CLICK_AIR, bobQueue, null, BlockFace.SELF));
        assertEquals(Lobby.LobbyState.COUNTDOWN, lobby.state(), "Countdown should start when enough players ready");
        assertEquals(lobby.countdownSeconds(), lobby.countdown().get(), "Countdown timer should reset to configured duration");
    }

    @Test
    void startHotbarItemWithoutPermissionDoesNotLaunchGame() {
        World world = server.addSimpleWorld("world_no_perm");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 1, 4, 5L, "test", DummyMode.class);
        PlayerMock player = server.addPlayer("NoPermStart");
        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(player));

        ItemStack startItem = player.getInventory().getItem(4);
        assertNotNull(startItem, "Start item missing from inventory");

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, startItem, null, BlockFace.SELF);
        listener.onHotbarInteract(event);

        assertTrue(event.isCancelled(), "Listener should still cancel interaction");
        assertEquals(Lobby.LobbyState.WAITING, lobby.state(), "Lobby should remain waiting without permission");
        assertNull(lobby.currentGame(), "Game should not start when permission missing");
        assertNotNull(player.getScoreboard().getObjective("pp_lobby"), "Lobby scoreboard should remain active");
    }

    @Test
    void queueHotbarUnreadyCancelsCountdownAndUpdatesScoreboard() {
        World world = server.addSimpleWorld("world_queue_cancel");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 2, 4, 5L, "test", DummyMode.class);
        PlayerMock alice = server.addPlayer("QueueCancelAlice");
        PlayerMock bob = server.addPlayer("QueueCancelBob");
        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(alice));
        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(bob));

        ItemStack aliceQueue = alice.getInventory().getItem(7);
        ItemStack bobQueue = bob.getInventory().getItem(7);
        assertNotNull(aliceQueue);
        assertNotNull(bobQueue);

        listener.onHotbarInteract(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, aliceQueue, null, BlockFace.SELF));
        listener.onHotbarInteract(new PlayerInteractEvent(bob, Action.RIGHT_CLICK_AIR, bobQueue, null, BlockFace.SELF));
        assertEquals(Lobby.LobbyState.COUNTDOWN, lobby.state(), "Countdown should start when ready threshold hit");

        listener.onHotbarInteract(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, aliceQueue, null, BlockFace.SELF));
        assertEquals(Lobby.LobbyState.WAITING, lobby.state(), "Countdown should cancel when player unreadies");
        assertEquals(lobby.countdownSeconds(), lobby.countdown().get(), "Countdown timer should reset after cancellation");
        assertNotNull(alice.getScoreboard().getObjective("pp_lobby"), "Lobby scoreboard should persist after unready");
    }

    @Test
    void hotbarReappliedAfterGameEnds() {
        World world = server.addSimpleWorld("world_post_game");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 1, 4, 5L, "test", DummyMode.class);
        PlayerMock player = server.addPlayer("PostGameHotbar");
        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(player));

        lobby.forceStart();
        assertEquals(Lobby.LobbyState.IN_GAME, lobby.state());
        assertNull(Lobby.hotbarAction(player.getInventory().getItem(4)), "Hotbar should be cleared during the game");

        Game game = lobby.currentGame();
        assertNotNull(game);
        game.end(Game.EndingCause.FORCE, List.of());

        assertEquals(Lobby.LobbyState.WAITING, lobby.state());
        assertEquals(Lobby.HotbarAction.START, Lobby.hotbarAction(player.getInventory().getItem(4)), "Start item should return after cleanup");
        assertEquals(Lobby.HotbarAction.QUEUE, Lobby.hotbarAction(player.getInventory().getItem(7)), "Queue item should return after cleanup");
        assertEquals(Lobby.HotbarAction.LEAVE, Lobby.hotbarAction(player.getInventory().getItem(8)), "Leave item should return after cleanup");
        assertNotNull(player.getScoreboard().getObjective("pp_lobby"), "Lobby scoreboard should be reapplied after cleanup");
    }

    @Test
    void leaveHotbarItemRemovesPlayerFromLobby() {
        World world = server.addSimpleWorld("world_leave");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 1, 4, 5L, "test", DummyMode.class);
        PlayerMock player = server.addPlayer("HotbarLeave");
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));
        ItemStack[] beforeJoin = player.getInventory().getContents().clone();
        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(player));

        ItemStack leaveItem = player.getInventory().getItem(8);
        assertNotNull(leaveItem, "Leave item missing");

        listener.onHotbarInteract(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, leaveItem, null, BlockFace.SELF));

        assertFalse(lobby.players().contains(player), "Player should no longer be in lobby after leave item");
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
        assertEquals(world.getSpawnLocation(), player.getLocation(), "Player should be teleported to world spawn/end spawn");
        assertArrayEquals(beforeJoin, player.getInventory().getContents(), "Inventory should be restored to pre-join state");
        assertEquals(Bukkit.getScoreboardManager().getMainScoreboard(), player.getScoreboard(), "Player scoreboard should reset after leaving");
    }

    @Test
    void hotbarRemovedAfterLeaveAndRestoredOnRejoin() {
        World world = server.addSimpleWorld("world_leave_rejoin");
        Location center = new Location(world, 0, 80, 0);

        Lobby lobby = new Lobby(center, 1, 4, 5L, "test", DummyMode.class);
        PlayerMock player = server.addPlayer("LeaveRejoin");
        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(player));

        ItemStack startItem = player.getInventory().getItem(4);
        ItemStack leaveItem = player.getInventory().getItem(8);
        assertEquals(Lobby.HotbarAction.START, Lobby.hotbarAction(startItem));
        assertEquals(Lobby.HotbarAction.LEAVE, Lobby.hotbarAction(leaveItem));

        listener.onHotbarInteract(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, leaveItem, null, BlockFace.SELF));
        assertNull(Lobby.hotbarAction(player.getInventory().getItem(4)), "Start item should be removed after leaving");
        assertEquals(Bukkit.getScoreboardManager().getMainScoreboard(), player.getScoreboard(), "Scoreboard should reset after leaving");

        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(player));
        assertEquals(Lobby.HotbarAction.START, Lobby.hotbarAction(player.getInventory().getItem(4)), "Start item should return when rejoining");
        assertEquals(Lobby.HotbarAction.QUEUE, Lobby.hotbarAction(player.getInventory().getItem(7)), "Queue item should return when rejoining");
        assertEquals(Lobby.HotbarAction.LEAVE, Lobby.hotbarAction(player.getInventory().getItem(8)), "Leave item should return when rejoining");
    }

    private static class DummyMode extends Game {
        private static final GameInfo INFO = new GameInfo(
                "hotbar-test",
                5,
                Time.parse("30sec"),
                NamedTextColor.WHITE,
                CircularPillarGen.class,
                BlockPlatform.class,
                m -> true
        );

        DummyMode(Location center, int startTick, List<org.bukkit.entity.Player> players) {
            super(center, startTick, players);
        }

        @Override
        public GameInfo info() {
            return INFO;
        }

        @Override
        public void end(@NotNull EndingCause cause, List<PillarPlayer> winners) {
            cleanup();
        }
    }
}
