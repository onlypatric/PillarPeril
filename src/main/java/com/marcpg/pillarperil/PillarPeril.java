package com.marcpg.pillarperil;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.marcpg.libpg.lang.Translation;
import com.marcpg.pillarperil.event.GameEvents;
import com.marcpg.pillarperil.event.LobbyEvents;
import com.marcpg.pillarperil.event.LobbyHotbarListener;
import com.marcpg.pillarperil.event.PlayerEvents;
import com.marcpg.pillarperil.game.Game;
import com.marcpg.pillarperil.game.util.GameManager;
import com.marcpg.pillarperil.game.util.StatsManager;
import com.marcpg.pillarperil.game.util.LobbyStorage;
import com.marcpg.pillarperil.game.util.LobbyManager;
import com.marcpg.pillarperil.generation.Generator;
import com.marcpg.pillarperil.generation.Platform;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@SuppressWarnings("UnstableApiUsage")
public class PillarPeril extends JavaPlugin {
    public static PillarPeril PLUGIN;
    public static Logger LOG;
    public static FileConfiguration CONFIG;
    private static Location END_SPAWN;

    @Override
    public void onEnable() {
        Locale.setDefault(Locale.of("en", "US"));
        saveDefaultConfig();

        PLUGIN = this;
        LOG = getSLF4JLogger();
        CONFIG = getConfig();

        try {
            translations();
        } catch (Exception e) {
            LOG.error("Could not load translations: {}", e.getMessage());
        }

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(Commands.games(), "Utilities for managing the Pillar Peril games or starting new ones.", List.of("pillarperil", "matches", "game-manager")));

        getServer().getPluginManager().registerEvents(new GameEvents(), this);
        getServer().getPluginManager().registerEvents(new PlayerEvents(), this);
        getServer().getPluginManager().registerEvents(new LobbyEvents(), this);
        getServer().getPluginManager().registerEvents(new LobbyHotbarListener(), this);

        Platform.platformHeight = CONFIG.getInt("platform-height");
        Platform.deathHeight = Platform.platformHeight - CONFIG.getInt("max-fall");
        Generator.platformDistanceFactor = CONFIG.getDouble("platform-distance-factor");

        END_SPAWN = loadEndSpawn();
        StatsManager.load(getDataFolder());
        LobbyStorage.loadAll(getDataFolder());
    }

    @Override
    public void onDisable() {
        // Need to create copy, because you can't loop over a list while removing values from it.
        List.copyOf(GameManager.GAMES).forEach(game -> game.end(Game.EndingCause.FORCE, List.of()));
        // Restore inventories for any players still sitting in lobbies to avoid wipes on shutdown.
        LobbyManager.LOBBIES.forEach(lobby -> List.copyOf(lobby.players()).forEach(lobby::leave));
        LobbyStorage.saveAll(getDataFolder());
        StatsManager.save(getDataFolder());
    }

    void translations() throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(new URI("https://marcpg.com/pillar-peril/lang/all")).GET().build();
            String response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
            Translation.loadMaps(new Gson().fromJson(response, new TypeToken<Map<Locale, Map<String, String>>>(){}.getType()));
        } catch (Exception e) {
            LOG.error("Could not retrieve translations from https://marcpg.com/pillar-peril/lang/all - PillarPeril will continue to work as usual, just without non-english translations.");

            Properties properties = new Properties();
            properties.load(this.getClassLoader().getResourceAsStream("en_US.properties"));
            Translation.loadSingleProperties(Locale.getDefault(), properties);
        }
    }

    public static Locale locale(Audience a) {
        return a instanceof Player p ? p.locale() : a instanceof PillarPlayer pp ? pp.locale() : Locale.getDefault();
    }

    public static void setEndSpawn(Location location) {
        END_SPAWN = location == null ? null : location.clone();
    }

    private Location loadEndSpawn() {
        String worldName = CONFIG.getString("end-spawn.world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return null;
        }

        Location spawn = world.getSpawnLocation();
        double x = CONFIG.getDouble("end-spawn.x", spawn.getX());
        double y = CONFIG.getDouble("end-spawn.y", spawn.getY());
        double z = CONFIG.getDouble("end-spawn.z", spawn.getZ());
        float yaw = (float) CONFIG.getDouble("end-spawn.yaw", spawn.getYaw());
        float pitch = (float) CONFIG.getDouble("end-spawn.pitch", spawn.getPitch());

        return new Location(world, x, y, z, yaw, pitch);
    }

    public static Location endSpawn(World fallbackWorld) {
        if (END_SPAWN != null) {
            return END_SPAWN.clone();
        }
        return fallbackWorld.getSpawnLocation();
    }
}
