package com.marcpg.pillarperil;

import com.marcpg.pillarperil.game.util.LeaderboardHologramManager;
import com.marcpg.pillarperil.game.util.StatsManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.ArmorStand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeaderboardHologramManagerTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        StatsManager.reset();
        LeaderboardHologramManager.clear();
    }

    @AfterEach
    void tearDown() {
        LeaderboardHologramManager.clear();
        MockBukkit.unmock();
    }

    @Test
    void hologramShowsKillsAboveWins() throws Exception {
        PlayerMock playerA = server.addPlayer("PlayerA");
        PlayerMock playerB = server.addPlayer("PlayerB");

        PillarPlayer pillarA = new PillarPlayer(playerA, null);
        PillarPlayer pillarB = new PillarPlayer(playerB, null);

        // Give PlayerA more kills, PlayerB more wins.
        StatsManager.recordKill(pillarA);
        StatsManager.recordKill(pillarA);
        StatsManager.recordKill(pillarB);
        StatsManager.recordWin(pillarB);

        LeaderboardHologramManager.createOrMove(playerA);

        Field field = LeaderboardHologramManager.class.getDeclaredField("HOLOGRAM_LINES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ArmorStand> lines = (List<ArmorStand>) field.get(null);

        assertFalse(lines.isEmpty(), "Hologram lines should be created");
        assertEquals(1 + 10 + 1 + 1 + 10, lines.size(), "Expected kills header+entries, spacer, wins header+entries");

        Component topHeader = lines.getFirst().customName();
        assertNotNull(topHeader);
        assertTrue(topHeader.toString().contains("Top Kills"), "First header should be Top Kills");

        Component winsHeader = lines.get(1 + 10 + 1).customName();
        assertNotNull(winsHeader);
        assertTrue(winsHeader.toString().contains("Top Wins"), "Second section header should be Top Wins");

        Component firstKillsLine = lines.get(1).customName();
        assertNotNull(firstKillsLine);
        assertTrue(firstKillsLine.toString().toLowerCase().contains("kills"), "Kills section entries should mention kills");

        Component firstWinsLine = lines.get(1 + 10 + 1 + 1).customName();
        assertNotNull(firstWinsLine);
        assertTrue(firstWinsLine.toString().toLowerCase().contains("wins"), "Wins section entries should mention wins");
    }
}

