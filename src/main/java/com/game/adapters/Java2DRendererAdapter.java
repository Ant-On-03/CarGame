package com.game.adapters;

import com.game.ports.Renderer;
import com.game.ports.WheelRenderData;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferStrategy;

import javax.swing.JFrame;
import java.awt.Canvas;

/**
 * Adapter that implements the {@link Renderer} port using Java2D
 * (AWT {@link Graphics2D} + Swing {@link JFrame}).
 * <p>
 * Uses double-buffering via {@link BufferStrategy} for smooth,
 * tear-free rendering. Applies anti-aliasing for clean edges.
 */
public class Java2DRendererAdapter extends Canvas implements Renderer {

    private static final Color BACKGROUND = new Color(30, 30, 36);
    private static final Color GRID_COLOR = new Color(45, 45, 52);
    private static final Color CAR_BODY = new Color(0, 230, 180);
    private static final Color CAR_OUTLINE = new Color(0, 180, 140);
    private static final Color CAR_FRONT = new Color(255, 80, 100);
    private static final Color CAR_GLOW = new Color(0, 230, 180, 40);
    private static final Color VELOCITY_INDICATOR = new Color(0, 230, 180, 100);
    private static final Color HUD_TEXT = new Color(200, 200, 210, 180);
    private static final Color HUD_KEY = new Color(0, 230, 180, 200);
    private static final Color HUD_BG = new Color(20, 20, 26, 160);

    private static final int GRID_SPACING = 60;
    private static final double CORNER_RADIUS = 6.0;

    private Graphics2D g2d;
    private BufferStrategy bufferStrategy;

    public Java2DRendererAdapter(int width, int height) {
        setSize(width, height);
        setIgnoreRepaint(true);
    }

    /**
     * Must be called after the canvas has been added to a visible container
     * so that the underlying peer exists for buffer strategy creation.
     */
    public void initBufferStrategy() {
        createBufferStrategy(2);
        bufferStrategy = getBufferStrategy();
    }

    @Override
    public void beginFrame() {
        g2d = (Graphics2D) bufferStrategy.getDrawGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        clearBackground();
        drawGrid();
        drawControlsHUD();
    }

    @Override
    public void drawCar(double x, double y, double rotation, double width, double length,
                        double velocity, WheelRenderData[] wheels) {
        AffineTransform saved = g2d.getTransform();

        g2d.translate(x, y);
        g2d.rotate(rotation);

        drawGlow(width, length);
        drawWheels(wheels);
        drawBody(width, length);
        drawFrontIndicator(width, length);
        drawVelocityBar(width, length, velocity);

        g2d.setTransform(saved);
    }

    @Override
    public void endFrame() {
        g2d.dispose();
        if (!bufferStrategy.contentsLost()) {
            bufferStrategy.show();
        }
    }

    @Override
    public int getCanvasWidth() {
        return getWidth();
    }

    @Override
    public int getCanvasHeight() {
        return getHeight();
    }

    // ---- Private rendering helpers ----

    private void clearBackground() {
        g2d.setColor(BACKGROUND);
        g2d.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawGrid() {
        g2d.setColor(GRID_COLOR);
        g2d.setStroke(new BasicStroke(1.0f));

        for (int gx = 0; gx < getWidth(); gx += GRID_SPACING) {
            g2d.drawLine(gx, 0, gx, getHeight());
        }
        for (int gy = 0; gy < getHeight(); gy += GRID_SPACING) {
            g2d.drawLine(0, gy, getWidth(), gy);
        }
    }

    private void drawGlow(double width, double length) {
        double glowPadding = 8.0;
        g2d.setColor(CAR_GLOW);
        g2d.fill(new RoundRectangle2D.Double(
                -width / 2 - glowPadding,
                -length / 2 - glowPadding,
                width + glowPadding * 2,
                length + glowPadding * 2,
                CORNER_RADIUS + glowPadding,
                CORNER_RADIUS + glowPadding
        ));
    }

    private void drawBody(double width, double length) {
        RoundRectangle2D body = new RoundRectangle2D.Double(
                -width / 2, -length / 2,
                width, length,
                CORNER_RADIUS, CORNER_RADIUS
        );

        g2d.setColor(CAR_BODY);
        g2d.fill(body);

        g2d.setColor(CAR_OUTLINE);
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.draw(body);
    }

    private void drawFrontIndicator(double width, double length) {
        double indicatorHeight = length * 0.18;
        double indicatorInset = 4.0;

        RoundRectangle2D front = new RoundRectangle2D.Double(
                -width / 2 + indicatorInset,
                -length / 2 + indicatorInset,
                width - indicatorInset * 2,
                indicatorHeight,
                3.0, 3.0
        );

        g2d.setColor(CAR_FRONT);
        g2d.fill(front);
    }

    // ---- Wheel rendering ----

    /** Normal force range for colour mapping (N). */
    private static final double WHEEL_FORCE_LOW = 1000.0;
    private static final double WHEEL_FORCE_HIGH = 5000.0;

    /** Wheel visual dimensions relative to pixels-per-meter scale. */
    private static final double WHEEL_WIDTH_RATIO = 0.35;
    private static final double WHEEL_LENGTH_RATIO = 0.55;

    private void drawWheels(WheelRenderData[] wheels) {
        if (wheels == null) {
            return;
        }
        for (WheelRenderData w : wheels) {
            drawSingleWheel(w);
        }
    }

    private void drawSingleWheel(WheelRenderData w) {
        AffineTransform saved = g2d.getTransform();

        // Position at wheel offset (already in car-local pixel coords)
        g2d.translate(w.offsetX(), w.offsetY());
        g2d.rotate(w.steeringAngle());

        // Wheel size — proportional to offset magnitude as a rough scale
        double wheelW = Math.abs(w.offsetX()) * WHEEL_WIDTH_RATIO;
        double wheelH = Math.abs(w.offsetY()) * WHEEL_LENGTH_RATIO;
        // Clamp minimums
        wheelW = Math.max(wheelW, 3.0);
        wheelH = Math.max(wheelH, 6.0);

        // Colour based on normal force: green (low) → yellow (mid) → red (high)
        Color wheelColor = computeWeightColor(w.normalForce(), w.slipping());

        // Draw the wheel body
        RoundRectangle2D wheelShape = new RoundRectangle2D.Double(
                -wheelW / 2, -wheelH / 2,
                wheelW, wheelH,
                2.0, 2.0
        );
        g2d.setColor(wheelColor);
        g2d.fill(wheelShape);

        // Outline
        g2d.setColor(new Color(
                Math.max(wheelColor.getRed() - 40, 0),
                Math.max(wheelColor.getGreen() - 40, 0),
                Math.max(wheelColor.getBlue() - 40, 0)
        ));
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.draw(wheelShape);

        g2d.setTransform(saved);
    }

    /**
     * Maps normal force to a colour gradient:
     * <ul>
     *   <li>Low force  → green (#00E64D)</li>
     *   <li>Mid force  → yellow (#E6E600)</li>
     *   <li>High force → red (#E63300)</li>
     *   <li>Slipping   → magenta (#E600E6) override</li>
     * </ul>
     */
    private Color computeWeightColor(double normalForce, boolean slipping) {
        if (slipping) {
            return new Color(230, 0, 230); // magenta = slipping
        }

        double t = (normalForce - WHEEL_FORCE_LOW) / (WHEEL_FORCE_HIGH - WHEEL_FORCE_LOW);
        t = Math.max(0.0, Math.min(1.0, t));

        int r, g, b;
        if (t < 0.5) {
            // Green → Yellow
            double s = t * 2.0;
            r = (int) (s * 230);
            g = 230;
            b = 0;
        } else {
            // Yellow → Red
            double s = (t - 0.5) * 2.0;
            r = 230;
            g = (int) ((1.0 - s) * 230);
            b = 0;
        }

        return new Color(r, g, b);
    }

    private void drawVelocityBar(double width, double length, double velocity) {
        if (Math.abs(velocity) < 0.5) {
            return;
        }

        double maxSpeed = 40.0; // ~144 km/h, realistic top speed in physics
        double ratio = Math.min(Math.abs(velocity) / maxSpeed, 1.0);
        double barWidth = (width - 10) * ratio;

        g2d.setColor(VELOCITY_INDICATOR);
        g2d.fillRect(
                (int) (-barWidth / 2),
                (int) (length / 2 - 6),
                (int) barWidth,
                3
        );
    }

    private void drawControlsHUD() {
        Font labelFont = new Font("Consolas", Font.PLAIN, 13);
        Font headerFont = new Font("Consolas", Font.BOLD, 14);
        g2d.setFont(headerFont);

        int panelX = 12;
        int panelY = 12;
        int panelW = 210;
        int panelH = 120;

        // Background panel
        g2d.setColor(HUD_BG);
        g2d.fillRoundRect(panelX, panelY, panelW, panelH, 8, 8);

        // Header
        g2d.setColor(HUD_KEY);
        g2d.drawString("CONTROLS", panelX + 10, panelY + 22);

        // Controls list
        g2d.setFont(labelFont);
        int lineY = panelY + 42;
        int lineSpacing = 18;

        drawControlLine("W / UP", "Throttle", panelX + 10, lineY);
        drawControlLine("S / DOWN / SPACE", "Brake", panelX + 10, lineY + lineSpacing);
        drawControlLine("A / LEFT", "Steer left", panelX + 10, lineY + lineSpacing * 2);
        drawControlLine("D / RIGHT", "Steer right", panelX + 10, lineY + lineSpacing * 3);
    }

    private void drawControlLine(String key, String action, int x, int y) {
        g2d.setColor(HUD_KEY);
        g2d.drawString(key, x, y);
        g2d.setColor(HUD_TEXT);
        g2d.drawString(" " + action, x + g2d.getFontMetrics().stringWidth(key), y);
    }
}
