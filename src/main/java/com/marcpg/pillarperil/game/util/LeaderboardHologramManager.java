package com.marcpg.pillarperil.game.util;

import com.marcpg.pillarperil.PillarPeril;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class LeaderboardHologramManager {
    private static final List<ArmorStand> HOLOGRAM_LINES = new ArrayList<>();
    private static final int LIMIT = 10;

    private LeaderboardHologramManager() {
    }

    public static void createOrMove(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation().clone().add(0.5, 0.0, 0.5);

        clear();

        double yOffset = 0.0;
        // Compute total lines: 1 header + LIMIT wins + 1 spacer + 1 header + LIMIT kills.
        int totalLines = 1 + LIMIT + 1 + 1 + LIMIT;
        for (int i = 0; i < totalLines; i++) {
            Location lineLoc = base.clone().add(0, yOffset, 0);
            ArmorStand stand = (ArmorStand) world.spawnEntity(lineLoc, EntityType.ARMOR_STAND);
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setCustomNameVisible(true);
            HOLOGRAM_LINES.add(stand);
            yOffset += 0.25;
        }

        update();
    }

    public static void update() {
        if (HOLOGRAM_LINES.isEmpty()) {
            return;
        }

        List<StatsManager.PlayerStats> wins = StatsManager.topWins(LIMIT);
        List<StatsManager.PlayerStats> kills = StatsManager.topKills(LIMIT);

        int index = 0;

        // Wins header
        HOLOGRAM_LINES.get(index++).customName(Component.text("Top Wins", NamedTextColor.GOLD));
        // Wins entries
        for (int i = 0; i < LIMIT; i++) {
            Component line;
            if (i < wins.size()) {
                StatsManager.PlayerStats stats = wins.get(i);
                line = Component.text((i + 1) + ". " + stats.name() + " - " + stats.wins() + " wins", NamedTextColor.YELLOW);
            } else {
                line = Component.empty();
            }
            HOLOGRAM_LINES.get(index++).customName(line);
        }

        // Spacer
        HOLOGRAM_LINES.get(index++).customName(Component.empty());

        // Kills header
        HOLOGRAM_LINES.get(index++).customName(Component.text("Top Kills", NamedTextColor.RED));
        // Kills entries
        for (int i = 0; i < LIMIT; i++) {
            Component line;
            if (i < kills.size()) {
                StatsManager.PlayerStats stats = kills.get(i);
                line = Component.text((i + 1) + ". " + stats.name() + " - " + stats.kills() + " kills", NamedTextColor.DARK_RED);
            } else {
                line = Component.empty();
            }
            HOLOGRAM_LINES.get(index++).customName(line);
        }
    }

    public static void clear() {
        if (!HOLOGRAM_LINES.isEmpty()) {
            HOLOGRAM_LINES.forEach(armorStand -> {
                if (armorStand != null && armorStand.isValid()) {
                    armorStand.remove();
                }
            });
            HOLOGRAM_LINES.clear();
        }
    }

    public static void startAutoUpdate() {
        // Periodically refresh hologram text so leaderboards stay accurate.
        Bukkit.getScheduler().runTaskTimer(PillarPeril.PLUGIN, LeaderboardHologramManager::update, 20L * 10, 20L * 10);
    }
}
