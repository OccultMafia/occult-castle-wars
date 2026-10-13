import net.runelite.api.GameState;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import javax.inject.Inject;
import org.pureapps.api.*;
import org.pureapps.api.scripts.*;
import org.pureapps.api.data.Teleport;

import java.awt.Color;
import java.util.Random;

@ScriptManifest(name = "Occult Castle Wars", version = "1.0", description = "Farms participation tickets — joins games and AFKs on the castle roof")
public class Main extends Script {

    @Inject
    private CastleWarsConfig config;

    @Inject
    private net.runelite.client.input.MouseManager mouseManager;

    private enum State {
        TELEPORT_TO_CW,
        HOP_WORLD,
        BANK_AND_CLEAR,
        ENTER_PORTAL,
        WAIT_FOR_GAME,
        CLIMB_TO_ROOF,
        IDLE_ON_ROOF
    }

    // ── Coordinates ───────────────────────────────────────────────────────────
    private static final int CW_X1 = 2368, CW_X2 = 2444;
    private static final int CW_Y1 = 3072, CW_Y2 = 3135;

    private static final WorldArea SARA_WAITING  = new WorldArea(2369, 9482, 22, 14, 0);
    private static final WorldArea ZAMMY_WAITING = new WorldArea(2410, 9514, 19, 19, 0);

    private static final WorldArea[] SARA_ROOF_AREAS = {
        new WorldArea(2426, 3073, 5, 1, 2),
        new WorldArea(2426, 3073, 1, 5, 2),
        new WorldArea(2430, 3073, 5, 1, 2),
        new WorldArea(2423, 3072, 1, 1, 2),
        new WorldArea(2426, 3072, 1, 5, 2)
    };

    private static final WorldArea[] ZAMMY_ROOF_AREAS = {
        new WorldArea(2369, 3135, 1, 5, 2),
        new WorldArea(2376, 3135, 1, 1, 2),
        new WorldArea(2369, 3130, 5, 1, 2),
        new WorldArea(2369, 3134, 1, 5, 2),
        new WorldArea(2373, 3130, 5, 1, 2)
    };

    private static final WorldPoint LOBBY_WALK_TARGET = new WorldPoint(2440, 3089, 0);

    // ── Script state ──────────────────────────────────────────────────────────
    private final Random rng         = new Random();
    private State state              = State.TELEPORT_TO_CW;
    private WorldPoint afkTile       = null;
    private int climbDelay           = 0;
    private boolean inventoryCleared = false;
    private boolean gearDeposited    = false;
    private int debugTick            = 0;
    private StatsOverlay overlay;

    // ── Startup checks ────────────────────────────────────────────────────────
    private int            breakWarningTicks   = 0;
    private MessageOverlay breakPopup          = null;

    // ── Session stats ─────────────────────────────────────────────────────────
    private int     gamesPlayed          = 0;
    private int     sessionTickets       = 0;
    private int     initialCwTickets     = 0;
    private int     lastGameEndTickets   = 0;
    private long    sessionStartMs       = 0;

    @Override
    public void onStart() {
        log("Occult Castle Wars started — target world: {}", targetWorld());

        // Break Handler check — warn once if off, script continues
        if (api.breaks.phase() == Breaks.Phase.OFF) {
            breakWarningTicks = 50;
            breakPopup = new MessageOverlay(
                new String[]{
                    "It is highly recommended that you",
                    "TURN ON the Break Handler and run it",
                    "in conjunction with this script."
                },
                new Color(220, 160, 0)
            );
            addOverlay(breakPopup);
            log("Break Handler is OFF — one-time warning shown");
        }

        if (api.combat.isAutoRetaliate()) {
            log("Auto Retaliate is ON — turning it off");
            api.combat.setAutoRetaliate(false);
        }
        api.breaks.block("castle-wars-game", () ->
            state == State.WAIT_FOR_GAME ||
            state == State.CLIMB_TO_ROOF ||
            state == State.IDLE_ON_ROOF);
        gamesPlayed          = 0;
        sessionTickets       = 0;
        initialCwTickets     = api.inventory.quantity("Castle wars ticket");
        lastGameEndTickets   = 0;
        inventoryCleared     = false;
        gearDeposited        = false;
        debugTick        = 0;
        sessionStartMs   = System.currentTimeMillis();
        state            = State.TELEPORT_TO_CW;
        afkTile          = null;
        overlay = new StatsOverlay();
        overlay.sessionStartMs = sessionStartMs;
        addOverlay(overlay);
        mouseManager.registerMouseListener(overlay);
    }

    @Override
    public void onLoop() {
        // Dismiss break handler warning after timed display (~30 s)
        if (breakWarningTicks > 0 && --breakWarningTicks == 0 && breakPopup != null) {
            breakPopup.visible = false;
        }

        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (api.player.inCutscene()) return;

        // Periodic status dump every 50 ticks (~30 sec)
        if (++debugTick % 50 == 0) {
            WorldPoint p = api.player.getWorldLocation();
            log("[tick {}] state={} pos=({},{},plane={}) moving={} animating={} world={}",
                debugTick, state, p.getX(), p.getY(), p.getPlane(),
                api.player.isMoving(), api.player.isAnimating(), client.getWorld());
        }

        // Update live ticket count first so game-complete log and overlay are always current
        sessionTickets = Math.max(0, api.inventory.quantity("Castle wars ticket") - initialCwTickets);

        State next = detectState();
        if (next != state) {
            boolean wasInGame    = state == State.WAIT_FOR_GAME || state == State.CLIMB_TO_ROOF || state == State.IDLE_ON_ROOF;
            boolean goingToLobby = next  == State.ENTER_PORTAL  || next  == State.TELEPORT_TO_CW ||
                                   next  == State.HOP_WORLD      || next  == State.BANK_AND_CLEAR;
            if (wasInGame && goingToLobby) {
                int earnedThisGame = sessionTickets - lastGameEndTickets;
                lastGameEndTickets = sessionTickets;
                gamesPlayed++;
                log("Game {} complete — +{} ticket(s) this game | {} total this session | {} in inventory",
                    gamesPlayed, earnedThisGame, sessionTickets, api.inventory.quantity("Castle wars ticket"));
            }
            if (api.walker.isWalking()) {
                log("Cancelling walker on state change {} → {}", state, next);
                api.walker.cancel();
            }
            log("State {} → {} at ({},{},plane={})",
                state, next,
                api.player.getWorldLocation().getX(),
                api.player.getWorldLocation().getY(),
                api.player.getWorldLocation().getPlane());
            state   = next;
            afkTile = null;
            if (next == State.CLIMB_TO_ROOF) {
                climbDelay = rng.nextInt(3) + 3;
                log("Respawned — waiting {} ticks before climbing", climbDelay);
            }
        }
        if (overlay.refreshRequested) {
            overlay.refreshRequested = false;
            log("[Force Update] initial={} | inv={} | session={} | games={} | state={}",
                initialCwTickets, api.inventory.quantity("Castle wars ticket"), sessionTickets, gamesPlayed, state);
        }
        overlay.gamesPlayed    = gamesPlayed;
        overlay.sessionTickets = sessionTickets;
        overlay.totalTickets   = initialCwTickets + sessionTickets;
        status(label(state));

        switch (state) {
            case TELEPORT_TO_CW:  doTeleport();    break;
            case HOP_WORLD:       doHop();         break;
            case BANK_AND_CLEAR:  doBank();        break;
            case ENTER_PORTAL:    doEnterPortal(); break;
            case WAIT_FOR_GAME:   doWaitForGame(); break;
            case CLIMB_TO_ROOF:   doClimb();       break;
            case IDLE_ON_ROOF:    doIdleOnRoof();  break;
        }
    }

    @Override
    public void onStop() {
        mouseManager.unregisterMouseListener(overlay);
        api.walker.cancel();
        api.breaks.unblock("castle-wars-game");
        log("Stopped — {} game(s), {} ticket(s) this session", gamesPlayed, sessionTickets);
    }

    // ── State detection ───────────────────────────────────────────────────────

    private State detectState() {
        WorldPoint pos = api.player.getWorldLocation();
        int plane = pos.getPlane();

        // Waiting rooms are instanced (high y) — check before region check
        if (SARA_WAITING.contains(pos) || ZAMMY_WAITING.contains(pos)) return State.WAIT_FOR_GAME;

        // plane 2 = top of either castle (roof), plane 1 = inside castle (need to climb)
        if (plane == 2) return State.IDLE_ON_ROOF;
        if (plane == 1) return State.CLIMB_TO_ROOF;

        // Overworld, plane 0
        if (!inCastleWarsRegion(pos)) return State.TELEPORT_TO_CW;
        if (!inventoryCleared) return State.BANK_AND_CLEAR;
        if (client.getWorld() != targetWorld()) return State.HOP_WORLD;
        return State.ENTER_PORTAL;
    }

    private boolean inCastleWarsRegion(WorldPoint pos) {
        int x = pos.getX(), y = pos.getY();
        return x >= CW_X1 && x <= CW_X2 && y >= CW_Y1 && y <= CW_Y2;
    }

    private boolean inAnyArea(WorldArea[] areas, WorldPoint pos) {
        for (WorldArea area : areas) {
            if (area.contains(pos)) return true;
        }
        return false;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void doTeleport() {
        // Handle teleport confirmation dialogues — cancel walker first so its thread
        // isn't touching widgets at the same time we are
        if (api.dialogue.canContinue()) {
            if (api.walker.isWalking()) api.walker.cancel();
            log("doTeleport: clicking continue");
            api.dialogue.continueDialogue();
            return;
        }
        if (api.dialogue.hasOptions()) {
            if (api.walker.isWalking()) api.walker.cancel();
            log("doTeleport: selecting Yes");
            api.dialogue.chooseOption("Yes");
            return;
        }
        if (api.walker.isWalking() || api.player.isMoving() || api.player.isAnimating()) return;
        if (api.teleports.use(Teleport.RING_OF_DUELING_CASTLE_WARS)) {
            log("doTeleport: used Ring of Dueling → Castle Wars");
        } else {
            log("doTeleport: no Ring of Dueling — starting walker to {}", LOBBY_WALK_TARGET);
            api.walker.walkTo(LOBBY_WALK_TARGET);
        }
    }

    private void doBank() {
        if (!api.bank.isOpen()) {
            log("doBank: bank not open — opening closest");
            api.bank.openClosest();
            return;
        }
        // Deposit everything except Castle Wars tickets
        if (hasNonTicketItems()) {
            int nonTicketSlots = 28 - api.inventory.emptySlots() - (api.inventory.quantity("Castle wars ticket") > 0 ? 1 : 0);
            log("doBank: depositing {} non-ticket slot(s), keeping Castle Wars tickets", nonTicketSlots);
            api.bank.depositAllExcept("Castle wars ticket");
            return;
        }
        // Deposit worn equipment (once per session)
        if (!gearDeposited) {
            log("doBank: depositing worn equipment");
            api.bank.depositEquipment();
            gearDeposited = true;
            return;
        }
        // Pull any tickets out of the bank — count them as pre-session (not earned this run)
        if (api.bank.contains("Castle wars ticket")) {
            int banked = api.bank.quantity("Castle wars ticket");
            log("doBank: withdrawing {} banked ticket(s)", banked);
            initialCwTickets += banked;
            api.bank.withdrawAll("Castle wars ticket");
            return;
        }
        log("doBank: closing bank");
        api.bank.close();
        inventoryCleared = true;
        gearDeposited    = false;
    }

    private void doHop() {
        if (client.getGameState() == GameState.HOPPING) return;
        int target = targetWorld();
        log("doHop: hopping from world {} to world {}", client.getWorld(), target);
        api.login.hop(target);
    }

    private void doEnterPortal() {
        WorldPoint pos = api.player.getWorldLocation();
        if (api.player.isMoving() || api.player.isAnimating()) return;
        boolean sent = api.objects.interact(4408, "Enter");
        if (sent) {
            log("doEnterPortal: sent Enter on Guthix portal (id=4408) from ({},{})", pos.getX(), pos.getY());
        } else {
            log("doEnterPortal: Guthix portal (id=4408) NOT found near ({},{}) — check proximity", pos.getX(), pos.getY());
        }
    }

    private void doWaitForGame() {
        if (afkTile == null) {
            WorldPoint pos = api.player.getWorldLocation();
            WorldArea room = ZAMMY_WAITING.contains(pos) ? ZAMMY_WAITING : SARA_WAITING;
            afkTile = randomTile(room);
            log("doWaitForGame: picked AFK tile {} (room origin=({},{}))", afkTile, room.getX(), room.getY());
        }
        walkToAfkTile();
    }

    private void doClimb() {
        if (climbDelay > 0) {
            log("doClimb: delay {} tick(s) remaining", climbDelay);
            climbDelay--;
            return;
        }
        if (api.player.isMoving() || api.player.isAnimating()) return;
        log("doClimb: attempting to climb — trying Ladder, Staircase, wall Ladder");
        if (api.objects.interact("Ladder", "Climb-up")) {
            log("doClimb: interacted with Ladder");
        } else if (api.objects.interact("Staircase", "Climb-up")) {
            log("doClimb: interacted with Staircase");
        } else if (api.walls.interact("Ladder", "Climb-up")) {
            log("doClimb: interacted with wall Ladder");
        } else {
            log("doClimb: no climbable object found at ({},{},plane={})",
                api.player.getWorldLocation().getX(),
                api.player.getWorldLocation().getY(),
                api.player.getWorldLocation().getPlane());
        }
    }

    private void doIdleOnRoof() {
        if (afkTile == null) {
            WorldPoint pos = api.player.getWorldLocation();
            // Sara castle is centred around x≈2424, Zammy around x≈2369 — use x>2400 to tell them apart
            boolean sara = pos.getX() > 2400;
            WorldArea[] roofAreas = sara ? SARA_ROOF_AREAS : ZAMMY_ROOF_AREAS;
            String side = sara ? "Sara" : "Zammy";
            afkTile = randomTileFromAreas(roofAreas);
            log("doIdleOnRoof: picked AFK tile {} ({} roof)", afkTile, side);
        }
        walkToAfkTile();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int targetWorld() {
        return config.members() ? 354 : 383;
    }

    private WorldPoint randomTile(WorldArea area) {
        int x = area.getX() + rng.nextInt(area.getWidth());
        int y = area.getY() + rng.nextInt(area.getHeight());
        return new WorldPoint(x, y, area.getPlane());
    }

    private WorldPoint randomTileFromAreas(WorldArea[] areas) {
        int total = 0;
        for (WorldArea a : areas) total += a.getWidth() * a.getHeight();
        int pick = rng.nextInt(total);
        for (WorldArea a : areas) {
            int tiles = a.getWidth() * a.getHeight();
            if (pick < tiles) return randomTile(a);
            pick -= tiles;
        }
        return randomTile(areas[0]);
    }

    private boolean hasNonTicketItems() {
        int usedSlots  = 28 - api.inventory.emptySlots();
        boolean hasTickets = api.inventory.contains("Castle wars ticket");
        return usedSlots > (hasTickets ? 1 : 0);
    }

    private void walkToAfkTile() {
        if (afkTile == null) return;
        if (api.walker.isWalking() || api.player.isMoving()) return;
        if (api.player.getWorldLocation().distanceTo(afkTile) > 1) {
            api.walker.walkTo(afkTile);
        }
    }

    private static String label(State s) {
        switch (s) {
            case TELEPORT_TO_CW:  return "Teleporting to CW";
            case HOP_WORLD:       return "Hopping world";
            case BANK_AND_CLEAR:  return "Banking gear";
            case ENTER_PORTAL:    return "Entering portal";
            case WAIT_FOR_GAME:   return "Waiting for game";
            case CLIMB_TO_ROOF:   return "Climbing to roof";
            case IDLE_ON_ROOF:    return "Idling on roof";
            default:              return s.name();
        }
    }

    private static Color stateColor(State s) {
        switch (s) {
            case IDLE_ON_ROOF:   return Color.GREEN;
            case WAIT_FOR_GAME:  return new Color(0, 200, 255);
            case CLIMB_TO_ROOF:  return Color.ORANGE;
            case BANK_AND_CLEAR: return Color.ORANGE;
            default:             return Color.YELLOW;
        }
    }
}
