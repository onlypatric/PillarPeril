package com.marcpg.pillarperil.game;

import com.marcpg.libpg.data.time.Time;
import com.marcpg.libpg.lang.Translation;
import com.marcpg.libpg.util.Randomizer;
import com.marcpg.pillarperil.PillarPeril;
import com.marcpg.pillarperil.game.util.LobbyManager;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class Lobby {
    private static final ScoreboardManager SCOREBOARD_MANAGER = Bukkit.getScoreboardManager();

    public enum LobbyState { WAITING, COUNTDOWN, IN_GAME, DISABLED }

    public enum JoinResult {
        SUCCESS,
        DISABLED,
        IN_GAME,
        FULL,
        ALREADY_PRESENT
    }

    public enum HotbarAction { START, LEAVE }

    private static NamespacedKey hotbarKey() {
        if (PillarPeril.PLUGIN != null) {
            return new NamespacedKey(PillarPeril.PLUGIN, "lobby_hotbar_action");
        }
        NamespacedKey key = NamespacedKey.fromString("pillarperil:lobby_hotbar_action");
        if (key == null) {
            key = NamespacedKey.minecraft("pillarperil_lobby_hotbar_action");
        }
        return key;
    }

    private final String id;
    private final Location center;
    private final World world;
    private final List<Player> players = new ArrayList<>();
    private final List<UUID> lastParticipants = new ArrayList<>();
    private final Map<UUID, InventorySnapshot> storedInventories = new HashMap<>();
    private Location waitingSpawn;
    private Location lobbySpawn;

    private final String modeKey;
    private final Class<? extends Game> modeClass;

    private final int minPlayers;
    private final int maxPlayers;
    private final long countdownSeconds;
    private Time countdown;
    private LobbyState state = LobbyState.WAITING;
    private Game currentGame;

    public Lobby(Location center, int minPlayers, int maxPlayers, long countdownSeconds, String modeKey, Class<? extends Game> modeClass) {
        this(Randomizer.generateRandomString(10, "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"), center, minPlayers, maxPlayers, countdownSeconds, modeKey, modeClass);
    }

    public Lobby(String id, Location center, int minPlayers, int maxPlayers, long countdownSeconds, String modeKey, Class<? extends Game> modeClass) {
        if (minPlayers <= 0) {
            throw new IllegalArgumentException("minPlayers must be > 0");
        }
        if (minPlayers > maxPlayers) {
            throw new IllegalArgumentException("minPlayers cannot exceed maxPlayers");
        }
        this.id = id;
        this.center = center.clone();
        this.world = center.getWorld();
        this.lobbySpawn = this.center.clone();
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

    public long countdownSeconds() {
        return countdownSeconds;
    }

    public LobbyState state() {
        return state;
    }

    public Game currentGame() {
        return currentGame;
    }

    public List<UUID> lastParticipants() {
        return List.copyOf(lastParticipants);
    }

    public Location lobbySpawn() {
        return lobbySpawn.clone();
    }

    public void setLobbySpawn(Location newSpawn) {
        this.lobbySpawn = newSpawn.clone();
    }

    public Location waitingSpawn() {
        return waitingSpawn != null ? waitingSpawn.clone() : lobbySpawn();
    }

    public void setWaitingSpawn(Location location) {
        this.waitingSpawn = location == null ? null : location.clone();
    }

    public JoinResult join(Player player) {
        if (state == LobbyState.DISABLED) return JoinResult.DISABLED;
        if (state == LobbyState.IN_GAME) return JoinResult.IN_GAME;
        if (players.contains(player)) return JoinResult.ALREADY_PRESENT;
        if (players.size() >= maxPlayers) return JoinResult.FULL;

        players.add(player);
        player.teleport(waitingSpawn());
        applyHotbar(player);
        updateScoreboards();
        updateStartItemsForAll();
        evaluatePlayerThresholds();
        return JoinResult.SUCCESS;
    }

    public void leave(Player player) {
        if (!players.remove(player)) {
            return;
        }
        resetScoreboard(player);
        restoreInventory(player, true);
        updateStartItemsForAll();
        updateScoreboards();
        // Do not re-evaluate thresholds while countdown is running;
        // once started, countdown should ignore joins/leaves.
        if (state == LobbyState.WAITING) {
            evaluatePlayerThresholds();
        }
    }

    public void cancel() {
        state = LobbyState.DISABLED;
        if (SCOREBOARD_MANAGER != null) {
            for (Player player : players) {
                resetScoreboard(player);
            }
        }
        for (Player player : players) {
            restoreInventory(player, true);
        }
        players.clear();
    }

    /**
     * Placeholder for future lobby ticking logic.
     * For now, this just ensures the structure for countdown-based auto-start exists.
     */
    public void tick(int tick) {
        if (state == LobbyState.DISABLED || state == LobbyState.IN_GAME) {
            return;
        }

        evaluatePlayerThresholds();

        if (tick % 20 == 0) {
            if (state == LobbyState.COUNTDOWN) {
                countdown.decrement();
                for (Player player : players) {
                    player.sendActionBar(Translation.component(player.locale(), "games.lobby.actionbar.countdown", countdown.getOneUnitFormatted()));
                }
                updateStartItemsForAll();

                long secondsLeft = countdown.get();
                if ((secondsLeft % 5 == 0 && secondsLeft > 5) || secondsLeft <= 5) {
                    for (Player player : players) {
                        player.sendMessage(Translation.component(player.locale(), "games.lobby.countdown.broadcast", secondsLeft));
                    }
                }

                if (countdown.get() <= 0) {
                    state = LobbyState.WAITING;
                    startGame();
                    return;
                }
            } else if (state == LobbyState.WAITING) {
                for (Player player : players) {
                    player.sendActionBar(Translation.component(player.locale(), "games.lobby.actionbar.waiting"));
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
        if (players.isEmpty() || state == LobbyState.DISABLED || currentGame != null) {
            return;
        }
        startGame();
    }

    private void startGame() {
        if (players.isEmpty() || currentGame != null || state == LobbyState.DISABLED) {
            return;
        }
        state = LobbyState.IN_GAME;
        lastParticipants.clear();
        for (Player player : players) {
            lastParticipants.add(player.getUniqueId());
            restoreInventory(player, false);
        }

        try {
            if (SCOREBOARD_MANAGER != null) {
                for (Player player : players) {
                    resetScoreboard(player);
                }
            }

            Game game = modeClass.getConstructor(Location.class, int.class, List.class)
                    .newInstance(center, Bukkit.getCurrentTick(), List.copyOf(players));
            game.setOriginLobby(this);
            currentGame = game;
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().log(java.util.logging.Level.SEVERE, "Unable to start game for lobby " + id, e);
            state = LobbyState.WAITING;
            currentGame = null;
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

            Locale locale = player.locale();
            List<Component> lines = new ArrayList<>();
            lines.add(Translation.component(locale, "games.lobby.scoreboard.title"));
            lines.add(Translation.component(locale, "games.lobby.scoreboard.mode", modeKey));
            lines.add(Translation.component(locale, "games.lobby.scoreboard.players", players.size(), maxPlayers));
            Component status = switch (state) {
                case COUNTDOWN ->
                        Translation.component(locale, "games.lobby.scoreboard.status.countdown", countdown.getOneUnitFormatted());
                case IN_GAME ->
                        Translation.component(locale, "games.lobby.scoreboard.status.in_game");
                case DISABLED ->
                        Translation.component(locale, "games.lobby.scoreboard.status.disabled");
                default ->
                        Translation.component(locale, "games.lobby.scoreboard.status.waiting");
            };
            lines.add(status);

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

    private void startCountdown() {
        state = LobbyState.COUNTDOWN;
        countdown = new Time(countdownSeconds);
        for (Player player : players) {
            player.sendMessage(Translation.component(player.locale(), "games.lobby.countdown.start", countdown.getOneUnitFormatted()));
        }
        updateStartItemsForAll();
    }

    public void onGameEnded(Game game) {
        if (currentGame != game) {
            return;
        }
        currentGame = null;
        if (state != LobbyState.DISABLED) {
            state = LobbyState.WAITING;
            countdown = new Time(countdownSeconds);
            List<Player> remaining = List.copyOf(players);
            remaining.forEach(this::leave);
        }
    }

    private void evaluatePlayerThresholds() {
        int activePlayers = players.size();
        if (state == LobbyState.WAITING && activePlayers >= minPlayers) {
            startCountdown();
        }
    }

    private void applyHotbar(Player player) {
        if (state == LobbyState.DISABLED || state == LobbyState.IN_GAME) {
            return;
        }
        storedInventories.computeIfAbsent(player.getUniqueId(), id -> InventorySnapshot.capture(player));

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[inventory.getArmorContents().length]);
        inventory.setExtraContents(new ItemStack[inventory.getExtraContents().length]);
        inventory.setItemInOffHand(null);

        updateStartItem(player);
        inventory.setItem(8, createLeaveItem(player));
    }

    private boolean canUseStartHotbar(Player player) {
        return player.hasPermission("pillarperil.lobby.force-start") || player.hasPermission("pillarperil.lobby.start");
    }

    private void restoreInventory(Player player, boolean releaseSnapshot) {
        InventorySnapshot snapshot = storedInventories.get(player.getUniqueId());
        if (snapshot == null) {
            return;
        }
        snapshot.restore(player);
        if (releaseSnapshot) {
            storedInventories.remove(player.getUniqueId());
        }
    }

    private ItemStack createStartItem(Player player) {
        Locale locale = player.locale();
        List<Component> lore = new ArrayList<>();
        lore.add(Translation.component(locale, "games.lobby.hotbar.start.lore").color(NamedTextColor.GRAY));
        if (state == LobbyState.COUNTDOWN) {
            lore.add(Translation.component(locale, "games.lobby.hotbar.start.countdown", countdown.getOneUnitFormatted()).color(NamedTextColor.AQUA));
        } else {
            lore.add(Translation.component(locale, "games.lobby.hotbar.start.waiting", minPlayers).color(NamedTextColor.DARK_GRAY));
        }
        return createHotbarItem(Material.EMERALD, Translation.component(locale, "games.lobby.hotbar.start").color(NamedTextColor.GREEN), lore, HotbarAction.START);
    }

    private ItemStack createLeaveItem(Player player) {
        Locale locale = player.locale();
        List<Component> lore = List.of(Translation.component(locale, "games.lobby.hotbar.leave.lore").color(NamedTextColor.GRAY));
        return createHotbarItem(Material.RED_DYE, Translation.component(locale, "games.lobby.hotbar.leave").color(NamedTextColor.RED), lore, HotbarAction.LEAVE);
    }

    private ItemStack createHotbarItem(Material material, Component displayName, List<Component> lore, HotbarAction action) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(displayName);
        meta.lore(lore);
        meta.getPersistentDataContainer().set(hotbarKey(), PersistentDataType.STRING, action.name());
        stack.setItemMeta(meta);
        return stack;
    }

    private void updateStartItem(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (canUseStartHotbar(player)) {
            player.getInventory().setItem(4, createStartItem(player));
        } else {
            player.getInventory().setItem(4, null);
        }
    }

    private void updateStartItemsForAll() {
        for (Player player : players) {
            updateStartItem(player);
        }
    }

    public static HotbarAction hotbarAction(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        String actionName = meta.getPersistentDataContainer().get(hotbarKey(), PersistentDataType.STRING);
        if (actionName == null) {
            return null;
        }
        try {
            return HotbarAction.valueOf(actionName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record InventorySnapshot(ItemStack[] contents, ItemStack[] armor, ItemStack[] extra, ItemStack offhand,
                                     float exp, int level, int totalExp) {
        static InventorySnapshot capture(Player player) {
            PlayerInventory inv = player.getInventory();
            return new InventorySnapshot(
                    clone(inv.getContents()),
                    clone(inv.getArmorContents()),
                    clone(inv.getExtraContents()),
                    inv.getItemInOffHand() == null ? null : inv.getItemInOffHand().clone(),
                    player.getExp(),
                    player.getLevel(),
                    player.getTotalExperience()
            );
        }

        void restore(Player player) {
            PlayerInventory inv = player.getInventory();
            inv.setContents(clone(contents));
            inv.setArmorContents(clone(armor));
            inv.setExtraContents(clone(extra));
            inv.setItemInOffHand(offhand == null ? null : offhand.clone());
            player.setExp(exp);
            player.setLevel(level);
            player.setTotalExperience(totalExp);
        }

        private static ItemStack[] clone(ItemStack[] source) {
            ItemStack[] copy = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) {
                copy[i] = source[i] == null ? null : source[i].clone();
            }
            return copy;
        }
    }
}
