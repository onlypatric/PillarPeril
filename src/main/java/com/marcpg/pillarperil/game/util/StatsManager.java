package com.marcpg.pillarperil.game.util;

import com.marcpg.pillarperil.PillarPlayer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class StatsManager {
    private static final Map<UUID, PlayerStats> STATS = new HashMap<>();

    public static void load(@NotNull File dataFolder) {
        File file = new File(dataFolder, "stats.yml");
        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int wins = cfg.getInt(key + ".wins", 0);
                int kills = cfg.getInt(key + ".kills", 0);
                String name = cfg.getString(key + ".name", "");
                STATS.put(uuid, new PlayerStats(uuid, name, wins, kills));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static void save(@NotNull File dataFolder) {
        File file = new File(dataFolder, "stats.yml");
        YamlConfiguration cfg = new YamlConfiguration();

        for (PlayerStats stats : STATS.values()) {
            String path = stats.uuid().toString();
            cfg.set(path + ".wins", stats.wins());
            cfg.set(path + ".kills", stats.kills());
            cfg.set(path + ".name", stats.name());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().severe("Could not save PillarPeril stats: " + e.getMessage());
        }
    }

    public static void recordKill(@NotNull PillarPlayer player) {
        PlayerStats stats = STATS.computeIfAbsent(player.uuid(), uuid -> new PlayerStats(uuid, player.player.getName(), 0, 0));
        stats.incrementKills();
    }

    public static void recordWin(@NotNull PillarPlayer player) {
        PlayerStats stats = STATS.computeIfAbsent(player.uuid(), uuid -> new PlayerStats(uuid, player.player.getName(), 0, 0));
        stats.incrementWins();
    }

    public static List<PlayerStats> topWins(int limit) {
        return STATS.values().stream()
                .sorted(Comparator.comparingInt(PlayerStats::wins).reversed())
                .limit(limit)
                .toList();
    }

    public static List<PlayerStats> topKills(int limit) {
        return STATS.values().stream()
                .sorted(Comparator.comparingInt(PlayerStats::kills).reversed())
                .limit(limit)
                .toList();
    }

    // For tests to reset state between runs.
    public static void reset() {
        STATS.clear();
    }

    public record PlayerStats(UUID uuid, String name, int wins, int kills) {
        public PlayerStats incrementWins() {
            return update(wins + 1, kills);
        }

        public PlayerStats incrementKills() {
            return update(wins, kills + 1);
        }

        private PlayerStats update(int newWins, int newKills) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            String latestName = offline.getName() != null ? offline.getName() : name;
            PlayerStats updated = new PlayerStats(uuid, latestName, newWins, newKills);
            STATS.put(uuid, updated);
            return updated;
        }
    }
}
