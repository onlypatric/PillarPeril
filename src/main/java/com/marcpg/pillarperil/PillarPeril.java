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
import com.marcpg.pillarperil.game.util.LeaderboardHologramManager;
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
    private static Location ARENA_MIN;
    private static Location ARENA_MAX;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        PLUGIN = this;
        LOG = getSLF4JLogger();
        CONFIG = getConfig();

        Locale.setDefault(parseLocale(CONFIG.getString("locale", "en_US")));

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
        loadArenaBounds();
        StatsManager.load(getDataFolder());
        LobbyStorage.loadAll(getDataFolder());
        LeaderboardHologramManager.startAutoUpdate();
    }

    @Override
    public void onDisable() {
        // Need to create copy, because you can't loop over a list while removing values from it.
        List.copyOf(GameManager.GAMES).forEach(game -> game.end(Game.EndingCause.FORCE, List.of()));
        // Restore inventories for any players still sitting in lobbies to avoid wipes on shutdown.
        LobbyManager.LOBBIES.forEach(lobby -> List.copyOf(lobby.players()).forEach(lobby::leave));
        LobbyStorage.saveAll(getDataFolder());
        StatsManager.save(getDataFolder());
        LeaderboardHologramManager.clear();
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

            // Also load built-in Italian translations if present.
            try {
                Properties it = new Properties();
                var stream = this.getClassLoader().getResourceAsStream("it_IT.properties");
                if (stream != null) {
                    it.load(stream);
                    Translation.loadSingleProperties(Locale.of("it", "IT"), it);
                }
            } catch (Exception ignored) {
                // If Italian resources fail to load, we still have English.
            }
        }
    }

    public static void reloadCoreConfig() {
        if (PLUGIN == null) {
            return;
        }

        PLUGIN.reloadConfig();
        CONFIG = PLUGIN.getConfig();

        Locale.setDefault(parseLocale(CONFIG.getString("locale", "en_US")));

        Platform.platformHeight = CONFIG.getInt("platform-height");
        Platform.deathHeight = Platform.platformHeight - CONFIG.getInt("max-fall");
        Generator.platformDistanceFactor = CONFIG.getDouble("platform-distance-factor");

        END_SPAWN = PLUGIN.loadEndSpawn();
        PLUGIN.loadArenaBounds();
    }

    private static Locale parseLocale(String value) {
        if (value == null || value.isBlank()) {
            return Locale.of("en", "US");
        }
        String[] parts = value.split("[_\\-]");
        if (parts.length == 1) {
            return Locale.of(parts[0]);
        }
        return Locale.of(parts[0], parts[1]);
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

    private void loadArenaBounds() {
        String worldName = CONFIG.getString("arena-bounds.world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) {
            ARENA_MIN = null;
            ARENA_MAX = null;
            return;
        }

        int minX = CONFIG.getInt("arena-bounds.min.x");
        int minY = CONFIG.getInt("arena-bounds.min.y");
        int minZ = CONFIG.getInt("arena-bounds.min.z");
        int maxX = CONFIG.getInt("arena-bounds.max.x");
        int maxY = CONFIG.getInt("arena-bounds.max.y");
        int maxZ = CONFIG.getInt("arena-bounds.max.z");

        ARENA_MIN = new Location(world, Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ));
        ARENA_MAX = new Location(world, Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ));
    }

    public static Location arenaMin() {
        return ARENA_MIN == null ? null : ARENA_MIN.clone();
    }

    public static Location arenaMax() {
        return ARENA_MAX == null ? null : ARENA_MAX.clone();
    }

    public static void setArenaBounds(Location min, Location max) {
        if (min == null || max == null || min.getWorld() == null || max.getWorld() == null) {
            ARENA_MIN = null;
            ARENA_MAX = null;
            return;
        }

        if (!min.getWorld().equals(max.getWorld())) {
            throw new IllegalArgumentException("Arena bounds must be in the same world");
        }

        World world = min.getWorld();
        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        ARENA_MIN = new Location(world, minX, minY, minZ);
        ARENA_MAX = new Location(world, maxX, maxY, maxZ);

        CONFIG.set("arena-bounds.world", world.getName());
        CONFIG.set("arena-bounds.min.x", minX);
        CONFIG.set("arena-bounds.min.y", minY);
        CONFIG.set("arena-bounds.min.z", minZ);
        CONFIG.set("arena-bounds.max.x", maxX);
        CONFIG.set("arena-bounds.max.y", maxY);
        CONFIG.set("arena-bounds.max.z", maxZ);
        PLUGIN.saveConfig();
    }

    public static void applyArenaBarriers(World world) {
        if (ARENA_MIN == null || ARENA_MAX == null) return;
        if (!ARENA_MIN.getWorld().equals(world)) return;

        int minX = Math.min(ARENA_MIN.getBlockX(), ARENA_MAX.getBlockX());
        int minY = Math.min(ARENA_MIN.getBlockY(), ARENA_MAX.getBlockY());
        int minZ = Math.min(ARENA_MIN.getBlockZ(), ARENA_MAX.getBlockZ());
        int maxX = Math.max(ARENA_MIN.getBlockX(), ARENA_MAX.getBlockX());
        int maxY = Math.max(ARENA_MIN.getBlockY(), ARENA_MAX.getBlockY());
        int maxZ = Math.max(ARENA_MIN.getBlockZ(), ARENA_MAX.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // Make the barrier shell at least 3 blocks thick on all sides.
                    boolean nearXBoundary = (x - minX) <= 2 || (maxX - x) <= 2;
                    boolean nearYBoundary = (y - minY) <= 2 || (maxY - y) <= 2;
                    boolean nearZBoundary = (z - minZ) <= 2 || (maxZ - z) <= 2;
                    boolean isBoundary = nearXBoundary || nearYBoundary || nearZBoundary;
                    if (!isBoundary) continue;
                    world.getBlockAt(x, y, z).setType(org.bukkit.Material.BARRIER, false);
                }
            }
        }
    }

    public static void clearArena(World world) {
        if (ARENA_MIN == null || ARENA_MAX == null) return;
        if (!ARENA_MIN.getWorld().equals(world)) return;

        int minX = Math.min(ARENA_MIN.getBlockX(), ARENA_MAX.getBlockX());
        int minY = Math.min(ARENA_MIN.getBlockY(), ARENA_MAX.getBlockY());
        int minZ = Math.min(ARENA_MIN.getBlockZ(), ARENA_MAX.getBlockZ());
        int maxX = Math.max(ARENA_MIN.getBlockX(), ARENA_MAX.getBlockX());
        int maxY = Math.max(ARENA_MIN.getBlockY(), ARENA_MAX.getBlockY());
        int maxZ = Math.max(ARENA_MIN.getBlockZ(), ARENA_MAX.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR, false);
                }
            }
        }

        world.getEntities().stream()
                .filter(entity -> !(entity instanceof Player))
                .filter(entity -> {
                    Location loc = entity.getLocation();
                    if (!loc.getWorld().equals(world)) return false;
                    int ex = loc.getBlockX();
                    int ey = loc.getBlockY();
                    int ez = loc.getBlockZ();
                    return ex >= minX && ex <= maxX && ey >= minY && ey <= maxY && ez >= minZ && ez <= maxZ;
                })
                .forEach(org.bukkit.entity.Entity::remove);
    }
}
