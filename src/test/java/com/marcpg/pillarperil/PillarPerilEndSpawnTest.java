package com.marcpg.pillarperil;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PillarPerilEndSpawnTest {
    private ServerMock server;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        // Ensure END_SPAWN starts as null for each test.
        setEndSpawn(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        setEndSpawn(null);
        MockBukkit.unmock();
    }

    @Test
    void endSpawnFallsBackToWorldSpawnWhenUnset() {
        World world = server.addSimpleWorld("world");
        Location spawn = world.getSpawnLocation();

        Location result = PillarPeril.endSpawn(world);

        assertNotNull(result);
        assertEquals(spawn.getWorld(), result.getWorld());
        assertEquals(spawn.getBlockX(), result.getBlockX());
        assertEquals(spawn.getBlockY(), result.getBlockY());
        assertEquals(spawn.getBlockZ(), result.getBlockZ());
    }

    @Test
    void endSpawnUsesConfiguredLocationWhenSet() throws Exception {
        World world = server.addSimpleWorld("world");
        Location configured = new Location(world, 10.5, 70.0, -5.25, 45.0f, 30.0f);

        setEndSpawn(configured);

        Location result = PillarPeril.endSpawn(world);

        assertNotNull(result);
        assertEquals(configured.getWorld(), result.getWorld());
        assertEquals(configured.getX(), result.getX(), 0.0001);
        assertEquals(configured.getY(), result.getY(), 0.0001);
        assertEquals(configured.getZ(), result.getZ(), 0.0001);
        assertEquals(configured.getYaw(), result.getYaw(), 0.0001);
        assertEquals(configured.getPitch(), result.getPitch(), 0.0001);
    }

    private void setEndSpawn(Location location) throws Exception {
        Field field = PillarPeril.class.getDeclaredField("END_SPAWN");
        field.setAccessible(true);
        field.set(null, location);
    }
}

