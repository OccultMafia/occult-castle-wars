import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.pureapps.api.overlays.OverlayButton;
import org.pureapps.api.overlays.StatusCard;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;

public class StatsOverlay extends Overlay implements MouseListener {

    private final StatusCard card = new StatusCard();

    int     gamesPlayed      = 0;
    int     sessionTickets   = 0;
    int     totalTickets     = 0;
    long    sessionStartMs   = System.currentTimeMillis();
    boolean refreshRequested = false;

    private long flashUntilMs  = 0;
    private final Point absMousePos = new Point();
    private int overlayScreenX = 0;
    private int overlayScreenY = 0;

    StatsOverlay() {
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D g) {
        // Store overlay's canvas position for click coordinate mapping
        AffineTransform t = g.getTransform();
        overlayScreenX = (int) t.getTranslateX();
        overlayScreenY = (int) t.getTranslateY();

        double hrs = (System.currentTimeMillis() - sessionStartMs) / 3_600_000.0;
        String rate = hrs > 1.0 / 60
            ? String.valueOf(Math.round(sessionTickets / hrs))
            : "—";

        boolean flashing = System.currentTimeMillis() < flashUntilMs;
        card.clear()
            .title("Live Stats")
            .row("GAMES PLAYED", String.valueOf(gamesPlayed))
            .row("TOTAL TICKETS", String.valueOf(totalTickets))
            .row("TICKETS /hr", rate)
            .button(
                flashing ? "Updated!" : "Force Update",
                flashing ? new Color(0, 200, 80) : new Color(0, 180, 255),
                () -> {
                    refreshRequested = true;
                    flashUntilMs = System.currentTimeMillis() + 2000;
                }
            );

        // Pass absolute mouse position — StatusCard uses the Graphics2D transform
        // to resolve hover state per button
        return card.render(g, absMousePos);
    }

    // ── MouseListener ─────────────────────────────────────────────────────────

    @Override
    public MouseEvent mouseMoved(MouseEvent e) {
        absMousePos.setLocation(e.getX(), e.getY());
        return e;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent e) {
        absMousePos.setLocation(e.getX(), e.getY());
        return e;
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent e) {
        // Convert canvas click to overlay-local coordinates and fire matching button
        Point rel = new Point(e.getX() - overlayScreenX, e.getY() - overlayScreenY);
        for (OverlayButton btn : card.drawnButtons()) {
            if (btn.contains(rel)) {
                btn.click();
                break;
            }
        }
        return e;
    }

    @Override public MouseEvent mousePressed(MouseEvent e)  { return e; }
    @Override public MouseEvent mouseReleased(MouseEvent e) { return e; }
    @Override public MouseEvent mouseEntered(MouseEvent e)  { return e; }
    @Override public MouseEvent mouseExited(MouseEvent e)   { return e; }
}
