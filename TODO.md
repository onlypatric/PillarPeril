# TODO

- [ ] General polish and cleanup

## Pre-Production Verification (Paper Server)

- [ ] Run plugin on a real Paper server matching your target MC version.
- [ ] Verify plugin loads cleanly (no stack traces on startup).

### Commands & Permissions

- [ ] `/games start force original <center> <world> <players>`:
  - [ ] Starts a game and teleports players to pillars correctly.
  - [ ] Fails with `games.start.player_in_game` if any target player is already in a game.
  - [ ] Shows `games.start.invalid_mode` for unknown mode keys.
- [ ] `/games stop <id>`:
  - [ ] Ends the specified game, teleports players to end spawn, and regenerates the arena.
- [ ] `/games info <id>`:
  - [ ] Displays correct world, center, time left, item cooldown, mode info, and player list.
- [ ] `/games leaderboard`:
  - [ ] Shows “Top Wins” and “Top Kills” based on actual play, respects `pillarperil.leaderboard` permission.
- [ ] `/games lobby create/join/leave/force-start/cancel`:
  - [ ] Lobbies can be created and joined by players.
  - [ ] Joining is blocked if player is already in a game (`games.lobby.join.in_game`).
  - [ ] Force-start converts lobby to a game and removes the lobby from `/games lobby` list.
  - [ ] Cancel removes the lobby and clears its UI.
- [ ] `/games lobby makesign <id>`:
  - [ ] Writes `[PillarPeril]` and lobby ID to signs correctly.
  - [ ] Right-clicking the sign joins the lobby (unless already in game or lobby).

### Gameplay & Arena

- [ ] Start games with each mode (`original`, `blocky`, `cubecraft`, `chaos`, `items-only`) and:
  - [ ] Verify loot feels correct for each mode.
  - [ ] Pillars/platforms generate in expected patterns and distances.
- [ ] During games:
  - [ ] Place and break blocks; ensure everything restores after game end.
  - [ ] Place and spread water/lava; confirm fluids are fully cleaned up.
  - [ ] Confirm time limit expiry ends the game properly and regenerates arena.
  - [ ] Confirm simultaneous death of last two players triggers DRAW and regen.

### Player State & UX

- [ ] Before starting a game, give players custom inventory and XP:
  - [ ] After game completion, verify their original inventory and XP are restored.
- [ ] Verify scoreboard:
  - [ ] In-game scoreboard (mode, name, time, kills) appears and updates.
  - [ ] Lobby scoreboard shows mode, player counts, and countdown.
  - [ ] Scoreboard resets after leaving/ending games or lobbies.
- [ ] Check end spawn behavior:
  - [ ] With a configured `end-spawn` in `config.yml`, players are teleported there after games.
  - [ ] With an invalid/missing world, plugin falls back gracefully to a sensible spawn without errors.

### Translations & Networking

- [ ] With network access:
  - [ ] Confirm translations load from `https://marcpg.com/pillar-peril/lang/all` and non-English locales show translated messages.
- [ ] With network blocked or endpoint down:
  - [ ] Confirm plugin falls back to bundled `en_US.properties` without crashing and logs a clear warning.

### Multi-Game & Load

- [ ] Run multiple games concurrently in the same and different worlds:
  - [ ] Ensure arenas and regen are isolated per game.
  - [ ] Confirm no cross-contamination of blocks or player state.
- [ ] Run with many players and multiple games:
  - [ ] Watch TPS and logs for any performance issues or repeated warnings.

