package com.marcpg.pillarperil;

import com.marcpg.libpg.data.time.Time;
import com.marcpg.pillarperil.game.Game;
import com.marcpg.pillarperil.game.util.GameInfo;
import com.marcpg.pillarperil.generation.generator.CircularPillarGen;
import com.marcpg.pillarperil.generation.platform.BlockPlatform;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEndLogicTest {
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
    void lastStandingWhenOnePlayerRemains() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        PlayerMock a = server.addPlayer("A");
        PlayerMock b = server.addPlayer("B");
        PlayerMock c = server.addPlayer("C");

        TestGame game = new TestGame(center, 0, List.of(a, b, c));

        // Simulate two eliminations by removing two players from the alive list
        List<PillarPlayer> alive = new ArrayList<>(game.players());
        game.players().remove(alive.get(0));
        game.players().remove(alive.get(1));

        game.invokeCheckEndCondition();

        assertEquals(Game.EndingCause.LAST_STANDING, game.lastCause);
        assertEquals(1, game.lastWinners.size());
        assertEquals(alive.get(2), game.lastWinners.getFirst());
    }

    @Test
    void drawWhenNoPlayersRemain() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        PlayerMock a = server.addPlayer("A");
        PlayerMock b = server.addPlayer("B");

        TestGame game = new TestGame(center, 0, List.of(a, b));

        // Simulate both players dying at the same time
        game.players().clear();

        game.invokeCheckEndCondition();

        assertEquals(Game.EndingCause.DRAW, game.lastCause);
        assertTrue(game.lastWinners.isEmpty());
    }

    @Test
    void timeOverCausesForceEndWithNoWinners() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        PlayerMock a = server.addPlayer("A");

        TimeLimitedGame game = new TimeLimitedGame(center, 0, List.of(a));

        // Tick seconds until time runs out; Game.tickSecond() should call end(FORCE, List.of()).
        for (int i = 0; i < 10; i++) {
            game.tickSecond();
        }

        assertEquals(Game.EndingCause.FORCE, game.lastCause);
        assertTrue(game.lastWinners.isEmpty());
    }

    /**
     * Minimal Game subclass for testing end-condition logic without invoking plugin cleanup,
     * teleports, or translations.
     */
    private static class TestGame extends Game {
        private static final GameInfo INFO = new GameInfo(
                "test",
                5,
                Time.parse("1min"),
                NamedTextColor.WHITE,
                CircularPillarGen.class,
                BlockPlatform.class,
                m -> true
        );

        Game.EndingCause lastCause;
        List<PillarPlayer> lastWinners = List.of();

        TestGame(Location center, int startTick, List<org.bukkit.entity.Player> players) {
            super(center, startTick, players);
        }

        @Override
        public GameInfo info() {
            return INFO;
        }

        @Override
        public void end(@NotNull EndingCause cause, List<PillarPlayer> winners) {
            // Override to capture cause/winners without running full cleanup / messaging logic.
            if (lastCause != null) {
                return;
            }
            lastCause = cause;
            lastWinners = new ArrayList<>(winners);
        }

        void invokeCheckEndCondition() {
            try {
                Method m = Game.class.getDeclaredMethod("checkEndCondition");
                m.setAccessible(true);
                m.invoke(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class TimeLimitedGame extends Game {
        private static final GameInfo INFO = new GameInfo(
                "time-test",
                5,
                Time.parse("5sec"),
                NamedTextColor.WHITE,
                CircularPillarGen.class,
                BlockPlatform.class,
                m -> true
        );

        Game.EndingCause lastCause;
        List<PillarPlayer> lastWinners = List.of();

        TimeLimitedGame(Location center, int startTick, List<org.bukkit.entity.Player> players) {
            super(center, startTick, players);
        }

        @Override
        public GameInfo info() {
            return INFO;
        }

        @Override
        public void end(@NotNull EndingCause cause, List<PillarPlayer> winners) {
            if (lastCause != null) {
                return;
            }
            lastCause = cause;
            lastWinners = new ArrayList<>(winners);
        }
    }
}
