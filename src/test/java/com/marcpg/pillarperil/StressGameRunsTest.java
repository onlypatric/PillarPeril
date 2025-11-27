package com.marcpg.pillarperil;

import com.marcpg.libpg.data.time.Time;
import com.marcpg.pillarperil.game.Game;
import com.marcpg.pillarperil.game.Lobby;
import com.marcpg.pillarperil.game.util.GameInfo;
import com.marcpg.pillarperil.game.util.GameManager;
import com.marcpg.pillarperil.game.util.LobbyManager;
import com.marcpg.pillarperil.generation.Generator;
import com.marcpg.pillarperil.generation.Platform;
import com.marcpg.pillarperil.generation.generator.CircularPillarGen;
import com.marcpg.pillarperil.generation.platform.BlockPlatform;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class StressGameRunsTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        com.marcpg.pillarperil.generation.Platform.platformHeight = 20;
        com.marcpg.pillarperil.generation.Platform.deathHeight = 0;
        com.marcpg.pillarperil.generation.Generator.platformDistanceFactor = 5.0;
        TestTranslations.ensure();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void runManyGamesWithBlockChangesAndCleanup() {
        World world = server.addSimpleWorld("world");

        int baseGames = GameManager.GAMES.size();

        for (int i = 0; i < 100; i++) {
            Location center = new Location(world, i * 20.0, 80.0, i * 20.0);

            PlayerMock a = server.addPlayer("StressA" + i);
            PlayerMock b = server.addPlayer("StressB" + i);

            StressGame game = new StressGame(center, 0, List.of(a, b));

            // Game should be registered
            assertTrue(GameManager.GAMES.contains(game), "Game not registered for iteration " + i);

            // Players should have been initialized correctly
            assertEquals(GameMode.SURVIVAL, a.getGameMode(), "Player A not in SURVIVAL at start");
            assertEquals(GameMode.SURVIVAL, b.getGameMode(), "Player B not in SURVIVAL at start");

            // For each game, modify multiple blocks in different ways.
            List<Location> modified = new ArrayList<>();
            for (int j = 0; j < 5; j++) {
                // Unique location per (game, j) well away from the arena.
                double safeX = Math.min(1000 + i, world.getWorldBorder().getSize() / 2 - 10);
                double safeZ = Math.min(1000 + i + j, world.getWorldBorder().getSize() / 2 - 10);
                Location loc = new Location(world, safeX, Math.min(70 + j, world.getMaxHeight() - 1), safeZ);
                loc.getBlock().setType(Material.STONE);
                modified.add(loc);

                // Record original state and then modify the block in different patterns.
                game.addBlock(loc, loc.getBlock().getBlockData());
                int pattern = (i + j) % 4;
                if (pattern == 0) {
                    loc.getBlock().setType(Material.WATER);
                } else if (pattern == 1) {
                    loc.getBlock().setType(Material.LAVA);
                } else if (pattern == 2) {
                    loc.getBlock().setType(Material.COBBLESTONE);
                } else {
                    loc.getBlock().setType(Material.AIR);
                }
            }

            // Run a few ticks to exercise tick logic on many games.
            for (int tick = 0; tick < 40; tick++) {
                game.tick(tick);
            }

            // End game and cleanup arena.
            game.cleanup();

            // After cleanup, game should be removed from manager
            assertTrue(!GameManager.GAMES.contains(game), "Game leaked in manager for iteration " + i);

            // After cleanup, players should be back on main scoreboard with empty inventory
            var scoreboardManager = Bukkit.getScoreboardManager();
            assertNotNull(scoreboardManager);
            assertEquals(scoreboardManager.getMainScoreboard(), a.getScoreboard(), "Player A scoreboard not reset");
            assertEquals(scoreboardManager.getMainScoreboard(), b.getScoreboard(), "Player B scoreboard not reset");
            assertTrue(a.getInventory().isEmpty(), "Player A inventory not cleared");
            assertTrue(b.getInventory().isEmpty(), "Player B inventory not cleared");
            assertEquals(GameMode.SURVIVAL, a.getGameMode(), "Player A not in SURVIVAL after cleanup");
            assertEquals(GameMode.SURVIVAL, b.getGameMode(), "Player B not in SURVIVAL after cleanup");

            // After cleanup, all modified blocks should be restored to their original state.
            for (Location loc : modified) {
                assertEquals(Material.STONE, loc.getBlock().getType(), "Block was not restored for game " + i + " at " + loc);
            }
        }

        // Ensure we did not leak games into the manager from this stress run.
        assertEquals(baseGames, GameManager.GAMES.size());
    }

    @Test
    void fullGameWithManyPlayersAndBlockChangesCleansUp() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        int baseGames = GameManager.GAMES.size();

        int maxHeight = world.getMaxHeight();
        Platform.platformHeight = Math.min(200, maxHeight - 5);
        Platform.deathHeight = Math.max(0, Platform.platformHeight - 25);
        Generator.platformDistanceFactor = 10.0;

        // Create 50 players for a large game, giving each some initial inventory and experience.
        List<PlayerMock> players = new ArrayList<>();
        int[] expectedLevels = new int[50];
        float[] expectedExp = new float[50];
        int[] expectedDiamonds = new int[50];
        for (int i = 0; i < 50; i++) {
            PlayerMock player = server.addPlayer("Player" + i);
            players.add(player);

            player.getInventory().clear();
            int diamonds = i % 5 + 1;
            expectedDiamonds[i] = diamonds;
            player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.DIAMOND, diamonds));

            int level = i % 10;
            float exp = 0.25f;
            player.setLevel(level);
            player.setExp(exp);
            expectedLevels[i] = level;
            expectedExp[i] = exp;
        }

        SimGame game = new SimGame(center, 0, new ArrayList<>(players));

        // Game should be registered and have correct world/center
        assertTrue(GameManager.GAMES.contains(game), "SimGame not registered");
        assertEquals(world, game.world, "Game world mismatch");

        // Verify that arena/platform generation placed bedrock beneath each player spawn,
        // similar to a real admin-started game.
        for (PlayerMock player : players) {
            Location base = player.getLocation().clone();
            Material found = Material.AIR;
            for (int y = (int) base.getY(); y >= 0; y--) {
                Location check = new Location(world, base.getX(), y, base.getZ());
                Material type = check.getBlock().getType();
                if (!type.isAir()) {
                    found = type;
                    break;
                }
            }
            assertEquals(Material.BEDROCK, found, "Expected bedrock pillar under player " + player.getName());

            // Players should be in SURVIVAL with full health at start
            assertEquals(GameMode.SURVIVAL, player.getGameMode(), "Player not in SURVIVAL at start");
            assertEquals(20.0, player.getHealth(), 0.01, "Player health not reset at start");
        }

        // Choose a bunch of locations around the arena to modify during the game
        List<Location> modifiedLocations = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            Location loc = new Location(world, Math.min(1000 + i, world.getWorldBorder().getSize() / 2 - 10), 70, Math.max(500 - i, -world.getWorldBorder().getSize() / 2 + 10));
            loc.getBlock().setType(Material.STONE);
            modifiedLocations.add(loc);

            // Record original state and then modify the block (simulate place / liquids)
            game.addBlock(loc, loc.getBlock().getBlockData());
            if (i % 3 == 0) {
                loc.getBlock().setType(Material.WATER);
            } else if (i % 3 == 1) {
                loc.getBlock().setType(Material.LAVA);
            } else {
                loc.getBlock().setType(Material.COBBLESTONE);
            }
        }

        // Run a number of ticks to exercise ticking logic and item distribution
        for (int tick = 0; tick < 200; tick++) {
            game.tick(tick);
        }

        // After some ticks, players should have received at least one item and have a game scoreboard
        var scoreboardManager = Bukkit.getScoreboardManager();
        assertNotNull(scoreboardManager);
        players.forEach(player -> {
            assertFalse(player.getInventory().isEmpty(), "Player " + player.getName() + " inventory is still empty after ticks");
            var objective = player.getScoreboard().getObjective("pp");
            assertNotNull(objective, "Player " + player.getName() + " missing game scoreboard objective 'pp'");
        });

        // End the game and run cleanup (using the overridden end to avoid translation dependencies)
        game.end(Game.EndingCause.FORCE, List.of());

        // After cleanup, players should be on main scoreboard, at world spawn, in SURVIVAL,
        // and have their original inventory and experience back.
        for (int i = 0; i < players.size(); i++) {
            PlayerMock player = players.get(i);
            assertEquals(scoreboardManager.getMainScoreboard(), player.getScoreboard(), "Player " + player.getName() + " scoreboard not reset");
            assertEquals(world.getSpawnLocation(), player.getLocation(), "Player " + player.getName() + " not teleported to spawn");
            assertEquals(GameMode.SURVIVAL, player.getGameMode(), "Player " + player.getName() + " not in SURVIVAL after cleanup");

            // Inventory: expect diamonds restored to the same amount as before the game.
            int diamondCount = 0;
            for (var item : player.getInventory().getContents()) {
                if (item != null && item.getType() == Material.DIAMOND) {
                    diamondCount += item.getAmount();
                }
            }
            assertEquals(expectedDiamonds[i], diamondCount, "Player " + player.getName() + " diamond count not restored");

            // Experience: level and exp fraction should match original.
            assertEquals(expectedLevels[i], player.getLevel(), "Player " + player.getName() + " level not restored");
            assertEquals(expectedExp[i], player.getExp(), 0.0001, "Player " + player.getName() + " exp not restored");
        }

        // All modified locations should be restored to their original state
        for (Location loc : modifiedLocations) {
            assertEquals(Material.STONE, loc.getBlock().getType(), "Block was not restored at " + loc);
        }

        // Ensure we did not leak the game into the manager
        assertEquals(baseGames, GameManager.GAMES.size());
    }

    private LobbySimGame waitForLobbyGameStart(Lobby lobby) {
        LobbySimGame started = null;
        for (int tick = 0; tick < 400; tick++) {
            lobby.tick(tick);
            if (lobby.currentGame() instanceof LobbySimGame sim) {
                started = sim;
                break;
            }
        }
        return started;
    }

    private int countMaterial(PlayerMock player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    @Test
    void persistentLobbiesHandleMultipleLargeGames() {
        World world = server.addSimpleWorld("lobby_world");
        int baseLobbies = LobbyManager.LOBBIES.size();
        var scoreboardManager = Bukkit.getScoreboardManager();
        assertNotNull(scoreboardManager);

        List<Lobby> lobbies = new ArrayList<>();
        List<List<PlayerMock>> lobbyPlayers = new ArrayList<>();
        Map<PlayerMock, Integer> expectedDiamonds = new HashMap<>();
        Map<PlayerMock, Integer> expectedLevels = new HashMap<>();

        for (int lobbyIndex = 0; lobbyIndex < 3; lobbyIndex++) {
            Location center = new Location(world, lobbyIndex * 120.0, 70.0, lobbyIndex * 40.0);
            Lobby lobby = new Lobby(center, 10, 20, 2L, "lobby-stress", LobbySimGame.class);
            lobby.setLobbySpawn(center.clone().add(0, 2, 0));
            lobbies.add(lobby);

            List<PlayerMock> players = new ArrayList<>();
            for (int p = 0; p < 20; p++) {
                PlayerMock player = server.addPlayer("L" + lobbyIndex + "P" + p);
                players.add(player);
                int diamonds = (p % 4) + 1;
                expectedDiamonds.put(player, diamonds);
                expectedLevels.put(player, p % 7);
                player.getInventory().clear();
                player.getInventory().addItem(new ItemStack(Material.DIAMOND, diamonds));
                player.setLevel(expectedLevels.get(player));
                player.setExp(0.5f);

                assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(player));
                assertNotNull(player.getScoreboard().getObjective("pp_lobby"));
            }
            lobbyPlayers.add(players);
        }

        assertTrue(LobbyManager.LOBBIES.containsAll(lobbies));

        for (int round = 0; round < 3; round++) {
            for (int lobbyIndex = 0; lobbyIndex < lobbies.size(); lobbyIndex++) {
                Lobby lobby = lobbies.get(lobbyIndex);
                List<PlayerMock> players = lobbyPlayers.get(lobbyIndex);

                for (PlayerMock player : players) {
                    if (!lobby.players().contains(player)) {
                        assertEquals(Lobby.JoinResult.SUCCESS, lobby.join(player));
                        assertNotNull(player.getScoreboard().getObjective("pp_lobby"));
                    }
                }

                LobbySimGame game = waitForLobbyGameStart(lobby);
                assertNotNull(game, "Lobby did not start a game for lobby index " + lobbyIndex + " round " + round);

                game.simulateRoundDamage(round);
                for (int tick = 0; tick < 100; tick++) {
                    game.tick(tick);
                }

                assertEquals(Lobby.LobbyState.IN_GAME, lobby.state());
                game.end(Game.EndingCause.FORCE, List.of());
                assertEquals(Lobby.LobbyState.WAITING, lobby.state());
                assertNull(lobby.currentGame());

                assertTrue(lobby.players().isEmpty(), "Lobby should be empty between games");
                Location hub = PillarPeril.endSpawn(world);
                for (PlayerMock player : players) {
                    Location loc = player.getLocation();
                    assertEquals(hub.getBlockX(), loc.getBlockX(), "Player not returned to end spawn X after round");
                    assertEquals(hub.getBlockZ(), loc.getBlockZ(), "Player not returned to end spawn Z after round");
                    assertEquals(hub.getBlockY(), loc.getBlockY(), "Player not returned to end spawn Y after round");
                    assertEquals(expectedDiamonds.get(player), countMaterial(player, Material.DIAMOND), "Diamonds not restored for " + player.getName());
                    assertEquals(expectedLevels.get(player), player.getLevel(), "Level not restored for " + player.getName());
                    assertEquals(scoreboardManager.getMainScoreboard(), player.getScoreboard(), "Player scoreboard should reset after round");
                }
            }
        }

        assertEquals(0, GameManager.GAMES.size(), "Games leaked after lobby stress test");
        LobbyManager.LOBBIES.removeAll(lobbies);
        assertEquals(baseLobbies, LobbyManager.LOBBIES.size());
    }

    /**
     * Minimal Game subclass for stress-testing cleanup over many runs.
     */
    private static class StressGame extends Game {
        private static final GameInfo INFO = new GameInfo(
                "stress",
                5,
                Time.parse("1min"),
                NamedTextColor.WHITE,
                CircularPillarGen.class,
                BlockPlatform.class,
                m -> true
        );

        StressGame(Location center, int startTick, List<org.bukkit.entity.Player> players) {
            super(center, startTick, players);
        }

        @Override
        public GameInfo info() {
            return INFO;
        }
    }

    /**
     * Game subclass that uses real generation/players but overrides end to avoid
     * title/translation logic while still performing cleanup.
     */
    private static class SimGame extends Game {
        private static final GameInfo INFO = new GameInfo(
                "sim",
                5,
                Time.parse("10min"),
                NamedTextColor.WHITE,
                CircularPillarGen.class,
                BlockPlatform.class,
                m -> true
        );

        SimGame(Location center, int startTick, List<org.bukkit.entity.Player> players) {
            super(center, startTick, players);
        }

        @Override
        public GameInfo info() {
            return INFO;
        }

        @Override
        public void end(@NotNull EndingCause cause, List<PillarPlayer> winners) {
            // Bypass titles / translations / stats; just cleanup to test arena regen + lifecycle.
            cleanup();
        }
    }

    private static class LobbySimGame extends Game {
        private static final GameInfo INFO = new GameInfo(
                "lobby-sim",
                5,
                Time.parse("5min"),
                NamedTextColor.LIGHT_PURPLE,
                CircularPillarGen.class,
                BlockPlatform.class,
                m -> true
        );

        LobbySimGame(Location center, int startTick, List<org.bukkit.entity.Player> players) {
            super(center, startTick, players);
        }

        @Override
        public GameInfo info() {
            return INFO;
        }

        void simulateRoundDamage(int round) {
            int index = 0;
            for (PillarPlayer pillar : players) {
                Location loc = pillar.player.getLocation().clone().add(round, 2 + (index % 3), -round);
                loc.getBlock().setType(Material.STONE);
                addBlock(loc, loc.getBlock().getBlockData());
                Material newType = switch ((round + index) % 4) {
                    case 0 -> Material.WATER;
                    case 1 -> Material.LAVA;
                    case 2 -> Material.COBBLESTONE;
                    default -> Material.AIR;
                };
                loc.getBlock().setType(newType);
                index++;
            }
        }
    }
}
