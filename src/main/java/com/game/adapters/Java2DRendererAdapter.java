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

/**
 * Adapter that implements the {@link Renderer} port using Java2D.
 * <p>
 * Performance-optimised: all Fonts, Strokes, Colors, and reusable shapes
 * are pre-allocated as fields or static constants — zero per-frame
 * object allocation in the hot path.
 * <p>
 * The renderer uses a camera transform to convert world-pixel coordinates
 * to screen coordinates. Terrain chunks are drawn in world space, and the
 * camera translation centres the view on the car.
 */
public class Java2DRendererAdapter extends Canvas implements Renderer {

    // ================================================================
    // Cached colour constants (zero per-frame allocation)
    // ================================================================

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

    /** Tire marks ring buffer (world-pixel coordinates). */
    private final Deque<TireMark> tireMarks = new ArrayDeque<>();

    /** Optional tuning overlay drawn on top of everything. */
    private ParameterTuningOverlay tuningOverlay;

    /** Terrain generator for drawing chunks (set via setTerrainGenerator). */
    private ProceduralTerrainGenerator terrainGenerator;

    // ---- Camera state (set each frame via setCamera) ----
    private double cameraWorldX;   // camera centre in metres
    private double cameraWorldY;   // camera centre in metres
    private double pixelsPerMeter = 8.0;

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

    /** Sets the terrain generator for chunk rendering. */
    public void setTerrainGenerator(ProceduralTerrainGenerator generator) {
        this.terrainGenerator = generator;
    }

    // ================================================================
    // Renderer port implementation
    // ================================================================

    @Override
    public void setCamera(double worldX, double worldY, double pixelsPerMeter) {
        this.cameraWorldX = worldX;
        this.cameraWorldY = worldY;
        this.pixelsPerMeter = pixelsPerMeter;
    }

    @Override
    public void beginFrame() {
        g2d = (Graphics2D) bufferStrategy.getDrawGraphics();

        // P1: Selective rendering hints — AA off for background, on for car
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_DEFAULT);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        // Clear screen with dark background (visible if terrain hasn't loaded)
        g2d.setColor(new Color(30, 30, 35));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // Draw terrain chunks in world space
        drawTerrainChunks();

        // Apply camera transform for world-space rendering (tire marks, car)
        applyCameraTransform();

        ageTireMarks();
        drawTireMarks();
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
        // Reset transform for screen-space HUD rendering
        g2d.setTransform(new AffineTransform());

        drawControlsHUD();

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
    // Camera transform
    // ================================================================

    /**
     * Applies a translation so that the camera centre maps to screen centre.
     * After this, all draw calls use world-pixel coordinates.
     */
    private void applyCameraTransform() {
        double screenCentreX = getWidth() / 2.0;
        double screenCentreY = getHeight() / 2.0;
        double cameraPixelX = cameraWorldX * pixelsPerMeter;
        double cameraPixelY = cameraWorldY * pixelsPerMeter;

        g2d.translate(screenCentreX - cameraPixelX, screenCentreY - cameraPixelY);
    }

    // ================================================================
    // Terrain chunk rendering
    // ================================================================

    /**
     * Draws visible terrain chunks. Iterates over the grid of chunks
     * that overlap the current viewport and blits their pre-rendered images.
     */
    private void drawTerrainChunks() {
        if (terrainGenerator == null) return;

        double ppm = pixelsPerMeter;
        int screenW = getWidth();
        int screenH = getHeight();

        double camPxX = cameraWorldX * ppm;
        double camPxY = cameraWorldY * ppm;

        double viewLeftM = cameraWorldX - (screenW / 2.0) / ppm;
        double viewTopM = cameraWorldY - (screenH / 2.0) / ppm;
        double viewRightM = cameraWorldX + (screenW / 2.0) / ppm;
        double viewBottomM = cameraWorldY + (screenH / 2.0) / ppm;

        double chunkSize = ProceduralTerrainGenerator.CHUNK_SIZE_METRES;
        int chunkPx = ProceduralTerrainGenerator.CHUNK_SIZE_PIXELS;

        int minChunkX = (int) Math.floor(viewLeftM / chunkSize);
        int maxChunkX = (int) Math.floor(viewRightM / chunkSize);
        int minChunkY = (int) Math.floor(viewTopM / chunkSize);
        int maxChunkY = (int) Math.floor(viewBottomM / chunkSize);

        double offsetX = screenW / 2.0 - camPxX;
        double offsetY = screenH / 2.0 - camPxY;

        double screenCenterX = screenW / 2.0;
        double screenCenterY = screenH / 2.0;

        // Parallax intensity
        double parallaxScaleFactor = 0.05;

        // Draw terrain layers bottom → top
        for (int z = 0; z < ProceduralTerrainGenerator.MAX_POSSIBLE_LAYERS; z++) {

            double layerScale = 1.0 + (z * parallaxScaleFactor);
            java.awt.geom.AffineTransform savedTransform = g2d.getTransform();

            // Scale around screen center
            g2d.translate(screenCenterX, screenCenterY);
            g2d.scale(layerScale, layerScale);
            g2d.translate(-screenCenterX, -screenCenterY);

            for (int cy = minChunkY; cy <= maxChunkY; cy++) {
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {

                    BufferedImage[] layers = terrainGenerator.getChunkImages(cx, cy);
                    BufferedImage layerImage = layers[z];

                    // Skip empty layers
                    if (layerImage != null) {

                        double chunkWorldPxX = cx * chunkSize * ppm;
                        double chunkWorldPxY = cy * chunkSize * ppm;

                        int screenX = (int) (chunkWorldPxX + offsetX);
                        int screenY = (int) (chunkWorldPxY + offsetY);

                        g2d.drawImage(layerImage, screenX, screenY, chunkPx, chunkPx, null);
                    }
                }
            }

            g2d.setTransform(savedTransform);
        }

        // Evict distant chunks periodically
        terrainGenerator.evictDistantChunks(cameraWorldX, cameraWorldY);
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
    // P1: Tire marks — alpha-bucketed rendering with reusable Line2D
    // (now in world-pixel coordinates)
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
     * <p>
     * Tire marks are stored in world-pixel coordinates and drawn with the
     * camera transform already applied.
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

        // Calculate roof shift based on G-forces (weight transfer)
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

        // --- 3D CABIN GEOMETRY ---

        // 1. Base footprint (attached to the car body, NO shift)
        double baseW = width * 0.80;
        double baseL = length * 0.65;
        double baseY = -length * 0.05 - baseL / 2;

        double baseFLx = -baseW / 2;  double baseFLy = baseY;           // Front-Left
        double baseFRx =  baseW / 2;  double baseFRy = baseY;           // Front-Right
        double baseRLx = -baseW / 2;  double baseRLy = baseY + baseL;   // Rear-Left
        double baseRRx =  baseW / 2;  double baseRRy = baseY + baseL;   // Rear-Right

        // 2. Roof footprint (smaller for perspective, SHIFTED by physics)
        double roofW = baseW * 0.80; // Roof is 20% narrower than the base
        double roofL = baseL * 0.70; // Roof is 30% shorter than the base
        double roofY = baseY + (baseL - roofL) / 2;

        double roofFLx = -roofW / 2 + shiftX;  double roofFLy = roofY + shiftY;           // Front-Left
        double roofFRx =  roofW / 2 + shiftX;  double roofFRy = roofY + shiftY;           // Front-Right
        double roofRLx = -roofW / 2 + shiftX;  double roofRLy = roofY + roofL + shiftY;   // Rear-Left
        double roofRRx =  roofW / 2 + shiftX;  double roofRRy = roofY + roofL + shiftY;   // Rear-Right

        // --- DRAWING THE WINDOWS (The 3D sides) ---
        g2d.setColor(WINDSHIELD); // Bluish transparent glass

        // Front Windshield Polygon
        GeneralPath frontGlass = new GeneralPath();
        frontGlass.moveTo(baseFLx, baseFLy);
        frontGlass.lineTo(roofFLx, roofFLy);
        frontGlass.lineTo(roofFRx, roofFRy);
        frontGlass.lineTo(baseFRx, baseFRy);
        frontGlass.closePath();
        g2d.fill(frontGlass);

        // Rear Glass Polygon
        GeneralPath rearGlass = new GeneralPath();
        rearGlass.moveTo(baseRLx, baseRLy);
        rearGlass.lineTo(roofRLx, roofRLy);
        rearGlass.lineTo(roofRRx, roofRRy);
        rearGlass.lineTo(baseRRx, baseRRy);
        rearGlass.closePath();
        g2d.fill(rearGlass);

        // Left Window Polygon
        GeneralPath leftGlass = new GeneralPath();
        leftGlass.moveTo(baseFLx, baseFLy);
        leftGlass.lineTo(roofFLx, roofFLy);
        leftGlass.lineTo(roofRLx, roofRLy);
        leftGlass.lineTo(baseRLx, baseRLy);
        leftGlass.closePath();
        g2d.fill(leftGlass);

        // Right Window Polygon
        GeneralPath rightGlass = new GeneralPath();
        rightGlass.moveTo(baseFRx, baseFRy);
        rightGlass.lineTo(roofFRx, roofFRy);
        rightGlass.lineTo(roofRRx, roofRRy);
        rightGlass.lineTo(baseRRx, baseRRy);
        rightGlass.closePath();
        g2d.fill(rightGlass);

        // --- DRAWING THE ROOF (The Top Sprite) ---
        g2d.setColor(CABIN_FILL);
        reusableRoundRect.setRoundRect(
                -roofW / 2 + shiftX, roofY + shiftY,
                roofW, roofL, 2.0, 2.0
        );
        g2d.fill(reusableRoundRect);

        // --- DRAWING THE ARISTAS (Corner Lines) ---
        g2d.setColor(CABIN_OUTLINE);
        g2d.setStroke(STROKE_1_0);

        // Draw the 4 corner pillars connecting the roof to the body
        g2d.drawLine((int)baseFLx, (int)baseFLy, (int)roofFLx, (int)roofFLy);
        g2d.drawLine((int)baseFRx, (int)baseFRy, (int)roofFRx, (int)roofFRy);
        g2d.drawLine((int)baseRLx, (int)baseRLy, (int)roofRLx, (int)roofRLy);
        g2d.drawLine((int)baseRRx, (int)baseRRy, (int)roofRRx, (int)roofRRy);

        // Draw the outline around the base (bottom of the windows)
        g2d.drawLine((int)baseFLx, (int)baseFLy, (int)baseFRx, (int)baseFRy);
        g2d.drawLine((int)baseRLx, (int)baseRLy, (int)baseRRx, (int)baseRRy);
        g2d.drawLine((int)baseFLx, (int)baseFLy, (int)baseRLx, (int)baseRLy);
        g2d.drawLine((int)baseFRx, (int)baseFRy, (int)baseRRx, (int)baseRRy);

        // Draw the outline around the roof
        g2d.draw(reusableRoundRect);
    }


    // ================================================================
    // HUD (cached fonts and colors — drawn in screen space)
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
