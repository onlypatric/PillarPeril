package com.marcpg.pillarperil;

import com.marcpg.libpg.lang.Translation;
import com.marcpg.pillarperil.game.Game;
import com.marcpg.pillarperil.game.Lobby;
import com.marcpg.pillarperil.game.mode.*;
import com.marcpg.pillarperil.game.util.GameManager;
import com.marcpg.pillarperil.game.util.LobbyManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public class Commands {
    public static final Component SEPARATOR_20 = Component.text("====================", NamedTextColor.DARK_GRAY);

    public static final Map<String, Class<? extends Game>> MODES = Map.of(
            "original", OriginalMode.class,
            "blocky", BlockyMode.class,
            "cubecraft", CubeCraftMode.class,
            "chaos", ChaosMode.class,
            "items-only", ItemOnlyMode.class
    );

    public static LiteralCommandNode<CommandSourceStack> games() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("games")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("start")
                        .requires(source -> source.getSender().hasPermission("pillarperil.start"))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("force")
                                .requires(source -> source.getSender().hasPermission("pillarperil.start.force"))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            MODES.keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, BlockPositionResolver>argument("center", ArgumentTypes.blockPosition())
                                                .then(RequiredArgumentBuilder.<CommandSourceStack, World>argument("world", ArgumentTypes.world())
                                                        .then(RequiredArgumentBuilder.<CommandSourceStack, PlayerSelectorArgumentResolver>argument("players", ArgumentTypes.players())
                                                                .executes(context -> {
                                                                    CommandSender source = context.getSource().getSender();
                                                                    Locale l = PillarPeril.locale(source);

                                                                    String mode = context.getArgument("mode", String.class);
                                                                    List<Player> players = context.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(context.getSource());
                                                                    BlockPosition centerPos = context.getArgument("center", BlockPositionResolver.class).resolve(context.getSource());
                                                                    Location center = centerPos.toLocation(context.getArgument("world", World.class));

                                                                    if (players.stream().anyMatch(p -> GameManager.game(p, true) != null)) {
                                                                        source.sendMessage(Translation.component(l, "games.start.player_in_game").color(NamedTextColor.RED));
                                                                        return 1;
                                                                    }

                                                                    if (MODES.containsKey(mode)) {
                                                                        try {
                                                                            MODES.get(mode).getConstructor(Location.class, int.class, List.class).newInstance(center, Bukkit.getCurrentTick(), players);
                                                                            source.sendMessage(Translation.component(l, "games.start.success").color(NamedTextColor.GREEN));
                                                                        } catch (ReflectiveOperationException e) {
                                                                            source.sendMessage(Translation.component(l, "games.start.internal_error").color(NamedTextColor.RED));
                                                                        }
                                                                    } else {
                                                                        source.sendMessage(Translation.component(l, "games.start.invalid_mode").color(NamedTextColor.RED));
                                                                    }
                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )
                )
                // Lobby subcommands provide an additional way to start games, without replacing /games start
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("lobby")
                        .requires(source -> source.getSender().hasPermission("pillarperil.lobby"))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("create")
                                .requires(source -> source.getSender().hasPermission("pillarperil.lobby.create"))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            MODES.keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, BlockPositionResolver>argument("center", ArgumentTypes.blockPosition())
                                                .then(RequiredArgumentBuilder.<CommandSourceStack, World>argument("world", ArgumentTypes.world())
                                                        .executes(context -> {
                                                            CommandSender source = context.getSource().getSender();
                                                            Locale l = PillarPeril.locale(source);

                                                            String mode = context.getArgument("mode", String.class);
                                                            if (!MODES.containsKey(mode)) {
                                                                source.sendMessage(Translation.component(l, "games.start.invalid_mode").color(NamedTextColor.RED));
                                                                return 1;
                                                            }

                                                            BlockPosition centerPos = context.getArgument("center", BlockPositionResolver.class).resolve(context.getSource());
                                                            Location center = centerPos.toLocation(context.getArgument("world", World.class));

                                                            // Basic defaults; can be made configurable later
                                                            int minPlayers = 2;
                                                            int maxPlayers = 16;

                                                            // Countdown of 30 seconds by default; precise value can be moved to config
                                                            long countdownSeconds = com.marcpg.libpg.data.time.Time.parse("30sec").get();

                                                            Class<? extends Game> modeClass = MODES.get(mode);
                                                            Lobby lobby = new Lobby(center, minPlayers, maxPlayers, countdownSeconds, mode, modeClass);

                                                            source.sendMessage(Translation.component(l, "games.lobby.create.success").color(NamedTextColor.GREEN)
                                                                    .appendSpace()
                                                                    .append(Component.text("(" + lobby.id() + ")", NamedTextColor.GRAY)));
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("join")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("id", StringArgumentType.word())
                                        .executes(context -> {
                                            CommandSender source = context.getSource().getSender();
                                            if (!(source instanceof Player player)) {
                                                source.sendMessage(Component.text("Only players can join lobbies.", NamedTextColor.RED));
                                                return 1;
                                            }

                                            Locale l = PillarPeril.locale(source);

                                            if (GameManager.game(player, true) != null) {
                                                source.sendMessage(Translation.component(l, "games.lobby.join.in_game").color(NamedTextColor.RED));
                                                return 1;
                                            }

                                            String id = context.getArgument("id", String.class);
                                            Lobby lobby = LobbyManager.lobby(id);
                                            if (lobby == null) {
                                                source.sendMessage(Translation.component(l, "games.lobby.join.not_found").color(NamedTextColor.RED));
                                                return 1;
                                            }

                                            if (LobbyManager.lobby(player) != null) {
                                                source.sendMessage(Translation.component(l, "games.lobby.join.already_in_lobby").color(NamedTextColor.RED));
                                                return 1;
                                            }

                                            Lobby.JoinResult result = lobby.join(player);
                                            Component response = switch (result) {
                                                case SUCCESS ->
                                                        Translation.component(l, "games.lobby.join.success").color(NamedTextColor.GREEN);
                                                case FULL ->
                                                        Translation.component(l, "games.lobby.join.full").color(NamedTextColor.RED);
                                                case DISABLED ->
                                                        Translation.component(l, "games.lobby.join.disabled").color(NamedTextColor.RED);
                                                case IN_GAME ->
                                                        Translation.component(l, "games.lobby.join.lobby_in_game").color(NamedTextColor.RED);
                                                case ALREADY_PRESENT ->
                                                        Translation.component(l, "games.lobby.join.already_in_lobby").color(NamedTextColor.YELLOW);
                                            };
                                            source.sendMessage(response);
                                            return 1;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("leave")
                                .executes(context -> {
                                    CommandSender source = context.getSource().getSender();
                                    if (!(source instanceof Player player)) {
                                        source.sendMessage(Component.text("Only players can leave lobbies.", NamedTextColor.RED));
                                        return 1;
                                    }

                                    Locale l = PillarPeril.locale(source);

                                    Lobby lobby = LobbyManager.lobby(player);
                                    if (lobby == null) {
                                        source.sendMessage(Translation.component(l, "games.lobby.leave.not_in_lobby").color(NamedTextColor.RED));
                                        return 1;
                                    }

                                    lobby.leave(player);
                                    source.sendMessage(Translation.component(l, "games.lobby.leave.success").color(NamedTextColor.YELLOW));
                                    return 1;
                                })
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("force-start")
                                .requires(source -> source.getSender().hasPermission("pillarperil.lobby.force-start"))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("id", StringArgumentType.word())
                                        .executes(context -> {
                                            CommandSender source = context.getSource().getSender();
                                            Locale l = PillarPeril.locale(source);

                                            String id = context.getArgument("id", String.class);
                                            Lobby lobby = LobbyManager.lobby(id);
                                            if (lobby == null) {
                                                source.sendMessage(Translation.component(l, "games.lobby.join.not_found").color(NamedTextColor.RED));
                                                return 1;
                                            }

                                            lobby.forceStart();
                                            source.sendMessage(Translation.component(l, "games.lobby.force_start.success").color(NamedTextColor.GREEN));
                                            return 1;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("cancel")
                                .requires(source -> source.getSender().hasPermission("pillarperil.lobby.cancel"))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("id", StringArgumentType.word())
                                        .executes(context -> {
                                            CommandSender source = context.getSource().getSender();
                                            Locale l = PillarPeril.locale(source);

                                            String id = context.getArgument("id", String.class);
                                            Lobby lobby = LobbyManager.lobby(id);
                                            if (lobby == null) {
                                                source.sendMessage(Translation.component(l, "games.lobby.join.not_found").color(NamedTextColor.RED));
                                                return 1;
                                            }

                                            lobby.cancel();
                                            source.sendMessage(Translation.component(l, "games.lobby.cancel.success").color(NamedTextColor.YELLOW));
                                            return 1;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("makesign")
                                .requires(source -> source.getSender().hasPermission("pillarperil.lobby.makesign"))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("id", StringArgumentType.word())
                                        .executes(context -> {
                                            CommandSender source = context.getSource().getSender();
                                            if (!(source instanceof Player player)) {
                                                source.sendMessage(Component.text("Only players can create lobby signs.", NamedTextColor.RED));
                                                return 1;
                                            }

                                            Locale l = PillarPeril.locale(source);

                                            String id = context.getArgument("id", String.class);
                                            Lobby lobby = LobbyManager.lobby(id);
                                            if (lobby == null) {
                                                source.sendMessage(Translation.component(l, "games.lobby.join.not_found").color(NamedTextColor.RED));
                                                return 1;
                                            }

                                            org.bukkit.block.Block target = player.getTargetBlockExact(5);
                                            if (target == null || !(target.getState() instanceof org.bukkit.block.Sign sign)) {
                                                source.sendMessage(Translation.component(l, "games.lobby.makesign.not_sign").color(NamedTextColor.RED));
                                                return 1;
                                            }

                                            sign.getSide(Side.FRONT).line(0, Component.text("[PillarPeril]", NamedTextColor.AQUA));
                                            sign.getSide(Side.FRONT).line(1, Component.text(id, NamedTextColor.GOLD));
                                            sign.getSide(Side.FRONT).line(2, Component.text("Click to join", NamedTextColor.GREEN));
                                            sign.getSide(Side.FRONT).line(3, Component.text("Players welcome", NamedTextColor.YELLOW));
                                            sign.update();

                                            source.sendMessage(Translation.component(l, "games.lobby.makesign.success").color(NamedTextColor.GREEN));
                                            return 1;
                                        })
                                )
                        )
                )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("set-endspawn")
                        .requires(source -> source.getSender().hasPermission("pillarperil.endspawn.set"))
                        .executes(context -> {
                            CommandSender source = context.getSource().getSender();
                            if (!(source instanceof Player player)) {
                                source.sendMessage(Component.text("Only players can set the end spawn.", NamedTextColor.RED));
                                return 1;
                            }

                            Locale l = PillarPeril.locale(source);
                            Location loc = player.getLocation();

                            PillarPeril.CONFIG.set("end-spawn.world", loc.getWorld().getName());
                            PillarPeril.CONFIG.set("end-spawn.x", loc.getX());
                            PillarPeril.CONFIG.set("end-spawn.y", loc.getY());
                            PillarPeril.CONFIG.set("end-spawn.z", loc.getZ());
                            PillarPeril.CONFIG.set("end-spawn.yaw", loc.getYaw());
                            PillarPeril.CONFIG.set("end-spawn.pitch", loc.getPitch());
                            PillarPeril.PLUGIN.saveConfig();

                            PillarPeril.setEndSpawn(loc);

                            source.sendMessage(Translation.component(l, "games.end-spawn.set").color(NamedTextColor.GREEN));
                            return 1;
                        })
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("set-arena-bounds")
                        .requires(source -> source.getSender().hasPermission("pillarperil.arena.setbounds"))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, World>argument("world", ArgumentTypes.world())
                                .then(RequiredArgumentBuilder.<CommandSourceStack, BlockPositionResolver>argument("min", ArgumentTypes.blockPosition())
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, BlockPositionResolver>argument("max", ArgumentTypes.blockPosition())
                                                .executes(context -> {
                                                    CommandSender source = context.getSource().getSender();
                                                    Locale l = PillarPeril.locale(source);

                                                    World world = context.getArgument("world", World.class);
                                                    BlockPosition minPos = context.getArgument("min", BlockPositionResolver.class).resolve(context.getSource());
                                                    BlockPosition maxPos = context.getArgument("max", BlockPositionResolver.class).resolve(context.getSource());

                                                    Location min = minPos.toLocation(world);
                                                    Location max = maxPos.toLocation(world);

                                                    PillarPeril.setArenaBounds(min, max);

                                                    source.sendMessage(Translation.component(l, "games.arena.set-bounds").color(NamedTextColor.GREEN));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("stop")
                        .requires(source -> source.getSender().hasPermission("pillarperil.stop"))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("game", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    GameManager.GAMES.forEach(game -> builder.suggest(game.id));
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSender source = context.getSource().getSender();
                                    Locale l = PillarPeril.locale(source);

                                    Game game = GameManager.game(context.getArgument("game", String.class));
                                    if (game != null) {
                                        game.end(Game.EndingCause.FORCE, List.of());
                                        source.sendMessage(Translation.component(l, "games.stop.success").color(NamedTextColor.YELLOW));
                                    } else {
                                        source.sendMessage(Translation.component(l, "games.wrong_id").color(NamedTextColor.RED));
                                    }
                                    return 1;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("setspawn")
                        .requires(source -> source.getSender().hasPermission("pillarperil.lobby.setspawn"))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("id", StringArgumentType.word())
                                .executes(context -> {
                                    CommandSender source = context.getSource().getSender();
                                    if (!(source instanceof Player player)) {
                                        source.sendMessage(Component.text("Only players can set lobby spawn.", NamedTextColor.RED));
                                        return 1;
                                    }

                                    Locale l = PillarPeril.locale(source);
                                    String id = context.getArgument("id", String.class);
                                    Lobby lobby = LobbyManager.lobby(id);
                                    if (lobby == null) {
                                        source.sendMessage(Translation.component(l, "games.lobby.join.not_found").color(NamedTextColor.RED));
                                        return 1;
                                    }

                                    lobby.setLobbySpawn(player.getLocation());
                                    source.sendMessage(Translation.component(l, "games.lobby.setspawn.success").color(NamedTextColor.GREEN));
                                    return 1;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("setwaiting")
                        .requires(source -> source.getSender().hasPermission("pillarperil.lobby.setwaiting"))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("id", StringArgumentType.word())
                                .executes(context -> {
                                    CommandSender source = context.getSource().getSender();
                                    if (!(source instanceof Player player)) {
                                        source.sendMessage(Component.text("Only players can set waiting spawns.", NamedTextColor.RED));
                                        return 1;
                                    }

                                    Locale l = PillarPeril.locale(source);

                                    String id = context.getArgument("id", String.class);
                                    Lobby lobby = LobbyManager.lobby(id);
                                    if (lobby == null) {
                                        source.sendMessage(Translation.component(l, "games.lobby.join.not_found").color(NamedTextColor.RED));
                                        return 1;
                                    }

                                    lobby.setWaitingSpawn(player.getLocation());
                                    source.sendMessage(Translation.component(l, "games.lobby.setwaiting.success").color(NamedTextColor.GREEN));
                                    return 1;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("leaderboard")
                        .requires(source -> source.getSender().hasPermission("pillarperil.leaderboard"))
                        .executes(context -> {
                            CommandSender source = context.getSource().getSender();

                            source.sendMessage(SEPARATOR_20);
                            source.sendMessage(Component.text("Top Wins:", NamedTextColor.GOLD));
                            int index = 1;
                            for (com.marcpg.pillarperil.game.util.StatsManager.PlayerStats stats : com.marcpg.pillarperil.game.util.StatsManager.topWins(10)) {
                                source.sendMessage(Component.text(index + ". " + stats.name() + " - " + stats.wins() + " wins", NamedTextColor.YELLOW));
                                index++;
                            }

                            source.sendMessage(SEPARATOR_20);
                            source.sendMessage(Component.text("Top Kills:", NamedTextColor.RED));
                            index = 1;
                            for (com.marcpg.pillarperil.game.util.StatsManager.PlayerStats stats : com.marcpg.pillarperil.game.util.StatsManager.topKills(10)) {
                                source.sendMessage(Component.text(index + ". " + stats.name() + " - " + stats.kills() + " kills", NamedTextColor.DARK_RED));
                                index++;
                            }
                            source.sendMessage(SEPARATOR_20);
                            return 1;
                        })
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("info")
                        .requires(source -> source.getSender().hasPermission("pillarperil.info"))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("game", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    GameManager.GAMES.forEach(game -> builder.suggest(game.id));
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSender source = context.getSource().getSender();
                                    Locale l = PillarPeril.locale(source);

                                    Game game = GameManager.game(context.getArgument("game", String.class));
                                    if (game != null) {
                                        source.sendMessage(SEPARATOR_20);
                                        source.sendMessage(Component.text("id: " + game.id));
                                        source.sendMessage(Component.text("world: " + game.world.getName()));
                                        source.sendMessage(Component.text("center: " + game.center));
                                        source.sendMessage(SEPARATOR_20);
                                        source.sendMessage(Component.text("timeLeft: " + game.timeLeft().getPreciselyFormatted()));
                                        source.sendMessage(Component.text("itemCooldown: " + game.itemCooldown()));
                                        source.sendMessage(SEPARATOR_20);
                                        source.sendMessage(Component.text("mode.name: " + game.info().name(l)));
                                        source.sendMessage(Component.text("mode.color: ").append(Component.text(game.info().accentColor().asHexString(), game.info().accentColor())));
                                        source.sendMessage(Component.text("mode.generator: " + game.info().generator()));
                                        source.sendMessage(Component.text("mode.itemCooldown: " + game.info().itemCooldown()));
                                        source.sendMessage(SEPARATOR_20);
                                        source.sendMessage(Component.text("players:"));
                                        for (PillarPlayer p : game.initialPlayers()) {
                                            source.sendMessage(Component.text("| ").append(Component.text(p.player.getName(), game.players().contains(p) ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
                                        }
                                        source.sendMessage(SEPARATOR_20);
                                    } else {
                                        source.sendMessage(Translation.component(l, "games.wrong_id").color(NamedTextColor.RED));
                                    }
                                    return 1;
                                })
                        )
                )
                .build();
    }
}
