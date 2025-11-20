package com.marcpg.pillarperil.game;

import com.marcpg.libpg.data.time.Time;
import com.marcpg.libpg.lang.Translation;
import com.marcpg.libpg.util.Randomizer;
import com.marcpg.pillarperil.PillarPeril;
import com.marcpg.pillarperil.PillarPlayer;
import com.marcpg.pillarperil.game.util.GameInfo;
import com.marcpg.pillarperil.game.util.GameManager;
import com.marcpg.pillarperil.generation.Platform;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.util.*;

public abstract class Game {
    public enum EndingCause { FORCE, TIME_OVER, LAST_STANDING, DRAW }

    public final String id = Randomizer.generateRandomString(10, "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
    public final Location center;
    public final World world;
    public final Audience initialAudience;

    protected final List<PillarPlayer> initialPlayers = new ArrayList<>();
    protected final List<PillarPlayer> players = new ArrayList<>();
    protected final Map<Location, BlockData> initialBlocks = new HashMap<>();
    protected List<Material> items;
    protected Lobby originLobby;

    protected long itemCooldown = info().itemCooldown();
    protected final Time timeLeft = new Time(info().timeLimit());
    private boolean ended = false;

    protected final Style keyStyle = Style.style(info().accentColor(), TextDecoration.BOLD);
    protected final Style valueStyle = Style.style(NamedTextColor.WHITE, TextDecoration.BOLD.withState(false));

    public Game(@NotNull Location center, int startTick, @NotNull List<Player> players) {
        this.center = center.clone();
        this.center.setY(Platform.platformHeight + 1);

        this.world = center.getWorld();

        players.stream()
                .map(player -> new PillarPlayer(player, this))
                .peek(this.initialPlayers::add)
                .forEach(this.players::add);

        this.initialAudience = Audience.audience(initialPlayers);
        //noinspection removal
        this.items = Arrays.stream(Material.values())
                .filter(m -> isAllowedMaterial(m, world))
                .filter(info().filter()::test)
                .toList();

        try {
            List<Location> pillars = info().generator().getConstructor(Game.class, Location.class, int.class).newInstance(this, center.clone(), players.size()).generate();
            for (int i = 0; i < pillars.size(); i++) {
                players.get(i).teleport(pillars.get(i));
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        GameManager.GAMES.add(this);
    }

    public abstract GameInfo info();
    public @Nullable Component actionbar(PillarPlayer player) { return null; }
    public @Nullable List<Component> scoreboard(@NotNull PillarPlayer player) {
        Locale l = player.locale();
        return List.of(
                MiniMessage.miniMessage().deserialize("<bold><gradient:#71CCF8:#FC91EC:#F87171>Pillar Peril"),
                Translation.component(l, "scoreboard.mode").style(keyStyle).appendSpace().append(Component.text(info().name(l), valueStyle)),
                Translation.component(l, "scoreboard.name").style(keyStyle).appendSpace().append(player.player.name().style(valueStyle)),
                Translation.component(l, "scoreboard.time").style(keyStyle).appendSpace().append(Component.text(timeLeft.getOneUnitFormatted(), valueStyle)),
                Translation.component(l, "scoreboard.kills").style(keyStyle).appendSpace().append(Component.text(player.kills(), valueStyle))
        );
    }

    public final List<PillarPlayer> initialPlayers() {
        return initialPlayers;
    }

    public final List<PillarPlayer> players() {
        return players;
    }

    public final @Nullable PillarPlayer player(Player player, boolean onlyAlive) {
        for (PillarPlayer p : onlyAlive ? players : initialPlayers) {
            if (p.player == player) return p;
        }
        return null;
    }

    public final @NotNull Audience audience(boolean onlyAlive) {
        return Audience.audience(onlyAlive ? players : initialPlayers);
    }

    public Time timeLeft() {
        return timeLeft;
    }

    public long itemCooldown() {
        return itemCooldown;
    }

    public void eliminate(PillarPlayer player) {
        players.remove(player);

        Bukkit.getScheduler().runTaskLater(PillarPeril.PLUGIN, () -> {
            player.player.teleport(center);
            player.player.setGameMode(GameMode.SPECTATOR);
            checkEndCondition();
        }, 20);
    }

    public final void addBlock(Location location, BlockData oldData) {
        if (!initialBlocks.containsKey(location))
            initialBlocks.put(location, oldData);
    }

    @OverridingMethodsMustInvokeSuper
    public void tick(int tick) {
        if (tick % 10 == 0) {
            players.forEach(PillarPlayer::tick);
            if (tick % 20 == 0)
                tickSecond();
        }
    }

    @OverridingMethodsMustInvokeSuper
    public void tickSecond() {
        itemCooldown--;
        if (itemCooldown <= 0) {
            players.forEach(p -> p.giveItem(items));
            itemCooldown = info().itemCooldown();
        }

        timeLeft.decrement();
        if (timeLeft.get() <= 0)
            end(EndingCause.FORCE, List.of());
    }

    public void end(@NotNull EndingCause cause, List<PillarPlayer> winners) {
        if (ended) return;
        ended = true;
        if (originLobby != null) {
            originLobby.onGameEnded(this);
        }

        for (PillarPlayer p : initialPlayers) {
            Locale l = p.locale();
            switch (cause) {
                case FORCE -> p.showTitle(Title.title(Translation.component(l, "info.end.force.title").color(NamedTextColor.YELLOW), Translation.component(l, "info.end.force.subtitle").color(NamedTextColor.RED)));
                case TIME_OVER -> {
                    p.showTitle(Title.title(Translation.component(l, "info.end.time-over.title").color(NamedTextColor.GREEN), Translation.component(l, "info.end.time-over.subtitle").color(NamedTextColor.YELLOW)));

                    p.sendMessage(Component.text("=== ").append(Translation.component(l, "info.end.time-over.stats", players.size()).append(Component.text(" ===")).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)));
                    List<PillarPlayer> sortedPlayers = players.stream().sorted(Comparator.comparingInt(PillarPlayer::kills)).toList();
                    for (int i = 0; i < sortedPlayers.size(); i++) {
                        p.sendMessage(Component.text(i + ". " + sortedPlayers.get(i).player.getName() + ": " + sortedPlayers.get(i).kills()));
                    }
                }
                case LAST_STANDING -> p.showTitle(Title.title(Translation.component(l, "info.end.last-standing.title", winners.getFirst().player.getName()).color(NamedTextColor.GREEN), Translation.component(l, "info.end.last-standing.subtitle", winners.getFirst().kills()).color(NamedTextColor.YELLOW)));
                case DRAW -> {
                    p.showTitle(Title.title(Translation.component(l, "info.end.draw.title").color(NamedTextColor.GOLD), Translation.component(l, "info.end.draw.subtitle").color(NamedTextColor.YELLOW)));

                    p.sendMessage(Component.text("=== ").append(Translation.component(l, "info.end.time-over.stats", initialPlayers.size()).append(Component.text(" ===")).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)));
                    List<PillarPlayer> sortedPlayers = initialPlayers.stream().sorted(Comparator.comparingInt(PillarPlayer::kills)).toList();
                    for (int i = 0; i < sortedPlayers.size(); i++) {
                        p.sendMessage(Component.text(i + ". " + sortedPlayers.get(i).player.getName() + ": " + sortedPlayers.get(i).kills()));
                    }
                }
            }
        }
        if (cause == EndingCause.LAST_STANDING) {
            winners.forEach(com.marcpg.pillarperil.game.util.StatsManager::recordWin);
        }
        cleanup();
    }

    private void checkEndCondition() {
        if (ended) return;

        if (players.size() == 1) {
            end(EndingCause.LAST_STANDING, List.of(players.getFirst()));
        } else if (players.isEmpty()) {
            end(EndingCause.DRAW, List.of());
        }
    }

    public void cleanup() {
        GameManager.GAMES.remove(this);
        initialPlayers.forEach(PillarPlayer::clean);
        initialBlocks.forEach((location, blockData) -> location.getBlock().setBlockData(blockData));
    }

    public static boolean hasUse(@NotNull Material m) {
        return m.isSolid();
    }

    public void setOriginLobby(@Nullable Lobby lobby) {
        this.originLobby = lobby;
    }

    public @Nullable Lobby originLobby() {
        return originLobby;
    }

    private static boolean isAllowedMaterial(Material material, World world) {
        return !isAirSafe(material) && !isLegacySafe(material) && isItemSafe(material) && isMaterialEnabled(material, world);
    }

    @SuppressWarnings("removal")
    private static boolean isMaterialEnabled(Material material, World world) {
        try {
            return material.isEnabledByFeature(world);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static boolean isAirSafe(Material material) {
        try {
            return material.isAir();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isLegacySafe(Material material) {
        try {
            return material.isLegacy();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isItemSafe(Material material) {
        try {
            return material.isItem();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
