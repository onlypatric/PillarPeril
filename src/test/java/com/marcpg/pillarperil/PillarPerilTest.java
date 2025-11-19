package com.marcpg.pillarperil;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("MockBukkit currently cannot initialize Paper lifecycle events used by PillarPeril")
class PillarPerilTest {
    private ServerMock server;
    private PillarPeril plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PillarPeril.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginLoadsAndConfigIsAvailable() {
        assertNotNull(plugin);
        assertNotNull(PillarPeril.CONFIG);
        assertNotNull(PillarPeril.LOG);
    }

    @Test
    void endSpawnLocationIsResolved() {
        // Disabled with the class; left as placeholder for future environment support.
        assertNotNull(plugin);
    }
}
