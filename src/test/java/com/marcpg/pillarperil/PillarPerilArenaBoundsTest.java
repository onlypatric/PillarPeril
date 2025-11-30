package com.marcpg.pillarperil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class PillarPerilArenaBoundsTest {
    private ServerMock server;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        setArenaBounds(null, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        setArenaBounds(null, null);
        MockBukkit.unmock();
    }

    @Test
    void appliesBarriersOnLargeAreaBoundariesOnly() throws Exception {
        World world = server.addSimpleWorld("world");

        // 80x80 area in X/Z, small height range in Y to keep the test fast.
        Location min = new Location(world, 0, 64, 0);
        Location max = new Location(world, 79, 66, 79);
        setArenaBounds(min, max);

        // Sanity: interior starts as air.
        Location interior = new Location(world, 40, 65, 40);
        assertEquals(Material.AIR, interior.getBlock().getType());

        PillarPeril.applyArenaBarriers(world);

        // Corners and edges become barriers.
        assertEquals(Material.BARRIER, world.getBlockAt(0, 64, 0).getType());
        assertEquals(Material.BARRIER, world.getBlockAt(79, 64, 0).getType());
        assertEquals(Material.BARRIER, world.getBlockAt(0, 66, 79).getType());
        assertEquals(Material.BARRIER, world.getBlockAt(79, 66, 79).getType());

        // Some mid-edge samples.
        assertEquals(Material.BARRIER, world.getBlockAt(0, 65, 40).getType());
        assertEquals(Material.BARRIER, world.getBlockAt(40, 65, 0).getType());
        assertEquals(Material.BARRIER, world.getBlockAt(79, 65, 40).getType());
        assertEquals(Material.BARRIER, world.getBlockAt(40, 65, 79).getType());

        // Interior remains non-barrier (air).
        assertEquals(Material.AIR, interior.getBlock().getType());
    }

    @Test
    void clearArenaClearsBlocksAndEntitiesInLargeArea() throws Exception {
        World world = server.addSimpleWorld("world");

        Location min = new Location(world, 0, 64, 0);
        Location max = new Location(world, 79, 66, 79);
        setArenaBounds(min, max);

        // Blocks inside the arena.
        Location inside = new Location(world, 10, 65, 10);
        Location boundary = new Location(world, 0, 64, 0);
        inside.getBlock().setType(Material.STONE);
        boundary.getBlock().setType(Material.STONE);

        // Block outside the arena should remain untouched.
        Location outside = new Location(world, 100, 65, 100);
        outside.getBlock().setType(Material.STONE);

        // Entities inside and outside the arena.
        Entity insideEntity = world.spawn(new Location(world, 10.5, 65, 10.5), Zombie.class);
        Entity outsideEntity = world.spawn(new Location(world, 100.5, 65, 100.5), Zombie.class);

        assertTrue(world.getEntities().contains(insideEntity));
        assertTrue(world.getEntities().contains(outsideEntity));

        PillarPeril.clearArena(world);

        // Inside region (including boundary) is cleared to air.
        assertEquals(Material.AIR, inside.getBlock().getType());
        assertEquals(Material.AIR, boundary.getBlock().getType());

        // Outside region remains unchanged.
        assertEquals(Material.STONE, outside.getBlock().getType());

        // Inside entities are removed, outside entities remain.
        assertFalse(world.getEntities().contains(insideEntity));
        assertTrue(world.getEntities().contains(outsideEntity));
    }

    private void setArenaBounds(Location min, Location max) throws Exception {
        Field minField = PillarPeril.class.getDeclaredField("ARENA_MIN");
        Field maxField = PillarPeril.class.getDeclaredField("ARENA_MAX");
        minField.setAccessible(true);
        maxField.setAccessible(true);
        minField.set(null, min);
        maxField.set(null, max);
    }
}

