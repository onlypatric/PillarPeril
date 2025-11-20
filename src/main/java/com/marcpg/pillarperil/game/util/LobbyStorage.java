package com.marcpg.pillarperil.game.util;

import com.marcpg.pillarperil.PillarPeril;
import com.marcpg.pillarperil.game.Game;
import com.marcpg.pillarperil.game.Lobby;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class LobbyStorage {
    private LobbyStorage() {
    }

    private static File file(File dataFolder) {
        return new File(dataFolder, "lobbies.yml");
    }

    public static void saveAll(File dataFolder) {
        YamlConfiguration cfg = new YamlConfiguration();
        ConfigurationSection root = cfg.createSection("lobbies");

        for (Lobby lobby : LobbyManager.LOBBIES) {
            if (lobby.state() == Lobby.LobbyState.DISABLED) continue;
            ConfigurationSection sec = root.createSection(lobby.id());
            sec.set("modeKey", lobby.modeKey());
            sec.set("modeClass", lobby.modeClass().getName());
            sec.set("minPlayers", lobby.minPlayers());
            sec.set("maxPlayers", lobby.maxPlayers());
            sec.set("countdownSeconds", lobby.countdownSeconds());
            writeLocation(sec.createSection("center"), lobby.center());
            writeLocation(sec.createSection("spawn"), lobby.lobbySpawn());
            writeLocation(sec.createSection("waitingSpawn"), lobby.waitingSpawn());
        }

        try {
            cfg.save(file(dataFolder));
        } catch (IOException e) {
            PillarPeril.LOG.error("Could not save lobbies.yml", e);
        }
    }

    public static void loadAll(File dataFolder) {
        LobbyManager.LOBBIES.clear();
        File f = file(dataFolder);
        if (!f.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = cfg.getConfigurationSection("lobbies");
        if (root == null) {
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            Location center = readLocation(sec.getConfigurationSection("center"));
            Location spawn = readLocation(sec.getConfigurationSection("spawn"));
            if (center == null || spawn == null) continue;
            Location waiting = readLocation(sec.getConfigurationSection("waitingSpawn"));

            int minPlayers = sec.getInt("minPlayers", 2);
            int maxPlayers = sec.getInt("maxPlayers", Math.max(minPlayers, 2));
            long countdownSeconds = sec.getLong("countdownSeconds", 30);
            String modeKey = sec.getString("modeKey", "original");
            String modeClassName = sec.getString("modeClass");
            Class<? extends Game> modeClass;
            try {
                modeClass = Class.forName(modeClassName).asSubclass(Game.class);
            } catch (Exception ex) {
                PillarPeril.LOG.error("Could not load lobby {} due to invalid mode class {}", id, modeClassName);
                continue;
            }

            Lobby lobby = new Lobby(id, center, minPlayers, maxPlayers, countdownSeconds, modeKey, modeClass);
            lobby.setLobbySpawn(spawn);
            if (waiting != null) {
                lobby.setWaitingSpawn(waiting);
            }
        }
    }

    private static void writeLocation(ConfigurationSection section, Location loc) {
        section.set("world", loc.getWorld().getName());
        section.set("x", loc.getX());
        section.set("y", loc.getY());
        section.set("z", loc.getZ());
        section.set("yaw", loc.getYaw());
        section.set("pitch", loc.getPitch());
    }

    private static Location readLocation(ConfigurationSection section) {
        if (section == null) return null;
        String worldName = section.getString("world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) {
            return null;
        }
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw", 0.0);
        float pitch = (float) section.getDouble("pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }
}
