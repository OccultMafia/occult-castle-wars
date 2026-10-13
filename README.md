# Occult Castle Wars

A [PureApi Scripter](https://runelite.net/plugin-hub/show/pureapi) script for RuneLite that farms Castle Wars participation tickets fully automatically.

The script enters the Guthix portal, waits in the lobby, climbs to the castle roof, and AFKs through each game. It loops indefinitely, banking between games to deposit equipment and consolidate tickets.

---

## Requirements

- RuneLite with the **PureApi Scripter** plugin installed
- A **Ring of Dueling** in your inventory or equipment (for the initial teleport to Castle Wars — the script falls back to walking if none is available)
- No other items needed — the script banks everything at the start

---

## Installation

1. Open the Scripter plugin in RuneLite and click **Import from GitHub**
2. Enter: `OccultMafia/occult-castle-wars`
3. The script will appear in your script list as **Occult Castle Wars**

Alternatively, clone or download this repository into your Scripter scripts folder and it will be picked up automatically.

---

## Configuration

| Setting | Description | Default |
|---|---|---|
| Members Account | Hops to world **354** (members) when enabled, **383** (F2P) when disabled | Enabled |

---

## How it works

1. **Teleport** — uses a Ring of Dueling to teleport to Castle Wars; falls back to walking if none is equipped or in inventory
2. **Bank** — deposits all inventory and worn equipment; withdraws any Castle Wars tickets already in the bank so the count stays accurate
3. **Hop world** — hops to the configured target world (354 members / 383 F2P)
4. **Enter portal** — interacts with the Guthix portal (neutral team, no side preference)
5. **Wait for game** — idles on a random tile in the waiting room until the game starts
6. **Climb to roof** — after spawning inside the castle, waits a few ticks then climbs the ladder to the roof
7. **AFK on roof** — walks to a random tile on the castle battlements and stays there until the game ends
8. **Repeat** — when the game ends the player is teleported back to the lobby and the loop restarts from step 2

If the player is killed during a game, the script detects the respawn (plane 1 inside the castle), waits 3–5 ticks, then climbs back up to the roof.

---

## Live stats overlay

While running, an overlay in the top-left corner displays:

- **GAMES PLAYED** — number of complete games this session
- **TOTAL TICKETS** — your current total Castle Wars ticket count (includes tickets you owned before starting)
- **TICKETS /hr** — average tickets earned per hour this session (excludes pre-existing tickets)

A **Force Update** button manually re-reads inventory state and prints a debug snapshot to the Scripter output log, useful for verifying ticket counts mid-session.

---

## Break handler

The script is compatible with the Scripter's built-in break handler. Breaks are blocked during active gameplay (waiting room, climbing, and idling on the roof) so a break never interrupts a game in progress — it will only trigger between games while the script is in the lobby.

A one-time warning overlay is shown on startup if the break handler is turned off.

---

## Notes

- Auto-retaliate is automatically disabled on script start
- The script targets the Guthix (neutral) portal — it does not pick a side and will be assigned randomly each game
- Ticket counts are accurate across restarts: tickets already in your inventory or bank before starting the script are recorded as a baseline and not counted as session earnings
- This script is sandboxed — it does not use files, the network, or any restricted APIs, and can run without being marked Trusted

---

## Disclaimer

Use at your own risk. Botting in any form is against the Old School RuneScape rules and can result in account bans. This script is provided for educational purposes.
