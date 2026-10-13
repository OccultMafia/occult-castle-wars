import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import java.awt.*;

public class MessageOverlay extends Overlay {

    private final String[] lines;
    private final Color    accent;
    boolean visible = true;

    MessageOverlay(String[] lines, Color accent) {
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.TOP_CENTER);
        this.lines  = lines;
        this.accent = accent;
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!visible) return null;

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        int lh   = fm.getHeight();
        int maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, fm.stringWidth(l));

        int boxW = maxW + 24;
        int boxH = lines.length * lh + 20;

        g.setColor(new Color(15, 15, 15, 215));
        g.fillRoundRect(0, 0, boxW, boxH, 8, 8);
        g.setColor(accent);
        g.drawRoundRect(1, 1, boxW - 2, boxH - 2, 8, 8);

        g.setColor(Color.WHITE);
        int ty = 10 + fm.getAscent();
        for (String l : lines) {
            g.drawString(l, 12, ty);
            ty += lh;
        }

        return new Dimension(boxW, boxH);
    }
}
