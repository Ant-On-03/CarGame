package com.game.adapters;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.game.adapters.terrain.LibGDXTerrainRenderer;
import com.game.ports.Renderer;
import com.game.ports.WheelRenderData;

import java.util.ArrayDeque;
import java.util.Deque;

public class LibGDXRendererAdapter implements Renderer {

    private OrthographicCamera camera;
    private OrthographicCamera hudCamera;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch batch;
    private BitmapFont font;

    private ShaderProgram terrainShader;
    private LibGDXTerrainRenderer terrainRenderer;

    private static final Color BODY_FILL = new Color(25/255f, 60/255f, 120/255f, 1f);
    private static final Color CABIN_FILL = new Color(20/255f, 20/255f, 25/255f, 0.8f);
    private static final Color WHEEL_RUBBER = new Color(30/255f, 30/255f, 30/255f, 1f);
    private static final Color WHEEL_SLIP = new Color(1f, 140/255f, 40/255f, 0.6f);
    private static final Color HUD_BG = new Color(15/255f, 15/255f, 20/255f, 0.8f);

    private double pixelsPerMeter = 8.0;
    private double cameraWorldX;
    private double cameraWorldY;

    private final Deque<TireMark> tireMarks = new ArrayDeque<>();
    private static final int MAX_TIRE_MARKS = 2000;
    private static final int TIRE_MARK_FADE_FRAMES = 240;

    public LibGDXRendererAdapter() {
        camera = new OrthographicCamera();
        hudCamera = new OrthographicCamera();
        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();

        // Load the Shader
        ShaderProgram.pedantic = false;
        terrainShader = new ShaderProgram(Gdx.files.internal("terrain.vert"), Gdx.files.internal("terrain.frag"));
        if (!terrainShader.isCompiled()) {
            Gdx.app.error("ShaderError", "Failed to compile terrain shader:\n" + terrainShader.getLog());
        }
    }

    public void setTerrainRenderer(LibGDXTerrainRenderer renderer) {
        this.terrainRenderer = renderer;
    }

    @Override
    public void setCamera(double worldX, double worldY, double pixelsPerMeter) {
        this.cameraWorldX = worldX;
        this.cameraWorldY = worldY;
        this.pixelsPerMeter = pixelsPerMeter;

        camera.setToOrtho(true, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set((float)(cameraWorldX * pixelsPerMeter), (float)(cameraWorldY * pixelsPerMeter), 0);
        camera.update();

        hudCamera.setToOrtho(true, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        hudCamera.update();
    }

    @Override
    public void beginFrame() {
        // Clear with the atmospheric shadow color so the edges blend perfectly
        Gdx.gl.glClearColor(0.1f, 0.12f, 0.2f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // 1. Draw the 3D Terrain
        drawTerrainGPU();

        // 2. Lock the tire marks and car to the correct altitude!
        if (terrainRenderer != null) {
            // Find the exact height of the ground under the center of the camera
            double elevation = terrainRenderer.getElevationAt(cameraWorldX, cameraWorldY);

            int maxLayers = terrainRenderer.getRenderConfig().maxLayers();
            float parallaxScale = (float) terrainRenderer.getRenderConfig().parallaxScaleFactor();

            // Map the elevation (-100 to 100) to our specific 3D layer (0 to maxLayers)
            float currentLayer = (float) (((elevation + 100.0) / 200.0) * maxLayers);

            // Clamp it safely
            currentLayer = Math.max(0, Math.min(maxLayers - 1, currentLayer));

            // Zoom the camera to match the altitude of the road perfectly
            camera.zoom = 1.0f / (1.0f + (currentLayer * parallaxScale));
            camera.update();
        }

        // 3. Apply the altitude-adjusted camera to the ShapeRenderer
        shapeRenderer.setProjectionMatrix(camera.combined);

        // Now when we draw the tire marks (and later the car), they will stick to the road perfectly!
        ageTireMarks();
        drawTireMarks();
    }

    @Override
    public void drawCar(double x, double y, double rotation, double width, double length, double velocity, WheelRenderData[] wheels) {
        recordTireMarks(wheels);
        Matrix4 savedMatrix = shapeRenderer.getTransformMatrix().cpy();

        shapeRenderer.translate((float)x, (float)y, 0);
        shapeRenderer.rotate(0, 0, 1, (float)Math.toDegrees(rotation));

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        if (wheels != null) {
            for (WheelRenderData w : wheels) {
                drawSingleWheel(w);
            }
        }

        shapeRenderer.setColor(BODY_FILL);
        shapeRenderer.rect((float)(-width / 2), (float)(-length / 2), (float)width, (float)length);

        shapeRenderer.setColor(CABIN_FILL);
        shapeRenderer.rect((float)(-width / 2.5), (float)(-length / 4), (float)(width * 0.8), (float)(length * 0.5));

        shapeRenderer.end();
        shapeRenderer.setTransformMatrix(savedMatrix);
    }

    @Override
    public void endFrame() {
        batch.setProjectionMatrix(hudCamera.combined);
        shapeRenderer.setProjectionMatrix(hudCamera.combined);
        drawControlsHUD();
    }

    @Override
    public int getCanvasWidth() { return Gdx.graphics.getWidth(); }

    @Override
    public int getCanvasHeight() { return Gdx.graphics.getHeight(); }

    // ================================================================
    // GPU Terrain Rendering
    // ================================================================

    private void drawTerrainGPU() {
        if (terrainRenderer == null) return;

        double ppm = pixelsPerMeter;
        double chunkSize = terrainRenderer.getRenderConfig().chunkSizeMetres();

        float screenChunkPx = (float) (chunkSize * ppm);

        int maxLayers = terrainRenderer.getRenderConfig().maxLayers();
        float parallaxScale = (float) terrainRenderer.getRenderConfig().parallaxScaleFactor();

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();

        double viewRadiusX = (screenW / 2.0) / ppm + chunkSize;
        double viewRadiusY = (screenH / 2.0) / ppm + chunkSize;

        int minChunkX = (int) Math.floor((cameraWorldX - viewRadiusX) / chunkSize);
        int maxChunkX = (int) Math.floor((cameraWorldX + viewRadiusX) / chunkSize);
        int minChunkY = (int) Math.floor((cameraWorldY - viewRadiusY) / chunkSize);
        int maxChunkY = (int) Math.floor((cameraWorldY + viewRadiusY) / chunkSize);

        batch.setShader(terrainShader);

        terrainShader.bind();

        // --- GLOBAL LIGHTING ---

        // Lower sun angle for stronger terrain shadows
        terrainShader.setUniformf("u_lightDir", 0.35f, -0.35f, 0.55f);

        // Warmer sunlight (golden hour tone)
        terrainShader.setUniformf("u_lightColor", 1.0f, 0.82f, 0.60f);

        // Stronger warm outline for slope readability
        terrainShader.setUniformf("u_outlineColor", 0.12f, 0.09f, 0.06f);
        terrainShader.setUniformf("u_outlineThickness", 1.4f);

        float baseTexelSize = 1.0f / terrainRenderer.getRenderConfig().chunkSizePixels();

        for (int z = 0; z < maxLayers; z++) {

            float layerScale = 1.0f + (z * parallaxScale);

            camera.zoom = 1.0f / layerScale;
            camera.update();

            batch.setProjectionMatrix(camera.combined);
            batch.begin();

            terrainShader.setUniformf("u_targetBand", (float) z);
            terrainShader.setUniformf("u_totalBands", (float) maxLayers);

            // Softer atmospheric depth
            float layerDepth = ((float) z / (float) maxLayers) * 0.45f;
            terrainShader.setUniformf("u_layerDepth", layerDepth);

            float texelSize = baseTexelSize * layerScale;
            terrainShader.setUniformf("u_texelSize", texelSize, texelSize);

            for (int cy = minChunkY; cy <= maxChunkY; cy++) {
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {

                    Texture chunkTex = terrainRenderer.getChunkTexture(cx, cy);

                    if (chunkTex != null) {
                        float chunkWorldPxX = (float) (cx * chunkSize * ppm);
                        float chunkWorldPxY = (float) (cy * chunkSize * ppm);

                        batch.draw(chunkTex, chunkWorldPxX, chunkWorldPxY, screenChunkPx, screenChunkPx);
                    }
                }
            }

            batch.end();
        }

        camera.zoom = 1f;
        camera.update();
    }



    // ================================================================
    // Helpers (Tire Marks, Wheels, HUD)
    // ================================================================

    private void recordTireMarks(WheelRenderData[] wheels) {
        if (wheels == null) return;
        for (int i = 0; i < wheels.length; i++) {
            if (wheels[i].slipping()) {
                tireMarks.addLast(new TireMark(wheels[i].worldX(), wheels[i].worldY(), TIRE_MARK_FADE_FRAMES, i));
                while (tireMarks.size() > MAX_TIRE_MARKS) { tireMarks.removeFirst(); }
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
                float alpha = (float) mark.age / TIRE_MARK_FADE_FRAMES;
                shapeRenderer.setColor(0.1f, 0.1f, 0.1f, alpha * 0.6f);
                shapeRenderer.line((float)prev.x, (float)prev.y, (float)mark.x, (float)mark.y);
            }
            prevMarks[mark.wheelIndex] = mark;
        }
        shapeRenderer.end();
    }

    private void drawSingleWheel(WheelRenderData w) {
        Matrix4 wheelSaved = shapeRenderer.getTransformMatrix().cpy();
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

    private void drawControlsHUD() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(HUD_BG);
        shapeRenderer.rect(12, 12, 200, 128);
        shapeRenderer.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "CONTROLS", 22, 32);
        font.draw(batch, "W/A/S/D - Drive", 22, 60);
        font.draw(batch, "TAB - Tuning Panel", 22, 80);
        batch.end();
    }

    private static class TireMark {
        final double x, y;
        final int wheelIndex;
        int age;
        TireMark(double x, double y, int age, int wheelIndex) {
            this.x = x; this.y = y; this.age = age; this.wheelIndex = wheelIndex;
        }
    }
}