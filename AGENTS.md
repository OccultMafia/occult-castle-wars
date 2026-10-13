<!-- pureapi-agents d1ff50f33881190bafc345d05bbdcc6954ca6ea0a57013b9bb691f221f1a21f2 -->
# AGENTS.md

Notes for AI coding agents working on this script.

## What this is

This folder is a PureApi **Scripter** script for the RuneLite (Old School RuneScape) client. The
Scripter plugin compiles every `.java` file in this folder (and its subfolders, for packages) in
memory and runs the result inside the client. There is no build file: don't add Gradle or Maven
files, and don't try to build or run the script outside the client.

- The main class is the one non-abstract subclass of `org.pureapps.api.scripts.Script`. With more
  than one, `@ScriptManifest` picks it.
- Every other file in the folder is a resource. Read it with `openResource("path")` (an
  `InputStream`, or null). It also works when the script is imported from GitHub, which the
  Scripter keeps in memory only: such a script has no folder, so `resource("path")` (a `File`)
  only works for local scripts, and only when the script is trusted (see Sandbox).
- A script can also live in a source folder linked into the Scripter (its Folders button), e.g. a
  project's `src/main/java` with several scripts. Each non-abstract `Script` subclass there is its
  own script, compiled from its package root with the shared code but not the other scripts'
  main files and folders. Resources are also found in the sibling `src/main/resources`.
- `.idea/`, `out/` and `*.iml` are IntelliJ files written by the Scripter's IntelliJ button. Leave
  them alone; they are not compiled.
- With "Hot Reload" on, saving any file recompiles the script and restarts it. A failed compile
  keeps the old version running, and the errors show in the Scripter output.
- The PureApi and RuneLite sources are not in this folder. Look up the PureApi API instead of
  guessing it: in the Scripter's AI chat use the `search_api` and `read_api` tools; as a CLI agent
  read the Javadoc HTML in `~/.runelite/pureapi/javadoc` (start at `allclasses-index.html`, or
  grep `member-search-index.js`; a class's page is `org/pureapps/api/Bank.html` and so on). Open
  the folder in IntelliJ (the Scripter's IntelliJ button) for completion. RuneLite's own classes
  are documented at https://static.runelite.net/runelite-api/apidocs/.

## Script lifecycle

```java
@ScriptManifest(name = "My Script", version = "1.0", description = "...")
public class Main extends Script {
    @Override public void onStart() { }   // once, before the first onLoop
    @Override public void onLoop() { }    // once per game tick (600 ms)
    @Override public void onStop() { }    // once, when the script stops for any reason
}
```

- `onStart`, `onLoop`, `onStop` and `@Subscribe` methods run on the **client thread**. Never sleep,
  wait or block in them: it freezes the game. Do one step per tick and keep state in fields.
- `onLoop` is ticked by PureApi's `StateManager`, so the break handler pauses it.
- Any exception thrown from the script stops it.
- Fields set for you: `api` (the PureApi facade), `client` (RuneLite `Client`), `clientThread`.
- `@Inject` fields and `@Subscribe` methods (RuneLite events from `net.runelite.api.events`) work
  like in a RuneLite plugin.
- Helpers on `Script`: `status(text)` (the overlay's state line), `log(format, args...)` (SLF4J
  style `{}` placeholders, goes to the Scripter output and run log), `stop()`,
  `addOverlay(overlay)` (removed on stop), `openResource(path)`, `resource(path)` (local, trusted
  scripts only).

## Sandbox

Every script runs sandboxed until the user marks it **Trusted** on its Scripter page. Before a
sandboxed script loads, the Scripter checks its bytecode against an allowlist and refuses to start
it if it uses anything else; the output lists each use as `File.java:line: <class or member> is
not allowed in sandboxed scripts`. Write scripts that pass the sandbox:

- Allowed: `java.lang` basics (`String`, `Math`, boxed types, exceptions, `StringBuilder`),
  `java.util` collections, `Optional`, `Random`, `java.util.function`, `java.util.stream`,
  `java.util.regex`, `java.time`, `java.text`, `java.math`, drawing classes from `java.awt`
  (`Graphics2D`, `Color`, `Font`, `Rectangle`, `Point`, `Polygon`, `BasicStroke`, `java.awt.geom`),
  most of `net.runelite.**` (the API, overlays, infoboxes, `ItemManager`, `ItemStats`, `SpriteManager`,
  `NPCManager`, `ChatMessageManager`, `Notifier`, `client.util`, ...), PureApi's `@ShowIf`, `@Divider`,
  `@StartButton`, `@ChoiceList`, `@Inject`, and the PureApi facades on `api`.
- Not allowed: files (`java.io.File`, `java.nio.file`, `resource(path)`: use `openResource(path)`),
  the network, threads and executors (so not `api.waits.waitUntil`), reflection
  (`Class.forName`, `getClassLoader`, `java.lang.reflect`), `System.getProperty`/`getenv`/`exit`,
  `api.config` (use the script's own `@ConfigGroup` interface), Swing and `org.pureapps.api.ui`,
  `ConfigManager`, `EventBus`, `KeyManager`, RuneLite's plugins, web clients (`*Client` classes),
  party and Discord, Swing (`client.ui` frames and components), and the client's hooks, canvas and socket.
- Lambdas, method references, records, enums, `switch` patterns and string concatenation work.
- When a script truly needs something the sandbox refuses, say so to the user: only they can
  trust the script, and they should read its code first.

## Settings

A script can have settings: one public interface in this folder that extends
`net.runelite.client.config.Config` and has `@ConfigGroup("unique-group")`, with `@ConfigItem`
methods like a RuneLite plugin config. Get it with an `@Inject` field of that type. The Scripter
page shows it, and changes apply to a running script at once.
`@ShowIf(key = "otherKey", value = "...")` (`org.pureapps.api.ui.plugins`) on an item or
`@ConfigSection` field shows it only while another item has one of the given raw values,
`@Divider("Label")` draws a divider above an item, and `@StartButton` draws a boolean item as a
Start/Stop button. `@ChoiceList(MyEnum.class)` on a `String` item draws an ordered pick list of the
enum's constants (switches to pick, drag to reorder); the value is the picked `name()`s in order,
comma-separated. An enum implementing `ChoiceList.Status` (`color()`, e.g. `Theme.PRIMARY`) has
each name drawn in its own colour; returning false from its `selectable()` lists that constant last,
switched off (disabled, and dropped from the saved value). They work in sandboxed scripts too.

## PureApi quick reference

Use the facades on `api`. Interaction methods return `true` when the action was sent; check the
game state on a later tick to see if it worked.

- `api.player`: `get()`, `getWorldLocation()`, `isMoving()`, `isAnimating()`, `isInteracting()`, `inCutscene()`.
- `api.npcs`: `search()` (an `NpcQuery`), `interact(name, action)`, `attack(name)`, `talk(name)`.
- `api.objects`: `search()`, `searchAll()` (every kind of tile object), `interact(name, action)`,
  `interact(tileObject[, action])`. Doors are often walls: also try `api.walls`.
- `api.groundItems`, `api.players`, `api.widgets`: search and interact the same way.
- `api.inventory`: `contains`, `count`, `quantity`, `isFull`, `emptySlots`, `interact(name, action)`,
  `eat`, `drink` (also by `Potion`), `equip`, `drop`, `dropAll`, `useOnItem`, `useOnNpc`,
  `useOnObject`.
- `api.bank`: `isOpen`, `open()`, `openClosest()`, `close`, `contains`, `count`, `withdraw(name, qty)`,
  `withdrawAll`, `deposit`, `depositAll`, `depositAllExcept`, `setNoting`, `cached()` (last seen bank,
  readable while closed).
- `api.walker`: `walkTo(worldPoint)` walks on its own thread (doors, transports, teleports);
  poll `isWalking()`, `isAt(point)` and `getState()` from `onLoop`; `cancel()`.
- `api.equipment`, `api.skills`, `api.combat`, `api.prayers`, `api.spells`, `api.dialogue`,
  `api.shop`, `api.ge`, `api.trade`, `api.depositBox`, `api.tabs`, `api.run`, `api.camera`,
  `api.keyboard`, `api.teleports`, `api.fairyRings`, `api.login`, `api.breaks`,
  `api.config` (trusted scripts only).
- `api.states`: `priorities(name)` builds a priority selector (`.rule(name, condition, action)`
  in priority order, then `.register()`); `build(name)` builds a state machine. Both are ticked
  every game tick. Stop them in `onStop` with `api.states.stop(name)`.
- `api.quests`: plays a quest written as a `QuestScript` (`org.pureapps.api.quests`):
  `progress(client)` reads the quest's varp, `finalProgress()` gives its value once finished (for progress bars), `step(ctx, progress)` returns the next step (build
  them with `QuestSteps.talk`, `interactObject`, `useOnObject`, `pickUp`, `buy`, `dig`, `custom`, ...),
  `items(ctx, progress)` lists `ItemRequirement`s, which the runner takes from the bank or gets
  through their sources (`.gather(...)`, `.spawns(ItemSpawn.f2p(x, y, plane), ItemSpawn.members(...))` to pick it up from the nearest ground spawn the world allows, `.shop(npc, tile, price)`, `.ge()`); mark an item the
  quest hands out with `.fromQuest()` so the runner leaves it (and items gathered with it) for the
  quest's steps, and
  `dialogueOptions()` lists the options to pick. `onMessage(ctx, message)` sees each game message
  and dialogue screen, for steps the game keeps no var for (it is lost on restart). Start it on the client thread with
  `api.quests.start(quest, QuestOptions.defaults(), onStop)`; stop it with `api.quests.stop()`.
  `QuestOptions.builder().tickDelayMin(0).tickDelayMax(2)` adds a random extra wait after each
  action (every `ctx.waitTicks(n)`). The runner turns run on by itself once energy reaches a
  random level from `runEnergyMin` to `runEnergyMax` (20-50); `.autoRun(false)` turns that off.
  To play several in order, pass `new QuestChain(List.of(questA, questB), continueOnFailure)`
  instead; finished quests are skipped and `chain.getFailures()` lists the ones that failed.
  `chain.stateOf(i)` gives each quest's `QuestChain.Entry` (QUEUED, PLAYING, DONE, SKIPPED, FAILED).
- `api.invokeLater(runnable)` runs code on the client thread from another thread.
- `api.waits.waitUntil(condition, timeoutMs)` blocks: only use it off the client thread, which
  only trusted scripts can start.

Queries are fluent filters: `api.npcs.search().withName("Banker").nearestReachable()` returns an
`Optional`. `nearest()` is straight-line distance and ignores walls; `nearestReachable()` and
`reachable()` only find things the player can walk to. The facades' `interact(name, ...)` methods
already use `nearestReachable()`.

## Conventions

- Prefer names and `net.runelite.api.gameval` constants (`ItemID`, `NpcID`, `ObjectID`,
  `VarbitID`) over magic numbers.
- Write the main loop as "look at the game, do one thing, return". Don't send the same action
  again every tick while the player is still moving or animating from the last one.
- Keep helper classes in their own files in this folder; they are all compiled together.

## Working with the user

- You can't run the game. The user tests the script in the client and tells you what happened.
  Ask them to paste the Scripter output (compile errors, the script's `log` lines, and the stack
  trace when the script stopped) instead of guessing.
- With "Hot Reload" on, every file you save recompiles and restarts the script, so finish a
  change before saving files that depend on each other, and say when it is ready to test.
- The user may prompt you from the Scripter editor's AI chat instead of a terminal. Each prompt
  then ends with an "Editor context" block: the open file, the cursor line, the selected text
  and the current compile problems. "This" or "here" in the prompt usually means the selection or
  the open file. Hot reload waits until your turn ends, and in review mode the user sees a diff of
  each file you changed and may revert it, so keep changes to what was asked.
- Add `log` lines at decisions (what the script saw and what it chose) so the output explains a
  failure.
- Don't add build files, a `src/` layout or tests; the Scripter compiles this folder as it is.
- `CLAUDE.md` in this folder only imports this file. Edit this file, not that one, if notes
  should change; an edited `AGENTS.md` is no longer updated by the Scripter.
