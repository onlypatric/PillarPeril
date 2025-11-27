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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArenaRegenTest {
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
    void untouchedAirRemainsAirAfterCleanup() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        PlayerMock a = server.addPlayer("A");
        PlayerMock b = server.addPlayer("B");

        TestGame game = new TestGame(center, 0, List.of(a, b));

        // Pick a location far away from any generation and ensure it is air.
        Location airLoc = new Location(world, 2000, 80, 2000);
        airLoc.getBlock().setType(Material.AIR);
        assertEquals(Material.AIR, airLoc.getBlock().getType());

        // Do not call addBlock for this location, so it is never tracked.
        // Run cleanup and ensure it stays air.
        game.cleanup();

        assertEquals(Material.AIR, airLoc.getBlock().getType(), "Untouched air block should remain air after cleanup");
    }

    @Test
    void waterPlacedDuringGameIsRemovedOnCleanup() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);

        PlayerMock a = server.addPlayer("A");
        PlayerMock b = server.addPlayer("B");

        TestGame game = new TestGame(center, 0, List.of(a, b));

        // Choose a location far away from the arena pillars so we don't interfere with generation.
        Location liquidLoc = new Location(world, 1000, 70, 1000);
        liquidLoc.getBlock().setType(Material.STONE);

        // Simulate player/place logic: remember original block, then place water.
        game.addBlock(liquidLoc, liquidLoc.getBlock().getBlockData());
        liquidLoc.getBlock().setType(Material.WATER);

        // Sanity check before cleanup.
        assertEquals(Material.WATER, liquidLoc.getBlock().getType());

        // End game and run cleanup, which should restore the original block data.
        game.cleanup();

        assertEquals(Material.STONE, liquidLoc.getBlock().getType());
    }

    /**
     * Minimal Game subclass for arena regen testing, using explicit GameInfo to avoid config.
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

        TestGame(Location center, int startTick, List<org.bukkit.entity.Player> players) {
            super(center, startTick, players);
        }

        @Override
        public GameInfo info() {
            return INFO;
        }
    }
}
