package com.game.adapters;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.game.ports.Renderer;
import com.game.ports.WheelRenderData;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Hardware-accelerated Renderer port implementation using LibGDX (OpenGL).
 * * Performance-optimised:
 * - Geometric shapes are drawn in batches via GPU.
 * - Complex transformations are handled instantly by Matrix4 math.
 * - Terrain is prepped to be offloaded to a GLSL Fragment Shader,
 * completely eliminating the CPU overdraw bottleneck.
 */
public class LibGDXRendererAdapter implements Renderer {

    // ================================================================
    // LibGDX Rendering Tools
    // ================================================================
    private OrthographicCamera camera;
    private OrthographicCamera hudCamera;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch batch;
    private BitmapFont font;

    // Shader program for the "Ridiculous Layers" terrain parallax
    private ShaderProgram terrainShader;

    // ================================================================
    // Cached Colors (LibGDX Color format)
    // ================================================================
    private static final Color BODY_FILL = new Color(25/255f, 60/255f, 120/255f, 1f);
    private static final Color CABIN_FILL = new Color(20/255f, 20/255f, 25/255f, 0.8f);
    private static final Color WHEEL_RUBBER = new Color(30/255f, 30/255f, 30/255f, 1f);
    private static final Color WHEEL_SLIP = new Color(1f, 140/255f, 40/255f, 0.6f);
    private static final Color HUD_BG = new Color(15/255f, 15/255f, 20/255f, 0.8f);

    // ================================================================
    // State
    // ================================================================
    private double pixelsPerMeter = 8.0;
    private double cameraWorldX;
    private double cameraWorldY;

    // Tire marks ring buffer
    private final Deque<TireMark> tireMarks = new ArrayDeque<>();
    private static final int MAX_TIRE_MARKS = 2000;
    private static final int TIRE_MARK_FADE_FRAMES = 240;

    public LibGDXRendererAdapter() {
        // Initialize LibGDX components (Must be called on the GL thread)
        camera = new OrthographicCamera();
        hudCamera = new OrthographicCamera();
        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont(); // Default Arial font provided by LibGDX

        // Note: You would compile your terrain shader here
        // terrainShader = new ShaderProgram(Gdx.files.internal("terrain.vert"), Gdx.files.internal("terrain.frag"));
    }

    // ================================================================
    // Renderer port implementation
    // ================================================================

    @Override
    public void setCamera(double worldX, double worldY, double pixelsPerMeter) {
        this.cameraWorldX = worldX;
        this.cameraWorldY = worldY;
        this.pixelsPerMeter = pixelsPerMeter;

        // Update the main world camera
        camera.setToOrtho(true, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set((float)(cameraWorldX * pixelsPerMeter), (float)(cameraWorldY * pixelsPerMeter), 0);
        camera.update();

        // Update the static HUD camera
        hudCamera.setToOrtho(true, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        hudCamera.update();
    }

    @Override
    public void beginFrame() {
        // 1. Clear the screen (Hardware clear, vastly faster than drawing a background rect)
        Gdx.gl.glClearColor(30/255f, 30/255f, 35/255f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 2. Enable Alpha Blending for transparent elements (glass, tire marks)
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // 3. Draw Terrain using the GPU Shader (Replaces your 24-loop Java2D chunk drawing)
        drawTerrainGPU();

        // 4. Set ShapeRenderer to use the world camera for the car and tire marks
        shapeRenderer.setProjectionMatrix(camera.combined);

        ageTireMarks();
        drawTireMarks();
    }

    @Override
    public void drawCar(double x, double y, double rotation, double width, double length,
                        double velocity, WheelRenderData[] wheels) {

        recordTireMarks(wheels);

        // Save current transform matrix
        Matrix4 savedMatrix = shapeRenderer.getTransformMatrix().cpy();

        // Translate and rotate for the car body
        shapeRenderer.translate((float)x, (float)y, 0);
        shapeRenderer.rotate(0, 0, 1, (float)Math.toDegrees(rotation));

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Draw Wheels
        if (wheels != null) {
            for (WheelRenderData w : wheels) {
                drawSingleWheel(w);
            }
        }

        // Draw Car Body (Simplified as a rect for the adapter baseline)
        shapeRenderer.setColor(BODY_FILL);
        shapeRenderer.rect((float)(-width / 2), (float)(-length / 2), (float)width, (float)length);

        // Draw Cabin/Roof
        shapeRenderer.setColor(CABIN_FILL);
        shapeRenderer.rect((float)(-width / 2.5), (float)(-length / 4), (float)(width * 0.8), (float)(length * 0.5));

        shapeRenderer.end();

        // Restore original matrix
        shapeRenderer.setTransformMatrix(savedMatrix);
    }

    @Override
    public void endFrame() {
        // Switch to the static HUD camera for text/UI
        batch.setProjectionMatrix(hudCamera.combined);
        shapeRenderer.setProjectionMatrix(hudCamera.combined);

        drawControlsHUD();
    }

    @Override
    public int getCanvasWidth() {
        return Gdx.graphics.getWidth();
    }

    @Override
    public int getCanvasHeight() {
        return Gdx.graphics.getHeight();
    }

    // ================================================================
    // GPU Terrain Rendering
    // ================================================================

    private void drawTerrainGPU() {
        /*
         * THE GPU OPTIMIZATION:
         * Instead of iterating 24 layers on the CPU, you bind your ShaderProgram here.
         * The shader takes 1 elevation map texture, the camera position, and calculates
         * all 24 layers of parallax depth per-pixel directly on the graphics card.
         * * Example:
         * batch.setShader(terrainShader);
         * terrainShader.setUniformf("u_cameraPos", (float)cameraWorldX, (float)cameraWorldY);
         * terrainShader.setUniformi("u_parallaxLayers", 24);
         * batch.begin();
         * batch.draw(elevationMapTexture, ...);
         * batch.end();
         * batch.setShader(null);
         */
    }

    // ================================================================
    // Tire Marks
    // ================================================================

    private void recordTireMarks(WheelRenderData[] wheels) {
        if (wheels == null) return;
        for (int i = 0; i < wheels.length; i++) {
            if (wheels[i].slipping()) {
                tireMarks.addLast(new TireMark(wheels[i].worldX(), wheels[i].worldY(), TIRE_MARK_FADE_FRAMES, i));
                while (tireMarks.size() > MAX_TIRE_MARKS) {
                    tireMarks.removeFirst();
                }
            }
        }
    }

    private void ageTireMarks() {
        tireMarks.removeIf(mark -> --mark.age <= 0);
    }

    private void drawTireMarks() {
        if (tireMarks.isEmpty()) return;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        TireMark[] prevMarks = new TireMark[4];

        for (TireMark mark : tireMarks) {
            TireMark prev = prevMarks[mark.wheelIndex];
            if (prev != null) {
                // Calculate opacity based on age
                float alpha = (float) mark.age / TIRE_MARK_FADE_FRAMES;
                shapeRenderer.setColor(0.1f, 0.1f, 0.1f, alpha * 0.6f);

                // LibGDX handles batching these lines efficiently on the GPU
                shapeRenderer.line((float)prev.x, (float)prev.y, (float)mark.x, (float)mark.y);
            }
            prevMarks[mark.wheelIndex] = mark;
        }
        shapeRenderer.end();
    }

    // ================================================================
    // Wheels
    // ================================================================

    private void drawSingleWheel(WheelRenderData w) {
        Matrix4 wheelSaved = shapeRenderer.getTransformMatrix().cpy();

        // Translate to wheel offset relative to the car body
        shapeRenderer.translate((float)w.offsetX(), (float)w.offsetY(), 0);
        shapeRenderer.rotate(0, 0, 1, (float)Math.toDegrees(w.steeringAngle()));

        float wheelW = (float)Math.max(Math.abs(w.offsetX()) * 0.35, 3.0);
        float wheelH = (float)Math.max(Math.abs(w.offsetY()) * 0.55, 6.0);

        shapeRenderer.setColor(WHEEL_RUBBER);
        shapeRenderer.rect(-wheelW / 2, -wheelH / 2, wheelW, wheelH);

        if (w.slipping()) {
            shapeRenderer.setColor(WHEEL_SLIP);
            shapeRenderer.rect(-wheelW / 2 - 2, -wheelH / 2 - 2, wheelW + 4, wheelH + 4);
        }

        shapeRenderer.setTransformMatrix(wheelSaved);
    }

    // ================================================================
    // HUD
    // ================================================================

    private void drawControlsHUD() {
        // Draw the background box
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(HUD_BG);
        shapeRenderer.rect(12, 12, 200, 128);
        shapeRenderer.end();

        // Draw the text
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "CONTROLS", 22, 32);
        font.draw(batch, "W/A/S/D - Drive", 22, 60);
        font.draw(batch, "TAB - Tuning Panel", 22, 80);
        batch.end();
    }

    // Inner class for TireMarks
    private static class TireMark {
        final double x, y;
        final int wheelIndex;
        int age;

        TireMark(double x, double y, int age, int wheelIndex) {
            this.x = x;
            this.y = y;
            this.age = age;
            this.wheelIndex = wheelIndex;
        }
    }
}