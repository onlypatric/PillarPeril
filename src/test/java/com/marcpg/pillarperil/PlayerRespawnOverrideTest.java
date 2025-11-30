package com.marcpg.pillarperil;

import com.marcpg.libpg.data.time.Time;
import com.marcpg.pillarperil.event.PlayerEvents;
import com.marcpg.pillarperil.game.Game;
import com.marcpg.pillarperil.game.util.GameInfo;
import com.marcpg.pillarperil.game.util.GameManager;
import com.marcpg.pillarperil.generation.generator.CircularPillarGen;
import com.marcpg.pillarperil.generation.platform.BlockPlatform;
import com.marcpg.pillarperil.generation.Platform;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerRespawnOverrideTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Platform.platformHeight = 20;
        Platform.deathHeight = 0;
        GameManager.GAMES.clear();
        TestTranslations.ensure();
    }

    @AfterEach
    void tearDown() {
        GameManager.GAMES.clear();
        MockBukkit.unmock();
    }

    @Test
    void respawnInGameIgnoresBedSpawnAndUsesGameCenter() {
        World world = server.addSimpleWorld("world");
        Location center = new Location(world, 0, 80, 0);
        PlayerMock player = server.addPlayer("PlayerA");

        // Player has a bed spawn elsewhere.
        Location bedLocation = new Location(world, 100, 70, 100);

        TestGame game = new TestGame(center, 0, List.of(player));
        Location expectedCenter = game.center;

        PlayerRespawnEvent event = new PlayerRespawnEvent(player, bedLocation, true);
        PlayerEvents listener = new PlayerEvents();
        listener.onPlayerRespawn(event);

        Location respawn = event.getRespawnLocation();
        assertEquals(expectedCenter.getWorld(), respawn.getWorld());
        assertEquals(expectedCenter.getBlockX(), respawn.getBlockX());
        assertEquals(expectedCenter.getBlockY(), respawn.getBlockY());
        assertEquals(expectedCenter.getBlockZ(), respawn.getBlockZ());
    }

    private static class TestGame extends Game {
        private static final GameInfo INFO = new GameInfo(
                "respawn-test",
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

