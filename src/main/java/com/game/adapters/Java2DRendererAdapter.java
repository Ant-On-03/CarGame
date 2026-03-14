package com.game.adapters;

import com.game.ports.Renderer;
import com.game.ports.WheelRenderData;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferStrategy;
import java.util.ArrayDeque;
import java.util.Deque;

import java.awt.Canvas;

/**
 * Adapter that implements the {@link Renderer} port using Java2D
 * (AWT {@link java.awt.Graphics2D} + {@link java.awt.Canvas}).
 * <p>
 * Features:
 * <ul>
 *   <li>Double-buffered, anti-aliased rendering</li>
 *   <li>Persistent tire marks left by slipping wheels (fading over time)</li>
 *   <li>Two-sprite car: bottom chassis + top roof that shifts with weight distribution</li>
 *   <li>Per-wheel colour coding based on normal force and slip state</li>
 * </ul>
 */
public class Java2DRendererAdapter extends Canvas implements Renderer {

    // ---- Colours ----
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
    private static final Color ROOF_COLOR = new Color(0, 200, 160);
    private static final Color ROOF_OUTLINE = new Color(0, 160, 120);

    // ---- Layout constants ----
    private static final int GRID_SPACING = 60;
    private static final double CORNER_RADIUS = 6.0;

    // ---- Wheel rendering ----
    private static final double WHEEL_FORCE_LOW = 1000.0;
    private static final double WHEEL_FORCE_HIGH = 5000.0;
    private static final double WHEEL_WIDTH_RATIO = 0.35;
    private static final double WHEEL_LENGTH_RATIO = 0.55;

    // ---- Tire marks ----
    private static final int MAX_TIRE_MARKS = 2000;
    private static final int TIRE_MARK_FADE_FRAMES = 180; // ~3 seconds at 60 FPS

    // ---- Roof weight-shift ----
    /** Maximum pixels the roof sprite can shift from chassis centre. */
    private static final double ROOF_MAX_SHIFT = 3.0;
    /** Roof is 60% the size of the chassis. */
    private static final double ROOF_SCALE = 0.55;

    // ---- State ----
    private Graphics2D g2d;
    private BufferStrategy bufferStrategy;

    /** Ring buffer of tire mark points. Each mark stores position, age, and colour. */
    private final Deque<TireMark> tireMarks = new ArrayDeque<>();

    public Java2DRendererAdapter(int width, int height) {
        setSize(width, height);
        setIgnoreRepaint(true);
    }

    public void initBufferStrategy() {
        createBufferStrategy(2);
        bufferStrategy = getBufferStrategy();
    }

    // ================================================================
    // Renderer port implementation
    // ================================================================

    @Override
    public void beginFrame() {
        g2d = (Graphics2D) bufferStrategy.getDrawGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        clearBackground();
        drawGrid();
        ageTireMarks();
        drawTireMarks();
        drawControlsHUD();
    }

    @Override
    public void drawCar(double x, double y, double rotation, double width, double length,
                        double velocity, WheelRenderData[] wheels) {

        // 1. Record tire marks for slipping wheels (before drawing car)
        recordTireMarks(wheels);

        AffineTransform saved = g2d.getTransform();
        g2d.translate(x, y);
        g2d.rotate(rotation);

        // 2. Draw bottom sprite: glow → wheels → chassis body → front indicator → velocity bar
        drawGlow(width, length);
        drawWheels(wheels);
        drawChassis(width, length);
        drawFrontIndicator(width, length);
        drawVelocityBar(width, length, velocity);

        // 3. Draw top sprite: roof shifted by weight distribution
        drawRoof(width, length, wheels);

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

    // ================================================================
    // Background
    // ================================================================

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

    // ================================================================
    // Tire marks
    // ================================================================

    /**
     * Records world-space positions of slipping wheels as tire marks.
     * The trigger is {@code wheel.slipping()} — set when the combined
     * tire force exceeds the friction circle ({@code desiredMagnitude > maxStaticForce}
     * in {@code TireForceModel.applyFrictionLimit}).
     */
    private void recordTireMarks(WheelRenderData[] wheels) {
        if (wheels == null) return;
        for (WheelRenderData w : wheels) {
            if (w.slipping()) {
                Color markColor = computeWeightColor(w.normalForce(), false);
                tireMarks.addLast(new TireMark(w.worldX(), w.worldY(), TIRE_MARK_FADE_FRAMES, markColor));
                // Cap the buffer
                while (tireMarks.size() > MAX_TIRE_MARKS) {
                    tireMarks.removeFirst();
                }
            }
        }
    }

    /** Decrements age of all marks; removes expired ones. */
    private void ageTireMarks() {
        tireMarks.removeIf(mark -> {
            mark.age--;
            return mark.age <= 0;
        });
    }

    /** Draws all tire marks as small semi-transparent dots that fade with age. */
    private void drawTireMarks() {
        for (TireMark mark : tireMarks) {
            float opacity = (float) mark.age / TIRE_MARK_FADE_FRAMES;
            int alpha = (int) (opacity * 160);
            Color c = mark.color;
            g2d.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));

            double radius = 1.5 + opacity * 1.0;
            g2d.fill(new Ellipse2D.Double(
                    mark.x - radius, mark.y - radius,
                    radius * 2, radius * 2
            ));
        }
    }

    /** Mutable tire mark point with a countdown age. */
    private static class TireMark {
        final double x, y;
        final Color color;
        int age;

        TireMark(double x, double y, int age, Color color) {
            this.x = x;
            this.y = y;
            this.age = age;
            this.color = color;
        }
    }

    // ================================================================
    // Car rendering — bottom sprite (chassis)
    // ================================================================

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

    private void drawChassis(double width, double length) {
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

    private void drawVelocityBar(double width, double length, double velocity) {
        if (Math.abs(velocity) < 0.5) return;

        double maxSpeed = 40.0;
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

    // ================================================================
    // Car rendering — top sprite (roof with weight-shift)
    // ================================================================

    /**
     * Draws the roof sprite offset from the chassis centre based on weight distribution.
     * <p>
     * The offset vector is the <em>centre of pressure</em> computed from the 4 wheels'
     * normal forces:
     * <pre>
     *   offsetX = sum(wheel.offsetX * wheel.normalForce) / totalNormalForce
     *   offsetY = sum(wheel.offsetY * wheel.normalForce) / totalNormalForce
     * </pre>
     * This naturally moves toward whichever wheels carry the most load:
     * braking → shifts forward, accelerating → shifts rearward,
     * left turn → shifts right, etc.
     */
    private void drawRoof(double width, double length, WheelRenderData[] wheels) {
        double shiftX = 0.0;
        double shiftY = 0.0;

        if (wheels != null && wheels.length > 0) {
            double totalForce = 0.0;
            double weightedX = 0.0;
            double weightedY = 0.0;

            for (WheelRenderData w : wheels) {
                totalForce += w.normalForce();
                weightedX += w.offsetX() * w.normalForce();
                weightedY += w.offsetY() * w.normalForce();
            }

            if (totalForce > 0.01) {
                // Centre of pressure in car-local pixels
                double copX = weightedX / totalForce;
                double copY = weightedY / totalForce;

                // Clamp shift magnitude
                double mag = Math.sqrt(copX * copX + copY * copY);
                if (mag > ROOF_MAX_SHIFT) {
                    double scale = ROOF_MAX_SHIFT / mag;
                    copX *= scale;
                    copY *= scale;
                }
                shiftX = copX;
                shiftY = copY;
            }
        }

        double roofW = width * ROOF_SCALE;
        double roofL = length * ROOF_SCALE;

        RoundRectangle2D roof = new RoundRectangle2D.Double(
                -roofW / 2 + shiftX,
                -roofL / 2 + shiftY,
                roofW, roofL,
                4.0, 4.0
        );

        g2d.setColor(ROOF_COLOR);
        g2d.fill(roof);

        g2d.setColor(ROOF_OUTLINE);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.draw(roof);
    }

    // ================================================================
    // Wheel rendering
    // ================================================================

    private void drawWheels(WheelRenderData[] wheels) {
        if (wheels == null) return;
        for (WheelRenderData w : wheels) {
            drawSingleWheel(w);
        }
    }

    private void drawSingleWheel(WheelRenderData w) {
        AffineTransform saved = g2d.getTransform();

        g2d.translate(w.offsetX(), w.offsetY());
        g2d.rotate(w.steeringAngle());

        double wheelW = Math.max(Math.abs(w.offsetX()) * WHEEL_WIDTH_RATIO, 3.0);
        double wheelH = Math.max(Math.abs(w.offsetY()) * WHEEL_LENGTH_RATIO, 6.0);

        Color wheelColor = computeWeightColor(w.normalForce(), w.slipping());

        RoundRectangle2D wheelShape = new RoundRectangle2D.Double(
                -wheelW / 2, -wheelH / 2,
                wheelW, wheelH,
                2.0, 2.0
        );
        g2d.setColor(wheelColor);
        g2d.fill(wheelShape);

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
     * green (low) → yellow (mid) → red (high).
     * Slipping overrides to magenta.
     */
    private Color computeWeightColor(double normalForce, boolean slipping) {
        if (slipping) {
            return new Color(230, 0, 230);
        }

        double t = (normalForce - WHEEL_FORCE_LOW) / (WHEEL_FORCE_HIGH - WHEEL_FORCE_LOW);
        t = Math.max(0.0, Math.min(1.0, t));

        int r, g, b;
        if (t < 0.5) {
            double s = t * 2.0;
            r = (int) (s * 230);
            g = 230;
            b = 0;
        } else {
            double s = (t - 0.5) * 2.0;
            r = 230;
            g = (int) ((1.0 - s) * 230);
            b = 0;
        }
        return new Color(r, g, b);
    }

    // ================================================================
    // HUD
    // ================================================================

    private void drawControlsHUD() {
        Font labelFont = new Font("Consolas", Font.PLAIN, 13);
        Font headerFont = new Font("Consolas", Font.BOLD, 14);
        g2d.setFont(headerFont);

        int panelX = 12;
        int panelY = 12;
        int panelW = 210;
        int panelH = 120;

        g2d.setColor(HUD_BG);
        g2d.fillRoundRect(panelX, panelY, panelW, panelH, 8, 8);

        g2d.setColor(HUD_KEY);
        g2d.drawString("CONTROLS", panelX + 10, panelY + 22);

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
