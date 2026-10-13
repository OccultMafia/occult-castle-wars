# Changelog

All notable changes to Occult Castle Wars are documented here in reverse chronological order.

---

## [a0f075e] — Skip banking between rounds when inventory is clean

**Date:** 2026-10-14

### What changed
- `Main.java`: Removed the `inventoryCleared` boolean field entirely
- `Main.java`: `detectState()` — replaced `if (!inventoryCleared)` with `if (hasNonTicketItems())` as the gate for the `BANK_AND_CLEAR` state
- `Main.java`: `onStart()` — removed `inventoryCleared = false` reset
- `Main.java`: `doBank()` — removed `inventoryCleared = true` assignment on bank close

### Why
The old `inventoryCleared` flag was a one-shot boolean that started `false` on every script launch. If the script was started while the player was already inside a game (e.g. on the castle roof), the flag was never set to `true` during that game — causing the script to route through `BANK_AND_CLEAR` after every game completion even when the inventory was perfectly clean (only CW tickets present). This meant an unnecessary bank open/close trip before every portal entry.

Replacing the flag with a live `hasNonTicketItems()` check means the decision is made from actual inventory state each tick, not from stale launch-time state. Banking now only occurs when there is genuinely something to deposit.

---

## [4b7f3b1] — Add README

**Date:** 2026-10-14

### What changed
- `README.md`: Created initial project README

### Why
Script was published to GitHub for the Pure Plugin community. A README was needed to explain installation, configuration, how the loop works, and what the overlay displays.

---

## [418ecba] — Initial release

**Date:** 2026-10-14

### What changed
- `Main.java`: Full Castle Wars ticket farming script
- `CastleWarsConfig.java`: Single config toggle — Members Account (world 354 vs 383)
- `StatsOverlay.java`: Live HUD overlay with GAMES PLAYED, TOTAL TICKETS, TICKETS /hr, and a Force Update button
- `MessageOverlay.java`: One-time warning popup shown on startup when the Break Handler is off
- `AGENTS.md` / `CLAUDE.md`: AI agent instructions for working on this codebase
- `.gitignore`: Excludes IntelliJ project files, build output, API stubs, and Claude memory

### Core loop
Teleport to Castle Wars (Ring of Dueling, walker fallback) → bank gear and consolidate tickets → hop to target world → enter Guthix portal → AFK in waiting room → climb to castle roof → AFK on roof → repeat.

### Ticket tracking design
`initialCwTickets` is captured from inventory on script start and incremented whenever tickets are withdrawn from the bank during a banking step. This ensures tickets the player already owned (including banked ones) are never counted as session earnings. `sessionTickets = inventoryCount − initialCwTickets` gives only game-earned tickets, used for the TICKETS /hr rate. TOTAL TICKETS displayed = `initialCwTickets + sessionTickets` (full ownership count).

### Other details
- Auto-retaliate is disabled on start
- Break handler is blocked during `WAIT_FOR_GAME`, `CLIMB_TO_ROOF`, and `IDLE_ON_ROOF` so breaks only fire between games
- Climb delay of 3–5 random ticks after respawn before attempting to climb back to the roof
- Walker guard (`isWalking() || isMoving()`) prevents spawning overlapping walker threads when picking AFK tiles
- Waiting room detection checks instanced coords (y ≈ 9482 / 9514) before the overworld region check, preventing false TELEPORT_TO_CW triggers while inside a game
