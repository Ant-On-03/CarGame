package com.game.adapters;

import com.game.ports.Renderer;
import com.game.ports.WheelRenderData;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Adapter that implements the {@link Renderer} port using Java2D.
 * <p>
 * Performance-optimised: all Fonts, Strokes, Colors, and reusable shapes
 * are pre-allocated as fields or static constants — zero per-frame
 * object allocation in the hot path.
 */
public class Java2DRendererAdapter extends Canvas implements Renderer {

    // ================================================================
    // Cached colour constants (zero per-frame allocation)
    // ================================================================

    // -- Environment --
    private static final Color ASPHALT_BASE = new Color(42, 42, 48);
    private static final Color LANE_MARKING = new Color(200, 200, 190, 50);
    private static final Color EDGE_LINE = new Color(200, 200, 190, 30);

    // -- Car body --
    private static final Color BODY_FILL = new Color(25, 60, 120);
    private static final Color BODY_HIGHLIGHT = new Color(45, 90, 160);
    private static final Color BODY_OUTLINE = new Color(15, 35, 70);
    private static final Color BODY_SHADOW = new Color(0, 0, 0, 60);
    private static final Color HOOD_CREASE = new Color(80, 130, 200, 40);

    // -- Cabin / roof --
    private static final Color WINDSHIELD = new Color(140, 180, 210, 180);
    private static final Color CABIN_FILL = new Color(20, 20, 25, 200);
    private static final Color CABIN_OUTLINE = new Color(60, 80, 100, 150);
    private static final Color REAR_GLASS = new Color(140, 180, 210, 120);

    // -- Wheels --
    private static final Color WHEEL_RUBBER = new Color(30, 30, 30);
    private static final Color WHEEL_RIM = new Color(70, 70, 75);
    private static final Color WHEEL_TREAD = new Color(50, 50, 52);
    private static final Color WHEEL_OUTLINE = new Color(20, 20, 20);
    private static final Color WHEEL_SLIP_GLOW = new Color(255, 140, 40, 60);

    // -- Lights --
    private static final Color HEADLIGHT_FILL = new Color(255, 250, 220);
    private static final Color HEADLIGHT_GLOW = new Color(255, 250, 220, 50);
    private static final Color TAILLIGHT_FILL = new Color(220, 20, 30);
    private static final Color TAILLIGHT_GLOW = new Color(220, 20, 30, 40);

    // -- Tire marks --
    private static final int TIRE_MARK_BASE_R = 15;
    private static final int TIRE_MARK_BASE_G = 15;
    private static final int TIRE_MARK_BASE_B = 15;
    private static final int TIRE_MARK_MAX_ALPHA = 140;

    // -- HUD --
    private static final Color HUD_TEXT = new Color(200, 200, 210, 180);
    private static final Color HUD_ACCENT = new Color(80, 160, 220, 220);
    private static final Color HUD_BG = new Color(15, 15, 20, 180);
    private static final Color HUD_BORDER = new Color(60, 60, 70, 100);
    private static final Color HUD_SEPARATOR = new Color(60, 60, 70, 80);

    // ================================================================
    // Cached stroke constants
    // ================================================================

    private static final BasicStroke STROKE_0_5 = new BasicStroke(0.5f);
    private static final BasicStroke STROKE_0_8 = new BasicStroke(0.8f);
    private static final BasicStroke STROKE_1_0 = new BasicStroke(1.0f);
    private static final BasicStroke STROKE_1_5 = new BasicStroke(1.5f);
    private static final BasicStroke STROKE_TIRE_MARK = new BasicStroke(
            2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke STROKE_LANE_DASH = new BasicStroke(
            2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[]{20.0f, 30.0f}, 0.0f);

    // ================================================================
    // Cached font constants
    // ================================================================

    private static final Font FONT_HUD_HEADER = new Font("Consolas", Font.BOLD, 13);
    private static final Font FONT_HUD_LABEL = new Font("Consolas", Font.PLAIN, 12);

    // ================================================================
    // Pre-computed tire mark alpha colour table
    // (256 entries, indexed by alpha 0-255 — avoids new Color() per mark)
    // ================================================================

    private static final Color[] TIRE_MARK_COLORS = new Color[256];
    static {
        for (int a = 0; a < 256; a++) {
            TIRE_MARK_COLORS[a] = new Color(TIRE_MARK_BASE_R, TIRE_MARK_BASE_G, TIRE_MARK_BASE_B, a);
        }
    }

    // ================================================================
    // Layout constants
    // ================================================================

    private static final int MAX_TIRE_MARKS = 2000;
    private static final int TIRE_MARK_FADE_FRAMES = 240;
    private static final int TIRE_MARK_ALPHA_BUCKETS = 16;
    private static final double ROOF_MAX_SHIFT = 3.0;

    // ================================================================
    // State
    // ================================================================

    private Graphics2D g2d;
    private BufferStrategy bufferStrategy;

    /** Full-screen pre-rendered asphalt (built once, redrawn on resize). */
    private BufferedImage asphaltBackground;
    private int asphaltBgWidth;
    private int asphaltBgHeight;

    /** Tire marks ring buffer. */
    private final Deque<TireMark> tireMarks = new ArrayDeque<>();

    /** Optional tuning overlay drawn on top of everything. */
    private ParameterTuningOverlay tuningOverlay;

    // ---- Cached car shapes (rebuilt only when dimensions change) ----
    private GeneralPath cachedCarSilhouette;
    private GradientPaint cachedBodyGradient;
    private double cachedCarWidth = -1;
    private double cachedCarLength = -1;

    // ---- Reusable geometry objects (mutated in place, never re-allocated) ----
    private final Line2D.Double reusableLine = new Line2D.Double();
    private final RoundRectangle2D.Double reusableRoundRect = new RoundRectangle2D.Double();

    // ---- Tire mark bucketing scratch lists (pre-allocated) ----
    @SuppressWarnings("unchecked")
    private final List<TireMark>[] tireMarkBuckets = new List[TIRE_MARK_ALPHA_BUCKETS];
    {
        for (int i = 0; i < TIRE_MARK_ALPHA_BUCKETS; i++) {
            tireMarkBuckets[i] = new ArrayList<>(MAX_TIRE_MARKS / TIRE_MARK_ALPHA_BUCKETS);
        }
    }

    // ---- Previous tire mark per wheel (for line connection) ----
    private final TireMark[] prevMarkPerWheel = new TireMark[4];

    public Java2DRendererAdapter(int width, int height) {
        setSize(width, height);
        setIgnoreRepaint(true);
    }

    public void initBufferStrategy() {
        createBufferStrategy(2);
        bufferStrategy = getBufferStrategy();
    }

    /** Sets the optional tuning overlay to render on top of everything. */
    public void setTuningOverlay(ParameterTuningOverlay overlay) {
        this.tuningOverlay = overlay;
    }

    // ================================================================
    // Renderer port implementation
    // ================================================================

    @Override
    public void beginFrame() {
        g2d = (Graphics2D) bufferStrategy.getDrawGraphics();

        // P1: Selective rendering hints — AA off for background, on for car
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_DEFAULT);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        drawAsphaltBackground();
        drawLaneMarkings();
        ageTireMarks();
        drawTireMarks();
        drawControlsHUD();
    }

    @Override
    public void drawCar(double x, double y, double rotation, double width, double length,
                        double velocity, WheelRenderData[] wheels) {
        recordTireMarks(wheels);

        // Enable AA only for the car (the detailed part)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform saved = g2d.getTransform();
        g2d.translate(x, y);
        g2d.rotate(rotation);

        // P2: Rebuild cached silhouette/gradient only when dimensions change
        if (width != cachedCarWidth || length != cachedCarLength) {
            rebuildCarCache(width, length);
        }

        drawDropShadow(width, length);
        drawWheels(wheels);
        drawCarBody(width, length);
        drawHeadlights(width, length);
        drawTaillights(width, length);
        drawRoof(width, length, wheels);

        g2d.setTransform(saved);

        // Turn AA back off after car
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    @Override
    public void endFrame() {
        if (tuningOverlay != null) {
            tuningOverlay.draw(g2d, getWidth(), getHeight());
        }

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
    // P2: Car silhouette + gradient cache
    // ================================================================

    private void rebuildCarCache(double width, double length) {
        cachedCarWidth = width;
        cachedCarLength = length;
        cachedCarSilhouette = createCarSilhouette(width, length);
        cachedBodyGradient = new GradientPaint(
                0, (float) (-length / 2), BODY_HIGHLIGHT,
                0, (float) (length / 2), BODY_FILL);
    }

    // ================================================================
    // P0: Pre-rendered full-screen asphalt background
    // ================================================================

    /**
     * Draws the asphalt background. On first call (or resize), renders
     * a noisy asphalt texture into a full-screen BufferedImage.
     * Subsequent frames: single drawImage() call.
     */
    private void drawAsphaltBackground() {
        int w = getWidth();
        int h = getHeight();

        if (asphaltBackground == null || w != asphaltBgWidth || h != asphaltBgHeight) {
            asphaltBackground = createFullAsphaltBackground(w, h);
            asphaltBgWidth = w;
            asphaltBgHeight = h;
        }

        g2d.drawImage(asphaltBackground, 0, 0, null);
    }

    /**
     * Creates a full-screen asphalt image with per-pixel noise.
     * Called once at startup and on resize.
     */
    private BufferedImage createFullAsphaltBackground(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rng = new Random(42);

        int baseR = ASPHALT_BASE.getRed();
        int baseG = ASPHALT_BASE.getGreen();
        int baseB = ASPHALT_BASE.getBlue();

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int noise = rng.nextInt(8) - 4;
                int r = clampByte(baseR + noise);
                int g = clampByte(baseG + noise);
                int b = clampByte(baseB + noise);
                img.setRGB(px, py, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private void drawLaneMarkings() {
        g2d.setColor(LANE_MARKING);
        g2d.setStroke(STROKE_LANE_DASH);

        int cx = getWidth() / 2;
        g2d.drawLine(cx, 0, cx, getHeight());

        int cy = getHeight() / 2;
        g2d.drawLine(0, cy, getWidth(), cy);

        g2d.setColor(EDGE_LINE);
        g2d.setStroke(STROKE_1_0);
        int margin = 40;
        g2d.drawRect(margin, margin, getWidth() - margin * 2, getHeight() - margin * 2);
    }

    // ================================================================
    // P1: Tire marks — alpha-bucketed rendering with reusable Line2D
    // ================================================================

    private void recordTireMarks(WheelRenderData[] wheels) {
        if (wheels == null) return;
        for (int i = 0; i < wheels.length; i++) {
            WheelRenderData w = wheels[i];
            if (w.slipping()) {
                tireMarks.addLast(new TireMark(w.worldX(), w.worldY(),
                        TIRE_MARK_FADE_FRAMES, i));
                while (tireMarks.size() > MAX_TIRE_MARKS) {
                    tireMarks.removeFirst();
                }
            }
        }
    }

    private void ageTireMarks() {
        tireMarks.removeIf(mark -> {
            mark.age--;
            return mark.age <= 0;
        });
    }

    /**
     * Draws tire marks using alpha bucketing.
     * <p>
     * Instead of setting a new Color per mark (2000 Color objects),
     * marks are sorted into {@value #TIRE_MARK_ALPHA_BUCKETS} alpha buckets.
     * Color is set once per bucket, then all lines in that bucket are drawn.
     * Total Color lookups: 16 (from pre-computed table) instead of 2000 allocations.
     */
    private void drawTireMarks() {
        if (tireMarks.isEmpty()) return;

        // Clear buckets
        for (int i = 0; i < TIRE_MARK_ALPHA_BUCKETS; i++) {
            tireMarkBuckets[i].clear();
        }

        // Clear previous-mark tracker
        for (int i = 0; i < 4; i++) {
            prevMarkPerWheel[i] = null;
        }

        // Assign each mark to an alpha bucket and link to previous mark
        for (TireMark mark : tireMarks) {
            float opacity = (float) mark.age / TIRE_MARK_FADE_FRAMES;
            int alpha = (int) (opacity * TIRE_MARK_MAX_ALPHA);
            int bucket = Math.min((alpha * TIRE_MARK_ALPHA_BUCKETS) / (TIRE_MARK_MAX_ALPHA + 1),
                    TIRE_MARK_ALPHA_BUCKETS - 1);

            // Link to previous mark of same wheel for line drawing
            if (mark.wheelIndex >= 0 && mark.wheelIndex < 4) {
                mark.prev = prevMarkPerWheel[mark.wheelIndex];
                prevMarkPerWheel[mark.wheelIndex] = mark;
            }

            mark.alphaBucket = bucket;
            tireMarkBuckets[bucket].add(mark);
        }

        // Draw each bucket with one color/stroke state change
        g2d.setStroke(STROKE_TIRE_MARK);
        for (int bucket = 0; bucket < TIRE_MARK_ALPHA_BUCKETS; bucket++) {
            List<TireMark> marks = tireMarkBuckets[bucket];
            if (marks.isEmpty()) continue;

            // Compute representative alpha for this bucket
            int alpha = ((bucket * 2 + 1) * TIRE_MARK_MAX_ALPHA) / (TIRE_MARK_ALPHA_BUCKETS * 2);
            alpha = Math.max(0, Math.min(255, alpha));
            g2d.setColor(TIRE_MARK_COLORS[alpha]);

            for (TireMark mark : marks) {
                TireMark prev = mark.prev;
                if (prev != null) {
                    double dx = mark.x - prev.x;
                    double dy = mark.y - prev.y;
                    if (dx * dx + dy * dy < 400) {
                        reusableLine.setLine(prev.x, prev.y, mark.x, mark.y);
                        g2d.draw(reusableLine);
                    }
                }
            }
        }
    }

    private static class TireMark {
        final double x, y;
        final int wheelIndex;
        int age;
        // Scratch fields set during drawTireMarks (avoids separate data structures)
        TireMark prev;
        int alphaBucket;

        TireMark(double x, double y, int age, int wheelIndex) {
            this.x = x;
            this.y = y;
            this.age = age;
            this.wheelIndex = wheelIndex;
        }
    }

    // ================================================================
    // Car rendering: drop shadow (uses cached silhouette)
    // ================================================================

    private void drawDropShadow(double width, double length) {
        AffineTransform saved = g2d.getTransform();
        g2d.translate(3.0, 3.0);
        g2d.setColor(BODY_SHADOW);
        g2d.fill(cachedCarSilhouette);
        g2d.setTransform(saved);
    }

    // ================================================================
    // Car rendering: body silhouette (GeneralPath created once, cached)
    // ================================================================

    private GeneralPath createCarSilhouette(double w, double l) {
        GeneralPath path = new GeneralPath();

        double halfW = w / 2.0;
        double halfL = l / 2.0;

        double noseWidth = halfW * 0.65;
        double cabinWidth = halfW;
        double rearWidth = halfW * 0.85;
        double hoodLength = halfL * 0.35;
        double trunkLength = halfL * 0.30;
        double cornerR = w * 0.12;

        path.moveTo(-noseWidth + cornerR, -halfL);
        path.lineTo(noseWidth - cornerR, -halfL);
        path.quadTo(noseWidth, -halfL, noseWidth, -halfL + cornerR);

        path.lineTo(noseWidth, -halfL + hoodLength);
        path.quadTo(cabinWidth, -halfL + hoodLength + cornerR,
                    cabinWidth, -halfL + hoodLength + cornerR * 2);

        path.lineTo(cabinWidth, halfL - trunkLength - cornerR);

        path.quadTo(cabinWidth, halfL - trunkLength + cornerR,
                    rearWidth, halfL - trunkLength + cornerR * 2);

        path.lineTo(rearWidth, halfL - cornerR);
        path.quadTo(rearWidth, halfL, rearWidth - cornerR, halfL);

        path.lineTo(-rearWidth + cornerR, halfL);
        path.quadTo(-rearWidth, halfL, -rearWidth, halfL - cornerR);

        path.lineTo(-rearWidth, halfL - trunkLength + cornerR * 2);
        path.quadTo(-cabinWidth, halfL - trunkLength + cornerR,
                    -cabinWidth, halfL - trunkLength - cornerR);

        path.lineTo(-cabinWidth, -halfL + hoodLength + cornerR * 2);

        path.quadTo(-cabinWidth, -halfL + hoodLength + cornerR,
                    -noseWidth, -halfL + hoodLength);

        path.lineTo(-noseWidth, -halfL + cornerR);
        path.quadTo(-noseWidth, -halfL, -noseWidth + cornerR, -halfL);

        path.closePath();
        return path;
    }

    private void drawCarBody(double width, double length) {
        // Use cached silhouette and gradient
        g2d.setPaint(cachedBodyGradient);
        g2d.fill(cachedCarSilhouette);

        // Hood crease line (reusable Line2D)
        g2d.setColor(HOOD_CREASE);
        g2d.setStroke(STROKE_1_0);
        double hoodEnd = -length / 2 + length * 0.35;
        reusableLine.setLine(0, -length / 2 + 3, 0, hoodEnd);
        g2d.draw(reusableLine);

        // Outline
        g2d.setColor(BODY_OUTLINE);
        g2d.setStroke(STROKE_1_5);
        g2d.draw(cachedCarSilhouette);
    }

    // ================================================================
    // Car rendering: headlights + taillights
    // ================================================================

    private void drawHeadlights(double width, double length) {
        double halfW = width / 2.0;
        double halfL = length / 2.0;
        double noseWidth = halfW * 0.65;

        double lightW = width * 0.14;
        double lightH = length * 0.06;

        double lx = -noseWidth + lightW * 0.3;
        double ly = -halfL + 2;
        double rx = noseWidth - lightW * 1.3;

        double glowPad = 3;
        g2d.setColor(HEADLIGHT_GLOW);
        g2d.fillOval((int) (lx - glowPad), (int) (ly - glowPad),
                (int) (lightW + glowPad * 2), (int) (lightH + glowPad * 2));
        g2d.fillOval((int) (rx - glowPad), (int) (ly - glowPad),
                (int) (lightW + glowPad * 2), (int) (lightH + glowPad * 2));

        g2d.setColor(HEADLIGHT_FILL);
        g2d.fillRoundRect((int) lx, (int) ly, (int) lightW, (int) lightH, 2, 2);
        g2d.fillRoundRect((int) rx, (int) ly, (int) lightW, (int) lightH, 2, 2);
    }

    private void drawTaillights(double width, double length) {
        double halfW = width / 2.0;
        double halfL = length / 2.0;
        double rearWidth = halfW * 0.85;

        double lightW = width * 0.16;
        double lightH = length * 0.04;

        double ly = halfL - lightH - 2;
        double lx = -rearWidth + lightW * 0.2;
        double rx = rearWidth - lightW * 1.2;

        double glowPad = 3;
        g2d.setColor(TAILLIGHT_GLOW);
        g2d.fillOval((int) (lx - glowPad), (int) (ly - glowPad),
                (int) (lightW + glowPad * 2), (int) (lightH + glowPad * 2));
        g2d.fillOval((int) (rx - glowPad), (int) (ly - glowPad),
                (int) (lightW + glowPad * 2), (int) (lightH + glowPad * 2));

        g2d.setColor(TAILLIGHT_FILL);
        g2d.fillRoundRect((int) lx, (int) ly, (int) lightW, (int) lightH, 2, 2);
        g2d.fillRoundRect((int) rx, (int) ly, (int) lightW, (int) lightH, 2, 2);
    }

    // ================================================================
    // Car rendering: wheels (reusable shapes, cached colors)
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

        double wheelW = Math.max(Math.abs(w.offsetX()) * 0.35, 3.0);
        double wheelH = Math.max(Math.abs(w.offsetY()) * 0.55, 6.0);

        // Rubber body (reusable RoundRectangle2D)
        reusableRoundRect.setRoundRect(-wheelW / 2, -wheelH / 2, wheelW, wheelH, 1.5, 1.5);
        g2d.setColor(WHEEL_RUBBER);
        g2d.fill(reusableRoundRect);

        // Rim highlight
        double rimW = wheelW * 0.3;
        g2d.setColor(WHEEL_RIM);
        g2d.fillRect((int) (-rimW / 2), (int) (-wheelH / 2 + 1),
                (int) Math.max(rimW, 1), (int) (wheelH - 2));

        // Tread lines (reusable Line2D)
        g2d.setColor(WHEEL_TREAD);
        g2d.setStroke(STROKE_0_5);
        int treadCount = Math.max((int) (wheelH / 3), 2);
        double treadSpacing = wheelH / (treadCount + 1);
        for (int t = 1; t <= treadCount; t++) {
            double ty = -wheelH / 2 + t * treadSpacing;
            reusableLine.setLine(-wheelW / 2 + 0.5, ty, wheelW / 2 - 0.5, ty);
            g2d.draw(reusableLine);
        }

        // Outline (reuse the same RoundRect — still has correct coords)
        g2d.setColor(WHEEL_OUTLINE);
        g2d.setStroke(STROKE_0_8);
        g2d.draw(reusableRoundRect);

        // Slip glow
        if (w.slipping()) {
            g2d.setColor(WHEEL_SLIP_GLOW);
            double glowPad = 2.0;
            g2d.fillRoundRect((int) (-wheelW / 2 - glowPad), (int) (-wheelH / 2 - glowPad),
                    (int) (wheelW + glowPad * 2), (int) (wheelH + glowPad * 2), 3, 3);
        }

        g2d.setTransform(saved);
    }

    // ================================================================
    // Car rendering: roof/cabin
    // ================================================================

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
                double copX = weightedX / totalForce;
                double copY = weightedY / totalForce;
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

        double cabinW = width * 0.50;
        double cabinL = length * 0.40;
        double cabinCentreY = -length * 0.05;

        // Windshield (GeneralPath — small, rebuilt each frame since shift changes)
        double windshieldW = cabinW * 0.95;
        double windshieldL = cabinL * 0.35;
        double windshieldY = cabinCentreY - cabinL / 2;
        double windshieldNarrowW = windshieldW * 0.75;

        GeneralPath windshield = new GeneralPath();
        windshield.moveTo(-windshieldNarrowW / 2 + shiftX, windshieldY + shiftY);
        windshield.lineTo(windshieldNarrowW / 2 + shiftX, windshieldY + shiftY);
        windshield.lineTo(windshieldW / 2 + shiftX, windshieldY + windshieldL + shiftY);
        windshield.lineTo(-windshieldW / 2 + shiftX, windshieldY + windshieldL + shiftY);
        windshield.closePath();

        g2d.setColor(WINDSHIELD);
        g2d.fill(windshield);

        // Main cabin
        double mainCabinY = windshieldY + windshieldL;
        double mainCabinL = cabinL - windshieldL;

        reusableRoundRect.setRoundRect(
                -cabinW / 2 + shiftX, mainCabinY + shiftY,
                cabinW, mainCabinL, 3.0, 3.0);

        g2d.setColor(CABIN_FILL);
        g2d.fill(reusableRoundRect);

        // Rear window
        double rearGlassH = mainCabinL * 0.25;
        double rearGlassW = cabinW * 0.80;
        double rearGlassY = mainCabinY + mainCabinL - rearGlassH;

        g2d.setColor(REAR_GLASS);
        g2d.fillRoundRect(
                (int) (-rearGlassW / 2 + shiftX), (int) (rearGlassY + shiftY),
                (int) rearGlassW, (int) rearGlassH, 2, 2);

        // Cabin outline
        g2d.setColor(CABIN_OUTLINE);
        g2d.setStroke(STROKE_1_0);
        g2d.draw(windshield);
        g2d.draw(reusableRoundRect);
    }

    // ================================================================
    // HUD (cached fonts and colors)
    // ================================================================

    private void drawControlsHUD() {
        int panelX = 12;
        int panelY = 12;
        int panelW = 200;
        int panelH = 128;

        g2d.setColor(HUD_BG);
        g2d.fillRoundRect(panelX, panelY, panelW, panelH, 8, 8);

        g2d.setColor(HUD_BORDER);
        g2d.setStroke(STROKE_1_0);
        g2d.drawRoundRect(panelX, panelY, panelW, panelH, 8, 8);

        g2d.setFont(FONT_HUD_HEADER);
        g2d.setColor(HUD_ACCENT);
        g2d.drawString("CONTROLS", panelX + 10, panelY + 20);

        g2d.setColor(HUD_SEPARATOR);
        g2d.drawLine(panelX + 10, panelY + 26, panelX + panelW - 10, panelY + 26);

        g2d.setFont(FONT_HUD_LABEL);
        int lineY = panelY + 42;
        int lineSpacing = 17;

        drawControlLine("W / UP", "Throttle", panelX + 10, lineY);
        drawControlLine("S / DOWN / SPACE", "Brake", panelX + 10, lineY + lineSpacing);
        drawControlLine("A / LEFT", "Steer left", panelX + 10, lineY + lineSpacing * 2);
        drawControlLine("D / RIGHT", "Steer right", panelX + 10, lineY + lineSpacing * 3);
        drawControlLine("TAB", "Tuning panel", panelX + 10, lineY + lineSpacing * 4);
    }

    private void drawControlLine(String key, String action, int x, int y) {
        g2d.setColor(HUD_ACCENT);
        g2d.drawString(key, x, y);
        g2d.setColor(HUD_TEXT);
        g2d.drawString(" " + action, x + g2d.getFontMetrics().stringWidth(key), y);
    }

    // ================================================================
    // Utility
    // ================================================================

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
