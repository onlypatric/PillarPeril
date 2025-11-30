package com.marcpg.pillarperil;

import com.marcpg.pillarperil.generation.Generator;
import com.marcpg.pillarperil.generation.Platform;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PillarPerilReloadConfigTest {
    private ServerMock server;
    private Locale previousLocale;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        previousLocale = Locale.getDefault();
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(previousLocale);
        PillarPeril.PLUGIN = null;
        MockBukkit.unmock();
    }

    @Test
    void reloadCoreConfigAppliesNumericSettingsAndLocale() {
        DummyPlugin plugin = new DummyPlugin();
        PillarPeril.PLUGIN = plugin;
        FileConfiguration cfg = plugin.getConfig();

        cfg.set("platform-height", 150);
        cfg.set("max-fall", 30);
        cfg.set("platform-distance-factor", 12.5);
        cfg.set("locale", "it_IT");

        PillarPeril.reloadCoreConfig();

        assertEquals(150, Platform.platformHeight);
        assertEquals(120, Platform.deathHeight);
        assertEquals(12.5, Generator.platformDistanceFactor, 0.0001);
        assertEquals(Locale.of("it", "IT"), Locale.getDefault());
    }

    @Test
    void reloadCoreConfigParsesHyphenatedLocale() {
        DummyPlugin plugin = new DummyPlugin();
        PillarPeril.PLUGIN = plugin;
        FileConfiguration cfg = plugin.getConfig();

        cfg.set("platform-height", 200);
        cfg.set("max-fall", 25);
        cfg.set("platform-distance-factor", 10.0);
        cfg.set("locale", "en-US");

        PillarPeril.reloadCoreConfig();

        assertEquals(Locale.of("en", "US"), Locale.getDefault());
    }

    private static class DummyPlugin extends PillarPeril {
        private final FileConfiguration cfg = new YamlConfiguration();

        @Override
        public void reloadConfig() {
            // No-op; config is managed directly in tests.
        }

        @Override
        public FileConfiguration getConfig() {
            return cfg;
        }
    }
}
