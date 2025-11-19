# Lobby Enhancements – Persistent Lobbies & Hotbar GUI

This document outlines a step-by-step plan to:

1. Make **lobbies persistent** (live even between games).
2. Add a **hard-coded hotbar GUI** for lobby interaction.

All tasks are expressed as checklists so you can track progress.

---

## 1. Persistent Lobbies (Live Between Games)

Goal: A lobby is a long-lived “room” that players can join/leave at any time. Games are transient matches started from a lobby; when a match ends, players return to their lobby instead of being dumped to a generic end spawn (unless they didn’t come from a lobby).

### 1.1. Lobby Lifecycle & State Machine

- [ ] Redesign `Lobby` to explicitly track its state:
  - [ ] Add enum `LobbyState { WAITING, COUNTDOWN, IN_GAME, DISABLED }`.
  - [ ] Add `LobbyState state` field (default `WAITING`).
- [ ] Update lobby flow:
  - [ ] `WAITING`: players can join/leave; no game running; countdown not active.
  - [ ] `COUNTDOWN`: enough players; countdown running; additional joins allowed; leaves may cancel countdown if below `minPlayers`.
  - [ ] `IN_GAME`: a game started from this lobby is currently running; players in that game are considered “locked in” until end.
  - [ ] `DISABLED`: lobby exists but cannot start new games (admin-only state).
- [ ] Ensure a single lobby can host many game sessions over its lifetime:
  - [ ] When a game starts, mark lobby as `IN_GAME` and remember the current participants.
  - [ ] When the game ends, mark lobby back to `WAITING` and allow new countdowns/games.

### 1.2. Decouple Lobby From Single Game Instance

- [ ] In `Lobby`, stop treating game as one-off:
  - [ ] Remove implicit assumption that `startGame()` destroys the lobby; instead:
    - [ ] Store spawning game ID or reference: `@Nullable Game currentGame`.
    - [ ] Only clear `currentGame` and revert to `WAITING` when that game ends.
- [ ] Modify `Lobby.startGame()`:
  - [ ] Create a new `Game` instance as now.
  - [ ] Store it in `currentGame`.
  - [ ] Set `state = IN_GAME`.
  - [ ] Optionally keep a `List<PillarPlayer>` of participants for stats / post-game UI.
- [ ] On game end:
  - [ ] Add callback or hook (e.g. from `Game.cleanup()` or a game listener) to:
    - [ ] Detect if the game was created by a lobby (`Game` can track `Lobby` ID or reference).
    - [ ] Teleport players back to the lobby center instead of global end spawn.
    - [ ] Clear `currentGame` and set lobby back to `WAITING`.

### 1.3. Track Player Origin (Lobby vs Direct Start)

- [ ] Extend `PillarPlayer` or `Game` to know if a player came from a lobby:
  - [ ] Add field `@Nullable Lobby originLobby` in `PillarPlayer` or store lobby ID in `Game`.
- [ ] Adjust `PillarPlayer.clean()` behavior:
  - [ ] If `originLobby != null`:
    - [ ] Teleport to `originLobby.center()` (or a lobby spawn location, see below) instead of global end spawn.
  - [ ] Otherwise:
    - [x] Keep current behavior: teleport to `PillarPeril.endSpawn(world)`.
- [ ] Update game-ending flow so that lobby-originating players flow back to their lobby while direct-start players still go to end spawn.

### 1.4. Lobby Spawn Location

- [ ] Make **per-lobby spawn** mandatory:
  - [ ] Field `Location lobbySpawn` in `Lobby` (must be set when lobby is created).
  - [ ] `Lobby` constructors should require a `Location lobbySpawn` (or derive it from the initial `center`) so every lobby always has a valid teleport target.
- [ ] Enforce a **min/max player capacity** per lobby:
  - [x] Keep `int minPlayers; int maxPlayers;` fields.
  - [ ] Validate on creation that `minPlayers > 0` and `minPlayers <= maxPlayers`.
  - [ ] In `Lobby.join(Player)`, refuse join when `players.size() >= maxPlayers` with a clear message.
- [ ] Add admin command to set lobby spawn:
  - [ ] `/games lobby setspawn <id>`:
    - [ ] Permission `pillarperil.lobby.setspawn`.
    - [ ] Uses executor’s current location as `lobbySpawn` for that lobby.
- [ ] On lobby join:
  - [ ] Teleport players to `lobbySpawn` instead of leaving them where they stand.
- [ ] On game end (for lobby players):
  - [ ] Teleport them back to `lobbySpawn`.

### 1.5. Persistent Lobby Storage (Optional)

If you want lobbies to survive server restarts:

- [ ] Design a simple storage format:
  - [ ] YAML: `lobbies.yml` with ID, mode key, center, lobbySpawn, min/max players.
- [ ] On `onDisable()`:
  - [ ] Save all `LobbyManager.LOBBIES` to disk (excluding transient runtime-only fields like `state`, `currentGame`, in-memory players).
- [ ] On `onEnable()`:
  - [ ] Load lobbies from `lobbies.yml` and reconstruct `Lobby` instances (empty player lists; `state = WAITING`).

---

## 2. Hotbar GUI (Hard-Coded)

Goal: When players are in a lobby, give them a fixed set of items in their hotbar that act as a simple GUI (via right-click), without yet requiring a custom inventory GUI.

### 2.1. Hotbar Layout

Define a simple, hard-coded layout for lobby players:

- [ ] Choose items and slots:
  - [ ] Slot 0: `COMPASS` named “Select Mode” (future expansion).
  - [x] Slot 4: `EMERALD` named “Start Game” (for players with permission).
  - [ ] Slot 7: `LIME_DYE` named “Join Queue” (or “Ready Up”).
  - [ ] Slot 8: `RED_DYE` named “Leave Lobby”.

### 2.2. Giving Hotbar Items In Lobbies

- [ ] Extend `Lobby.join(Player)`:
  - [ ] After adding the player to `players`, give them the hotbar items if:
    - [ ] They’re not already in a game.
    - [ ] They’re in `WAITING` or `COUNTDOWN` state.
  - [ ] Ensure you don’t permanently overwrite their “real” inventory:
    - [x] `PillarPlayer` already snapshots and restores inventory around games; for lobbies:
      - [ ] Either treat lobby hotbar items as “visual only” on a separate scoreboard, or
      - [ ] Save & restore inventory when entering/leaving a lobby (if you want full persistence).
- [ ] On `Lobby.leave(...)`:
  - [ ] Remove any lobby hotbar items (or restore previous inventory if you snapshot it for lobbies).

### 2.3. Handling Hotbar Interactions

- [ ] Add a `LobbyHotbarListener`:
  - [ ] Listen for `PlayerInteractEvent`.
  - [ ] If the player is in a lobby (via `LobbyManager.lobby(player)`):
    - [ ] Check the item in hand and its display name.
    - [ ] Handle actions:
      - [ ] “Start Game” (EMERALD, slot 4):
        - [ ] Only if player has `pillarperil.lobby.start` or similar.
        - [ ] Trigger immediate game start for that lobby (ignore countdown) using its configured mode.
      - [ ] “Join Queue” (LIME_DYE):
        - [ ] Mark player as “ready” (e.g., add to a `Set<UUID> readyPlayers` on the lobby).
        - [ ] Optionally start countdown when enough players are ready.
      - [ ] “Leave Lobby” (RED_DYE):
        - [ ] Call `lobby.leave(player)` and teleport them to a safe location (e.g., end spawn).
  - [ ] Cancel the default interaction so players don’t place/destroy blocks with GUI items.

### 2.4. Visual Feedback & States

- [ ] Update item metadata based on state:
  - [ ] Change “Join Queue” item to “Ready ✓” when player is ready.
  - [ ] Use lore to show:
    - [ ] Number of ready players.
    - [ ] Required players to start.
- [ ] When countdown is running:
  - [ ] Update “Start Game” or “Join Queue” item lore to show time remaining.
  - [ ] Consider briefly giving a “Cancel Countdown” item to admins.

### 2.5. Integration With Existing Scoreboards

- [ ] Ensure hotbar GUI and lobby scoreboard complement each other:
  - [x] Lobby scoreboard currently shows mode, player counts, and countdown.
  - [ ] Ensure lobby scoreboard updates when players ready/unready via hotbar actions.
  - [ ] Ensure game scoreboard (`pp`) is removed and lobby scoreboard is applied when players return to a lobby after a game.

---

## 3. Validation Checklist After Implementation

Once the above steps are implemented, validate:

- [ ] Creating a lobby, starting multiple games from it, and having it remain usable between games.
- [ ] Players who join a lobby via commands or signs receive the hotbar GUI items.
- [ ] Hotbar actions start games, queue/ready players, and allow leaving lobbies as expected.
- [ ] Arena regeneration still works correctly with persistent lobbies.
- [ ] Inventory/XP restore still behaves correctly when games are started via lobbies with the new flow.
