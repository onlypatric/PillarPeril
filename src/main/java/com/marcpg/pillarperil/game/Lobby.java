package com.marcpg.pillarperil.game;

import com.marcpg.libpg.data.time.Time;
import com.marcpg.libpg.util.Randomizer;
import com.marcpg.pillarperil.game.util.LobbyManager;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.List;

public class Lobby {
    private static final ScoreboardManager SCOREBOARD_MANAGER = Bukkit.getScoreboardManager();

    private final String id = Randomizer.generateRandomString(10, "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
    private final Location center;
    private final World world;
    private final List<Player> players = new ArrayList<>();

    private final String modeKey;
    private final Class<? extends Game> modeClass;

    private final int minPlayers;
    private final int maxPlayers;
    private final long countdownSeconds;
    private Time countdown;
    private boolean countdownRunning = false;

    public Lobby(Location center, int minPlayers, int maxPlayers, long countdownSeconds, String modeKey, Class<? extends Game> modeClass) {
        this.center = center.clone();
        this.world = center.getWorld();
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.countdownSeconds = countdownSeconds;
        this.countdown = new Time(countdownSeconds);
        this.modeKey = modeKey;
        this.modeClass = modeClass;

        LobbyManager.LOBBIES.add(this);
    }

    public String id() {
        return id;
    }

    public Location center() {
        return center;
    }

    public World world() {
        return world;
    }

    public List<Player> players() {
        return players;
    }

    public String modeKey() {
        return modeKey;
    }

    public Class<? extends Game> modeClass() {
        return modeClass;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public Time countdown() {
        return countdown;
    }

    public boolean countdownRunning() {
        return countdownRunning;
    }

    public void join(Player player) {
        if (players.size() >= maxPlayers) return;
        if (!players.contains(player)) {
            players.add(player);
        }
    }

    public void leave(Player player) {
        if (!players.remove(player)) {
            return;
        }
        resetScoreboard(player);
        if (players.isEmpty()) {
            LobbyManager.LOBBIES.remove(this);
        }
    }

    public void cancel() {
        countdownRunning = false;
        // Reset scoreboards for all players before removing the lobby
        if (SCOREBOARD_MANAGER != null) {
            for (Player player : players) {
                resetScoreboard(player);
            }
        }
        LobbyManager.LOBBIES.remove(this);
    }

    /**
     * Placeholder for future lobby ticking logic.
     * For now, this just ensures the structure for countdown-based auto-start exists.
     */
    public void tick(int tick) {
        int size = players.size();
        if (!countdownRunning && size >= minPlayers) {
            countdownRunning = true;
            countdown = new Time(countdownSeconds);
        } else if (countdownRunning && size < minPlayers) {
            countdownRunning = false;
            countdown = new Time(countdownSeconds);
        }

        if (tick % 20 == 0) {
            if (countdownRunning) {
                countdown.decrement();
                String formatted = countdown.getOneUnitFormatted();
                for (Player player : players) {
                    player.sendActionBar(Component.text("Game starting in " + formatted));
                }

                if (countdown.get() <= 0) {
                    countdownRunning = false;
                    startGame();
                    return;
                }
            } else {
                for (Player player : players) {
                    player.sendActionBar(Component.text("Waiting for players..."));
                }
            }

            updateScoreboards();
        }
    }

    /**
     * Placeholder: this will later construct a concrete Game instance using Commands.MODES.
     * For step 1.1 we only establish the structure.
     */
    public void forceStart() {
        if (players.isEmpty()) {
            return;
        }
        startGame();
    }

    private void startGame() {
        if (players.isEmpty()) {
            return;
        }

        try {
            if (SCOREBOARD_MANAGER != null) {
                for (Player player : players) {
                    resetScoreboard(player);
                }
            }

            modeClass.getConstructor(Location.class, int.class, List.class)
                    .newInstance(center, Bukkit.getCurrentTick(), List.copyOf(players));
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().severe("Unable to start game for lobby " + id + ": " + e.getMessage());
        } finally {
            LobbyManager.LOBBIES.remove(this);
        }
    }

    private void updateScoreboards() {
        if (SCOREBOARD_MANAGER == null || players.isEmpty()) {
            return;
        }

        for (Player player : players) {
            Scoreboard current = player.getScoreboard();
            Objective objective;

            if (current == null || current == SCOREBOARD_MANAGER.getMainScoreboard()) {
                Scoreboard sb = SCOREBOARD_MANAGER.getNewScoreboard();
                objective = sb.registerNewObjective("pp_lobby", Criteria.DUMMY, Component.text("Pillar Peril Lobby"));
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                player.setScoreboard(sb);
            } else {
                objective = current.getObjective("pp_lobby");
                if (objective == null) {
                    objective = current.registerNewObjective("pp_lobby", Criteria.DUMMY, Component.text("Pillar Peril Lobby"));
                    objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                }
            }

            Scoreboard sb = player.getScoreboard();
            objective = sb.getObjective("pp_lobby");
            if (objective == null) {
                continue;
            }

            List<Component> lines = new ArrayList<>();
            lines.add(Component.text("Pillar Peril Lobby"));
            lines.add(Component.text("Mode: " + modeKey));
            lines.add(Component.text("Players: " + players.size() + "/" + minPlayers));
            String status = countdownRunning ? "Starting in " + countdown.getOneUnitFormatted() : "Waiting for players";
            lines.add(Component.text(status));

            for (int i = lines.size() - 1; i >= 0; i--) {
                Score score = objective.getScore("lobby-score-" + i);
                score.numberFormat(NumberFormat.blank());
                score.setScore(lines.size() - i);
                score.customName(lines.get(i));
            }
        }
    }

    private void resetScoreboard(Player player) {
        if (SCOREBOARD_MANAGER != null) {
            player.setScoreboard(SCOREBOARD_MANAGER.getMainScoreboard());
        }
    }
}
