# Lobby Enhancements – Persistent Lobbies & Hotbar GUI

This document outlines a step-by-step plan to:

1. Make **lobbies persistent** (live even between games).
2. Add a **hard-coded hotbar GUI** for lobby interaction.

All tasks are expressed as checklists so you can track progress.

---

## 1. Persistent Lobbies (Live Between Games)

Goal: A lobby is a long-lived “room” that players can join/leave at any time. Games are transient matches started from a lobby; when a match ends, players return to their lobby instead of being dumped to a generic end spawn (unless they didn’t come from a lobby).

### 1.1. Lobby Lifecycle & State Machine

- [x] Redesign `Lobby` to explicitly track its state:
  - [x] Add enum `LobbyState { WAITING, COUNTDOWN, IN_GAME, DISABLED }`.
  - [x] Add `LobbyState state` field (default `WAITING`).
- [x] Update lobby flow:
  - [x] `WAITING`: players can join/leave; no game running; countdown not active.
  - [x] `COUNTDOWN`: enough players; countdown running; additional joins allowed; leaves may cancel countdown if below `minPlayers`.
  - [x] `IN_GAME`: a game started from this lobby is currently running; players in that game are considered “locked in” until end.
  - [x] `DISABLED`: lobby exists but cannot start new games (admin-only state).
- [x] Ensure a single lobby can host many game sessions over its lifetime:
  - [x] When a game starts, mark lobby as `IN_GAME` and remember the current participants.
  - [x] When the game ends, mark lobby back to `WAITING` and allow new countdowns/games.

### 1.2. Decouple Lobby From Single Game Instance

- [x] In `Lobby`, stop treating game as one-off:
  - [x] Remove implicit assumption that `startGame()` destroys the lobby; instead:
    - [x] Store spawning game ID or reference: `@Nullable Game currentGame`.
    - [x] Only clear `currentGame` and revert to `WAITING` when that game ends.
- [x] Modify `Lobby.startGame()`:
  - [x] Create a new `Game` instance as now.
  - [x] Store it in `currentGame`.
  - [x] Set `state = IN_GAME`.
  - [x] Optionally keep a `List<PillarPlayer>` of participants for stats / post-game UI.
- [x] On game end:
  - [x] Add callback or hook (e.g. from `Game.cleanup()` or a game listener) to:
    - [x] Detect if the game was created by a lobby (`Game` can track `Lobby` ID or reference).
    - [x] Teleport players back to the lobby center instead of global end spawn.
    - [x] Clear `currentGame` and set lobby back to `WAITING`.

### 1.3. Track Player Origin (Lobby vs Direct Start)

- [x] Extend `PillarPlayer` or `Game` to know if a player came from a lobby:
  - [x] Add field `@Nullable Lobby originLobby` in `Game`, expose via getter, and copy to `PillarPlayer`.
- [x] Adjust `PillarPlayer.clean()` behavior:
  - [x] If `originLobby != null`:
    - [x] Teleport to `originLobby.lobbySpawn()` instead of global end spawn.
  - [x] Otherwise:
    - [x] Keep current behavior: teleport to `PillarPeril.endSpawn(world)`.
- [x] Update game-ending flow so that lobby-originating players flow back to their lobby while direct-start players still go to end spawn.

### 1.4. Lobby Spawn Location

- [x] Make **per-lobby spawn** mandatory:
  - [x] Field `Location lobbySpawn` in `Lobby` (defaults to center on creation).
  - [x] Constructor enforces a valid spawn (derived from center); loading path supports explicit ID/spawn.
- [x] Enforce a **min/max player capacity** per lobby:
  - [x] Keep `int minPlayers; int maxPlayers;` fields.
  - [x] Validate on creation that `minPlayers > 0` and `minPlayers <= maxPlayers`.
  - [x] In `Lobby.join(Player)`, refuse join when `players.size() >= maxPlayers` with a clear message (currently silently ignores).
- [x] Add admin command to set lobby spawn:
  - [x] `/games lobby setspawn <id>` with `pillarperil.lobby.setspawn`.
  - [x] Uses executor’s current location as `lobbySpawn` for that lobby.
- [x] On lobby join:
  - [x] Teleport players to `lobbySpawn` instead of leaving them where they stand.
- [x] On game end (for lobby players):
  - [x] Teleport them back to `lobbySpawn`.

### 1.5. Persistent Lobby Storage (Optional)

If you want lobbies to survive server restarts:

- [x] Design a simple storage format:
  - [x] YAML: `lobbies.yml` with ID, mode key, center, lobbySpawn, min/max players.
- [x] On `onDisable()`:
  - [x] Save all `LobbyManager.LOBBIES` to disk (excluding transient runtime-only fields like `state`, `currentGame`, in-memory players).
- [x] On `onEnable()`:
  - [x] Load lobbies from `lobbies.yml` and reconstruct `Lobby` instances (empty player lists; `state = WAITING`).

---

## 2. Hotbar GUI (Hard-Coded)

Goal: When players are in a lobby, give them a fixed set of items in their hotbar that act as a simple GUI (via right-click), without yet requiring a custom inventory GUI.

### 2.1. Hotbar Layout

Define a simple, hard-coded layout for lobby players:

- [x] Choose items and slots:
  - [x] Slot 4: `EMERALD` named “Start Game” (for players with permission).
  - [x] Slot 8: `RED_DYE` named “Leave Lobby”.

### 2.2. Giving Hotbar Items In Lobbies

- [x] Extend `Lobby.join(Player)`:
  - [x] After adding the player to `players`, give them the hotbar items if:
    - [x] They’re not already in a game.
    - [x] They’re in `WAITING` or `COUNTDOWN` state.
  - [x] Ensure you don’t permanently overwrite their “real” inventory:
    - [x] `PillarPlayer` already snapshots and restores inventory around games; for lobbies:
      - [x] Save & restore inventory when entering/leaving a lobby (if you want full persistence).
- [x] On `Lobby.leave(...)`:
  - [x] Remove any lobby hotbar items (or restore previous inventory if you snapshot it for lobbies).
  - [x] Reset active countdown timers back to full duration whenever a new player joins mid-countdown, so late arrivals get the full timer.

### 2.3. Handling Hotbar Interactions

- [x] Add a `LobbyHotbarListener`:
  - [x] Listen for `PlayerInteractEvent`.
  - [x] If the player is in a lobby (via `LobbyManager.lobby(player)`):
    - [x] Check the item in hand and its display name.
    - [x] Handle actions:
      - [x] “Start Game” (EMERALD, slot 4):
        - [x] Only if player has `pillarperil.lobby.start` or similar.
        - [x] Trigger immediate game start for that lobby (ignore countdown) using its configured mode.
      - [x] “Join Queue” (LIME_DYE):
        - [x] Mark player as “ready” (e.g., add to a `Set<UUID> readyPlayers` on the lobby).
        - [x] Optionally start countdown when enough players are ready.
      - [x] “Leave Lobby” (RED_DYE):
        - [x] Call `lobby.leave(player)` and teleport them to a safe location (e.g., end spawn).
  - [x] Cancel the default interaction so players don’t place/destroy blocks with GUI items.

### 2.4. Visual Feedback & States

- [x] Update item metadata based on state:
  - [x] Change “Join Queue” item to “Ready ✓” when player is ready.
      - [x] Use lore to show:
        - [x] Number of ready players.
        - [x] Required players to start.
- [x] When countdown is running:
  - [x] Update “Start Game” or “Join Queue” item lore to show time remaining.

### 2.5. Integration With Existing Scoreboards

- [ ] Ensure hotbar GUI and lobby scoreboard complement each other:
  - [x] Lobby scoreboard currently shows mode, player counts, and countdown.
  - [x] Ensure lobby scoreboard updates when players ready/unready via hotbar actions.
- [x] Ensure game scoreboard (`pp`) is removed and lobby scoreboard is applied when players return to a lobby after a game.

---

## 3. Validation Checklist After Implementation

Once the above steps are implemented, validate:

- [ ] Creating a lobby, starting multiple games from it, and having it remain usable between games.
- [ ] Players who join a lobby via commands or signs receive the hotbar GUI items.
- [ ] Hotbar actions start games, queue/ready players, and allow leaving lobbies as expected.
- [ ] Arena regeneration still works correctly with persistent lobbies.
- [ ] Inventory/XP restore still behaves correctly when games are started via lobbies with the new flow.
