package com.marcpg.pillarperil;

import com.marcpg.pillarperil.game.util.StatsManager;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StatsManagerPersistenceTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        StatsManager.reset();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void statsArePersistedAndReloaded() throws Exception {
        PlayerMock a = server.addPlayer("PersistA");
        PlayerMock b = server.addPlayer("PersistB");

        // Wrap in PillarPlayer; game is not used by StatsManager.
        PillarPlayer pa = new PillarPlayer(a, null);
        PillarPlayer pb = new PillarPlayer(b, null);

        // Record some stats.
        StatsManager.recordKill(pa);
        StatsManager.recordKill(pa);
        StatsManager.recordKill(pb);
        StatsManager.recordWin(pb);

        Path tempDir = Files.createTempDirectory("pillarperil-stats-test");

        // Save to disk.
        StatsManager.save(tempDir.toFile());

        // Reset and reload.
        StatsManager.reset();
        StatsManager.load(tempDir.toFile());

        var topKills = StatsManager.topKills(10);
        var topWins = StatsManager.topWins(10);

        assertFalse(topKills.isEmpty(), "Top kills should not be empty after reload");
        assertFalse(topWins.isEmpty(), "Top wins should not be empty after reload");

        assertEquals("PersistA", topKills.getFirst().name());
        assertEquals(2, topKills.getFirst().kills());

        assertEquals("PersistB", topWins.getFirst().name());
        assertEquals(1, topWins.getFirst().wins());
    }
}

