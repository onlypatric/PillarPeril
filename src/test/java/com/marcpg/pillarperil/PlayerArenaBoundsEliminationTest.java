package com.marcpg.pillarperil;

import com.marcpg.libpg.data.time.Time;
import com.marcpg.pillarperil.PillarPlayer;
import com.marcpg.pillarperil.game.Game;
import com.marcpg.pillarperil.game.util.GameInfo;
import com.marcpg.pillarperil.generation.generator.CircularPillarGen;
import com.marcpg.pillarperil.generation.platform.BlockPlatform;
import com.marcpg.pillarperil.event.PlayerEvents;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerArenaBoundsEliminationTest {
    private ServerMock server;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        com.marcpg.pillarperil.generation.Platform.platformHeight = 20;
        com.marcpg.pillarperil.generation.Platform.deathHeight = 0;
        TestTranslations.ensure();
        setArenaBounds(null, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        setArenaBounds(null, null);
        MockBukkit.unmock();
    }

    @Test
    void movingOutsideArenaBoundsEliminatesPlayer() throws Exception {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 10, 0);

        // Set arena bounds around the origin, with a safe Y range for MockBukkit.
        Location min = new Location(world, -10, 0, -10);
        Location max = new Location(world, 10, 50, 10);
        setArenaBounds(min, max);

        PlayerMock a = server.addPlayer("A");
        PlayerMock b = server.addPlayer("B");

        TestGame game = new TestGame(center, 0, List.of(a, b));

        // Sanity: both players are alive in the game initially.
        assertEquals(2, game.players().size());

        PlayerEvents listener = new PlayerEvents();

        // Move player A inside bounds: should remain in the game.
        Location inside = new Location(world, 0, 10, 0);
        listener.onPlayerMove(new PlayerMoveEvent(a, center, inside));
        assertEquals(2, game.players().size(), "Moving inside bounds should not eliminate the player");

        // Move player A outside bounds: should be eliminated from the game.
        Location outside = new Location(world, 20, 10, 0);
        listener.onPlayerMove(new PlayerMoveEvent(a, inside, outside));
        assertEquals(1, game.players().size(), "Moving outside bounds should eliminate the player");
    }

    private void setArenaBounds(Location min, Location max) throws Exception {
        Field minField = PillarPeril.class.getDeclaredField("ARENA_MIN");
        Field maxField = PillarPeril.class.getDeclaredField("ARENA_MAX");
        minField.setAccessible(true);
        maxField.setAccessible(true);
        minField.set(null, min);
        maxField.set(null, max);
    }

    /**
     * Minimal Game subclass used for testing arena-bound elimination.
     */
    private static class TestGame extends Game {
        private static final GameInfo INFO = new GameInfo(
                "arena-elim-test",
                5,
                Time.parse("1min"),
                NamedTextColor.WHITE,
                CircularPillarGen.class,
                BlockPlatform.class,
                m -> true
        );

        TestGame(Location center, int startTick, List<Player> players) {
            super(center, startTick, players);
        }

        @Override
        public GameInfo info() {
            return INFO;
        }

        @Override
        public void eliminate(PillarPlayer player) {
            // For this test, avoid scheduler and full end logic; just remove the player.
            players().remove(player);
        }
    }
}
