package com.marcpg.pillarperil;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import com.marcpg.pillarperil.game.util.StatsManager;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StatsManagerTest {
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
    void recordsKillsAndWinsAndOrdersLeaderboards() {
        PlayerMock playerA = server.addPlayer("PlayerA");
        PlayerMock playerB = server.addPlayer("PlayerB");

        PillarPlayer pillarA = new PillarPlayer(playerA, null);
        PillarPlayer pillarB = new PillarPlayer(playerB, null);

        StatsManager.recordKill(pillarA);
        StatsManager.recordKill(pillarA);
        StatsManager.recordKill(pillarB);
        StatsManager.recordWin(pillarB);

        var topKills = StatsManager.topKills(10);
        var topWins = StatsManager.topWins(10);

        assertFalse(topKills.isEmpty());
        assertFalse(topWins.isEmpty());

        assertEquals("PlayerA", topKills.getFirst().name());
        assertEquals("PlayerB", topWins.getFirst().name());
    }
}
