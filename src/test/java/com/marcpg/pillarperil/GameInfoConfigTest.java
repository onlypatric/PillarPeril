package com.marcpg.pillarperil;

import com.marcpg.libpg.data.time.Time;
import com.marcpg.pillarperil.game.util.GameInfo;
import com.marcpg.pillarperil.generation.generator.CircularPillarGen;
import com.marcpg.pillarperil.generation.generator.RandomPillarGen;
import com.marcpg.pillarperil.generation.platform.BlockPlatform;
import com.marcpg.pillarperil.generation.platform.PillarPlatform;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameInfoConfigTest {
    private org.bukkit.configuration.file.FileConfiguration previousConfig;

    @BeforeEach
    void setUp() {
        previousConfig = PillarPeril.CONFIG;

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("modes.cfgtest.cooldown", 7);
        cfg.set("modes.cfgtest.time-limit", "30sec");
        cfg.set("modes.cfgtest.color", "#123456");
        cfg.set("modes.cfgtest.generator", "random");
        cfg.set("modes.cfgtest.platforms", "blocks");

        cfg.set("modes.defaults.cooldown", 5);
        cfg.set("modes.defaults.time-limit", "5min");
        cfg.set("modes.defaults.color", "#FFFFFF");
        cfg.set("modes.defaults.generator", "circular");
        cfg.set("modes.defaults.platforms", "pillars");

        PillarPeril.CONFIG = cfg;
    }

    @AfterEach
    void tearDown() {
        PillarPeril.CONFIG = previousConfig;
    }

    @Test
    void gameInfoReadsConfiguredValues() {
        GameInfo info = new GameInfo("cfgtest", m -> true);

        assertEquals(7, info.itemCooldown());
        assertEquals(Time.parse("30sec"), info.timeLimit());
        assertEquals(TextColor.fromHexString("#123456"), info.accentColor());
        assertEquals(RandomPillarGen.class, info.generator());
        assertEquals(BlockPlatform.class, info.platforms());
    }

    @Test
    void gameInfoFallsBackToDefaults() {
        GameInfo info = new GameInfo("defaults", m -> true);

        assertEquals(5, info.itemCooldown());
        assertEquals(Time.parse("5min"), info.timeLimit());
        assertEquals(TextColor.fromHexString("#FFFFFF"), info.accentColor());
        assertEquals(CircularPillarGen.class, info.generator());
        assertEquals(PillarPlatform.class, info.platforms());
    }
}

