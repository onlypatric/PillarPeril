package com.marcpg.pillarperil;

import com.marcpg.libpg.util.Randomizer;
import com.marcpg.pillarperil.game.Game;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scoreboard.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PillarPlayer implements ForwardingAudience.Single {
    private static final ScoreboardManager SCOREBOARD_MANAGER = Bukkit.getScoreboardManager();

    public final Player player;
    public final Game game;
    private final com.marcpg.pillarperil.game.Lobby originLobby;

    private int kills = 0;

    private final ItemStack[] previousContents;
    private final ItemStack[] previousArmorContents;
    private final ItemStack[] previousExtraContents;
    private final float previousExp;
    private final int previousLevel;
    private final int previousTotalExp;

    public PillarPlayer(Player player, Game game) {
        this.player = player;
        this.game = game;
        this.originLobby = game == null ? null : game.originLobby();

        PlayerInventory inv = player.getInventory();
        this.previousContents = inv.getContents().clone();
        this.previousArmorContents = inv.getArmorContents().clone();
        this.previousExtraContents = inv.getExtraContents().clone();
        this.previousExp = player.getExp();
        this.previousLevel = player.getLevel();
        this.previousTotalExp = player.getTotalExperience();

        player.setGameMode(GameMode.SURVIVAL);
        player.clearActivePotionEffects();
        player.getInventory().clear();
        player.setHealth(20.0);
    }

    public void giveItem(List<Material> availableItems) {
        player.getInventory().addItem(new ItemStack(Randomizer.fromCollection(availableItems)));
    }

    public void tick() {
        List<Component> scoreboard = game.scoreboard(this);
        if (scoreboard != null && !scoreboard.isEmpty()) {
            Objective objective;

            if (player.getScoreboard() == SCOREBOARD_MANAGER.getMainScoreboard()) {
                Scoreboard sb = SCOREBOARD_MANAGER.getNewScoreboard();
                objective = sb.registerNewObjective("pp", Criteria.DUMMY, scoreboard.getFirst());
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                player.setScoreboard(sb);
            } else {
                objective = player.getScoreboard().getObjective("pp");
                if (objective == null)
                    objective = player.getScoreboard().registerNewObjective("pp", Criteria.DUMMY, scoreboard.getFirst());
            }

            for (int i = scoreboard.size() - 1; i > 0; i--) {
                Score score = objective.getScore("score-" + i);
                score.numberFormat(NumberFormat.blank());
                score.setScore(scoreboard.size() - i);

                score.customName(scoreboard.get(i));
            }
        }

        Component actionbar = game.actionbar(this);
        if (actionbar != null && !actionbar.equals(Component.empty()))
            player.sendActionBar(actionbar);
    }

    public void addKill() {
        kills++;
    }

    public int kills() {
        return kills;
    }

    @Override
    public @NotNull Audience audience() {
        return player;
    }

    public Locale locale() {
        return player.locale();
    }

    public UUID uuid() {
        return player.getUniqueId();
    }

    public void clean() {
        player.setScoreboard(SCOREBOARD_MANAGER.getMainScoreboard());
        player.clearActivePotionEffects();
        if (originLobby != null) {
            player.teleport(originLobby.lobbySpawn());
        } else {
            player.teleport(PillarPeril.endSpawn(game.world));
        }
        player.setGameMode(GameMode.SURVIVAL);

        PlayerInventory inv = player.getInventory();
        inv.setContents(previousContents);
        inv.setArmorContents(previousArmorContents);
        inv.setExtraContents(previousExtraContents);
        player.setExp(previousExp);
        player.setLevel(previousLevel);
        player.setTotalExperience(previousTotalExp);
    }
}
