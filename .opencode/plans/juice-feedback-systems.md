# Player Feedback ("Juice") Systems — Implementation Plan

## Overview

Three interconnected feedback systems to make open-world exploration feel satisfying:
1. **Terrain-Specific Visual Feedback** — particles & surface-colored trails
2. **Camera Juice** — speed zoom + rough terrain shake
3. **Discoverable POIs** — boost pads & mud pits

**4 new files, 11 modified files. Zero external dependencies.**

---

## Foundation Changes (shared by all three systems)

### `SurfaceType.java` — add `roughness()` + 3rd constructor param

Update enum constants to include roughness (3rd arg):
```java
TARMAC(1.0, 0.0, 0.0),
DIRT(0.55, 30.0, 0.3),
GRAVEL(0.65, 20.0, 0.7),
MUD(0.35, 80.0, 0.5),
ICE(0.12, 0.0, 0.0),
SAND(0.45, 100.0, 0.4);
```

Add `roughness` field + getter:
```java
private final double roughness;
// In constructor: this.roughness = roughness;
public double roughness() { return roughness; }
```

### `Wheel.java` — add `surfaceType` field

```java
private SurfaceType surfaceType = SurfaceType.TARMAC;

public SurfaceType getSurfaceType() { return surfaceType; }
public void setSurfaceType(SurfaceType surfaceType) { this.surfaceType = surfaceType; }
```

### `WheelRenderData.java` — add 8th field

```java
import com.game.domain.SurfaceType;

public record WheelRenderData(
    double offsetX, double offsetY,
    double worldX, double worldY,
    double steeringAngle, double normalForce,
    boolean slipping,
    SurfaceType surfaceType    // NEW
) {}
```

### Physics Strategies — store surface on Wheel

Both `SimulationHandlingStrategy.java` and `ArcadeMayhemStrategy.java` already call `terrain.getSurfaceAt(wheelWorldX, wheelWorldY)` per wheel. Add `wheel.setSurfaceType(surface)` at that point.

### `GameLoop.java` — pass surface when building WheelRenderData

In the `render()` method, add `w.getSurfaceType()` as the 8th arg to WheelRenderData constructor.

---

## System 1: Terrain-Specific Visual Feedback

### NEW: `ParticleManager.java` (adapters package)

Fixed-size particle pool (512 max). Zero allocation during gameplay.

```java
public class ParticleManager {
    private static final int MAX_PARTICLES = 512;

    // Particle inner class
    static class Particle {
        double x, y;           // world pixels
        double vx, vy;        // velocity pixels/s
        double age, lifetime;  // seconds
        double size, growthRate;
        int r, g, b;
        int shape;             // 0=circle, 1=square
        boolean alive;
    }

    private final Particle[] pool;
    private final Random rng = new Random(123);

    // Emission methods per surface type
    void emitDirt(double wx, double wy, double carAngle, double speed) { ... }
    void emitGravel(double wx, double wy, double carAngle, double speed) { ... }
    void emitSand(double wx, double wy, double carAngle, double speed) { ... }
    void emitBoostBurst(double wx, double wy) { ... }

    void update(double dt) { /* age, move, kill expired */ }
    void draw(Graphics2D g2d) { /* alpha-bucketed rendering */ }
}
```

**Surface emission rules (called from Java2DRendererAdapter.recordTireMarks):**
- Tarmac: No particles
- Dirt: Brown/tan circles, 0.4s lifetime, emit rate ~ speed/10 per wheel per frame
- Gravel: Grey dust + occasional larger "rock" squares, 0.3s lifetime
- Mud: No particles (wet)
- Ice: No particles
- Sand: Tan/yellow circles, similar to dirt

### `Java2DRendererAdapter.java` — surface-dependent changes

**Tire mark colors** (replace single black with per-surface):
```java
private static final Map<SurfaceType, Color[]> TIRE_MARK_COLORS_BY_SURFACE = ...;
// TARMAC: dark grey/black
// DIRT: brown
// GRAVEL: grey-brown
// MUD: dark brown
// ICE: white/light-blue at 50% base alpha
// SAND: tan
```

**Mud always-trail**: When surface is MUD, record tire marks even when `slipping=false`. Use doubled line width.

**TireMark struct**: Add `SurfaceType surface` field to determine color during rendering.

**Integration**: Construct `ParticleManager` in adapter constructor. Call `update(dt)` and `draw(g2d)` in `beginFrame()`. Call `emit*()` in `recordTireMarks()` based on wheel surface.

**dt for particles**: `beginFrame()` doesn't currently receive dt. Options:
- Track wall-clock time in the adapter (System.nanoTime delta)
- Pass dt through setCamera or a new method
- Simplest: adapter tracks its own frame time internally

---

## System 2: Camera Juice

### `Camera.java` — add zoom + shake

New fields:
```java
// Speed zoom
private static final double MAX_PPM = 8.0;
private static final double MIN_PPM = 5.0;
private static final double ZOOM_MAX_SPEED = 40.0; // m/s
private static final double ZOOM_SMOOTHING = 3.0;
private double currentPpm = MAX_PPM;
private double zoomImpulse = 0.0;
private static final double ZOOM_IMPULSE_DECAY = 6.0;

// Screen shake
private static final double SHAKE_SCALE = 0.08;
private static final double MAX_SHAKE_PX = 3.0;
private double shakeOffsetX = 0.0;
private double shakeOffsetY = 0.0;
private final Random shakeRng = new Random();
```

Update signature:
```java
public void update(double carX, double carY, double velX, double velY,
                   double surfaceRoughness, double dt)
```

In update():
```java
// Speed zoom
double speed = Math.sqrt(velX*velX + velY*velY);
double t = Math.min(speed / ZOOM_MAX_SPEED, 1.0);
double targetPpm = MAX_PPM - (MAX_PPM - MIN_PPM) * t;
double zoomAlpha = 1.0 - Math.exp(-ZOOM_SMOOTHING * dt);
currentPpm += (targetPpm - currentPpm) * zoomAlpha;

// Zoom impulse decay
zoomImpulse *= Math.exp(-ZOOM_IMPULSE_DECAY * dt);
if (zoomImpulse < 0.01) zoomImpulse = 0.0;

// Screen shake
double intensity = Math.min(speed * surfaceRoughness * SHAKE_SCALE, MAX_SHAKE_PX);
shakeOffsetX = (shakeRng.nextDouble() * 2 - 1) * intensity;
shakeOffsetY = (shakeRng.nextDouble() * 2 - 1) * intensity;
```

New methods:
```java
public double getEffectivePpm() { return Math.max(currentPpm - zoomImpulse, 2.0); }
public double getShakeX() { return shakeOffsetX; }
public double getShakeY() { return shakeOffsetY; }
public void applyZoomImpulse(double magnitude) { zoomImpulse += magnitude; }
```

### `GameLoop.java` — use dynamic zoom + shake

- Accept `TerrainProvider` in constructor (new parameter)
- In `run()`, after camera update, query terrain:
  ```java
  SurfaceType surface = terrain.getSurfaceAt(car.getPosition().x(), car.getPosition().y());
  camera.update(carX, carY, vel.x(), vel.y(), surface.roughness(), elapsed);
  ```
- In `render()`:
  ```java
  double ppm = camera.getEffectivePpm();
  renderer.setCamera(
      camera.getX() + camera.getShakeX() / ppm,
      camera.getY() + camera.getShakeY() / ppm,
      ppm
  );
  ```

### `Main.java` — pass terrainGenerator to GameLoop constructor

---

## System 3: Points of Interest

### NEW: `POIType.java` (domain package)

```java
public enum POIType {
    SPEED_BOOST(8.0, 4.0),
    MUD_PIT(10.0, 10.0);

    private final double defaultWidth;
    private final double defaultHeight;
    // constructor + getters
}
```

### NEW: `PointOfInterest.java` (domain package)

```java
public record PointOfInterest(
    double x, double y,
    double width, double height,
    POIType type
) {
    public boolean containsPoint(double px, double py) {
        return px >= x - width/2 && px <= x + width/2
            && py >= y - height/2 && py <= y + height/2;
    }
}
```

### NEW: `POIProvider.java` (ports package)

```java
public interface POIProvider {
    List<PointOfInterest> getPOIsNear(double worldX, double worldY, double radius);
}
```

### NEW: `POIInteractionService.java` (domain/physics package)

```java
public class POIInteractionService {
    private static final double BOOST_IMPULSE = 30.0;  // m/s added
    private static final double MUD_PIT_DRAG = 0.96;   // velocity multiplier per tick

    public PointOfInterest checkCollision(Car car, List<PointOfInterest> pois) {
        for (PointOfInterest poi : pois) {
            if (poi.containsPoint(car.getPosition().x(), car.getPosition().y())) {
                return poi;
            }
        }
        return null;
    }

    public void applyEffect(Car car, PointOfInterest poi) {
        switch (poi.type()) {
            case SPEED_BOOST -> {
                Vector2 boost = car.forwardVector().multiply(BOOST_IMPULSE);
                car.setVelocity(car.getVelocity().add(boost));
            }
            case MUD_PIT -> {
                car.setVelocity(car.getVelocity().multiply(MUD_PIT_DRAG));
            }
        }
    }
}
```

### `ProceduralTerrainGenerator.java` — POI generation

- Implement `POIProvider` interface
- `TerrainChunk` gets a `List<PointOfInterest> pois` field
- During chunk generation, evaluate `poiNoise = valueNoise2D(chunkX * 0.7 + 500, chunkY * 0.7 + 500)`
- If `poiNoise > 0.92`: spawn POI at chunk centre
  - Type determined by dominant surface in chunk: TARMAC area → SPEED_BOOST, DIRT/MUD → MUD_PIT
- `getPOIsNear()`: iterate cached chunks within radius, collect POIs

### `Java2DRendererAdapter.java` — POI rendering

In `beginFrame()`, after terrain chunks and before tire marks:
- Query nearby POIs from terrain generator
- For each visible POI:
  - SPEED_BOOST: Bright cyan filled rectangle with yellow chevron arrows
  - MUD_PIT: Dark brown filled rectangle with darker border

### `GameLoop.java` — POI interaction

- Accept `POIProvider` and `POIInteractionService` in constructor
- Each physics tick:
  ```java
  List<PointOfInterest> nearby = poiProvider.getPOIsNear(carX, carY, 20.0);
  PointOfInterest hit = poiService.checkCollision(car, nearby);
  if (hit != null && hit.type() == POIType.SPEED_BOOST) {
      poiService.applyEffect(car, hit);
      camera.applyZoomImpulse(2.0);
      // trigger particle burst via flag
  }
  if (hit != null && hit.type() == POIType.MUD_PIT) {
      poiService.applyEffect(car, hit);
  }
  ```
- Boost pad needs a cooldown to avoid re-triggering every tick while overlapping. Track `lastBoostPOI` and `boostCooldownTimer`.

### `Main.java` — wire POI services

```java
POIInteractionService poiService = new POIInteractionService();
// terrainGenerator already implements POIProvider
GameLoop gameLoop = new GameLoop(car, physics, inputAdapter, rendererAdapter,
                                 camera, terrainGenerator, poiService);
```

---

## Implementation Order

1. Foundation: SurfaceType, Wheel, WheelRenderData
2. Physics strategies: store surface on wheels
3. ParticleManager (new file)
4. Camera: zoom + shake
5. POI domain types (4 new files)
6. ProceduralTerrainGenerator: POI generation
7. Java2DRendererAdapter: particles, surface marks, POI rendering
8. GameLoop: wire everything
9. Main.java: wiring
10. Build verification

## Key Risks

- **WheelRenderData change breaks existing constructor calls** in GameLoop and Java2DRendererAdapter (need to add 8th arg everywhere)
- **Camera.update() signature change** breaks GameLoop call site
- **GameLoop constructor change** breaks Main.java
- **Performance**: 512-particle pool + alpha-bucketed rendering should be fine at 60fps. Monitor if gravel produces too many particles.
