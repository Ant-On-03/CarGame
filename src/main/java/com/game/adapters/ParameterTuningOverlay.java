package com.game.adapters;

import com.game.domain.Car;
import com.game.domain.CarConfig;
import com.game.domain.DriveType;
import com.game.domain.Tire;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.RoundRectangle2D;

/**
 * In-game parameter tuning overlay.
 * <p>
 * Press <b>Tab</b> to toggle the panel. While visible:
 * <ul>
 *   <li><b>Up/Down</b> — select parameter</li>
 *   <li><b>Left/Right</b> — decrease/increase value</li>
 *   <li><b>Shift + Left/Right</b> — large step adjustment</li>
 *   <li><b>R</b> — reset selected parameter to default</li>
 *   <li><b>Shift+R</b> — reset ALL parameters to defaults</li>
 * </ul>
 * <p>
 * The car continues to drive while the panel is open.
 * Changes take effect immediately — a new {@link CarConfig} record is
 * built and swapped into the {@link Car} on every adjustment.
 */
public class ParameterTuningOverlay implements KeyListener {

    // ================================================================
    // Tunable parameters
    // ================================================================

    /**
     * Each enum constant maps to one field of {@link CarConfig} (or its
     * nested {@link Tire} records), with display name, range, step size,
     * and format string.
     */
    enum TunableParam {
        // -- Vehicle --
        MASS("Mass", "kg", 200, 3000, 50, 10),
        WHEELBASE("Wheelbase", "m", 1.0, 5.0, 0.1, 0.02),
        TRACK_WIDTH("Track Width", "m", 0.8, 3.0, 0.1, 0.02),
        CG_HEIGHT("CG Height", "m", 0.1, 1.5, 0.05, 0.01),
        BODY_LENGTH("Body Length", "m", 2.0, 6.0, 0.25, 0.05),
        BODY_WIDTH("Body Width", "m", 1.0, 3.0, 0.1, 0.02),

        // -- Power --
        DRIVE_TYPE("Drive Type", "", 0, 2, 1, 1),
        ENGINE_FORCE("Engine Force", "N", 5000, 200000, 5000, 1000),
        BRAKE_FORCE("Brake Force", "N", 2000, 80000, 2000, 500),
        MAX_STEERING("Max Steering", "deg", 10, 90, 5, 1),

        // -- Drag/Resistance --
        DRAG_COEFF("Drag Coefficient", "", 0.0, 5.0, 0.1, 0.02),
        ROLLING_RES("Rolling Resistance", "N", 0, 500, 10, 2),
        ANGULAR_DAMP("Angular Damping", "", 0, 500, 10, 2),
        STEERING_ASSIST("Steering Assist", "N-m", 0, 50000, 1000, 200),

        // -- Front Tire --
        FRONT_FRICTION("Front Friction (mu)", "", 0.3, 3.0, 0.1, 0.02),
        FRONT_DYN_RATIO("Front Dynamic Ratio", "", 0.2, 1.0, 0.05, 0.01),
        FRONT_CORNERING("Front Cornering Stiff", "N/rad", 10000, 1000000, 10000, 2000),

        // -- Rear Tire --
        REAR_FRICTION("Rear Friction (mu)", "", 0.3, 3.0, 0.1, 0.02),
        REAR_DYN_RATIO("Rear Dynamic Ratio", "", 0.2, 1.0, 0.05, 0.01),
        REAR_CORNERING("Rear Cornering Stiff", "N/rad", 1000, 1000000, 5000, 1000);

        final String displayName;
        final String unit;
        final double min;
        final double max;
        final double bigStep;
        final double smallStep;

        TunableParam(String displayName, String unit,
                     double min, double max, double bigStep, double smallStep) {
            this.displayName = displayName;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.bigStep = bigStep;
            this.smallStep = smallStep;
        }

        /** Reads this parameter's current value from a CarConfig. */
        double get(CarConfig cfg) {
            return switch (this) {
                case MASS -> cfg.mass();
                case WHEELBASE -> cfg.wheelbase();
                case TRACK_WIDTH -> cfg.trackWidth();
                case CG_HEIGHT -> cfg.cgHeight();
                case BODY_LENGTH -> cfg.bodyLength();
                case BODY_WIDTH -> cfg.bodyWidth();
                case DRIVE_TYPE -> cfg.driveType().ordinal();
                case ENGINE_FORCE -> cfg.engineForce();
                case BRAKE_FORCE -> cfg.brakeForce();
                case MAX_STEERING -> Math.toDegrees(cfg.maxSteeringAngle());
                case DRAG_COEFF -> cfg.dragCoefficient();
                case ROLLING_RES -> cfg.rollingResistance();
                case ANGULAR_DAMP -> cfg.angularDamping();
                case STEERING_ASSIST -> cfg.steeringAssistTorque();
                case FRONT_FRICTION -> cfg.frontTire().frictionCoefficient();
                case FRONT_DYN_RATIO -> cfg.frontTire().dynamicFrictionRatio();
                case FRONT_CORNERING -> cfg.frontTire().corneringStiffness();
                case REAR_FRICTION -> cfg.rearTire().frictionCoefficient();
                case REAR_DYN_RATIO -> cfg.rearTire().dynamicFrictionRatio();
                case REAR_CORNERING -> cfg.rearTire().corneringStiffness();
            };
        }

        /** Builds a new CarConfig with this parameter changed to {@code value}. */
        CarConfig set(CarConfig cfg, double value) {
            double v = clamp(value, min, max);
            return switch (this) {
                case MASS -> withVehicle(cfg, v, cfg.wheelbase(), cfg.trackWidth(), cfg.cgHeight());
                case WHEELBASE -> withVehicle(cfg, cfg.mass(), v, cfg.trackWidth(), cfg.cgHeight());
                case TRACK_WIDTH -> withVehicle(cfg, cfg.mass(), cfg.wheelbase(), v, cfg.cgHeight());
                case CG_HEIGHT -> withVehicle(cfg, cfg.mass(), cfg.wheelbase(), cfg.trackWidth(), v);
                case BODY_LENGTH -> withBody(cfg, v, cfg.bodyWidth());
                case BODY_WIDTH -> withBody(cfg, cfg.bodyLength(), v);
                case DRIVE_TYPE -> withDriveType(cfg, DriveType.values()[(int) Math.round(v)]);
                case ENGINE_FORCE -> withDrive(cfg, v, cfg.brakeForce(), cfg.maxSteeringAngle());
                case BRAKE_FORCE -> withDrive(cfg, cfg.engineForce(), v, cfg.maxSteeringAngle());
                case MAX_STEERING -> withDrive(cfg, cfg.engineForce(), cfg.brakeForce(), Math.toRadians(v));
                case DRAG_COEFF -> withDrag(cfg, v, cfg.rollingResistance(), cfg.angularDamping(), cfg.steeringAssistTorque());
                case ROLLING_RES -> withDrag(cfg, cfg.dragCoefficient(), v, cfg.angularDamping(), cfg.steeringAssistTorque());
                case ANGULAR_DAMP -> withDrag(cfg, cfg.dragCoefficient(), cfg.rollingResistance(), v, cfg.steeringAssistTorque());
                case STEERING_ASSIST -> withDrag(cfg, cfg.dragCoefficient(), cfg.rollingResistance(), cfg.angularDamping(), v);
                case FRONT_FRICTION -> withFrontTire(cfg, v, cfg.frontTire().dynamicFrictionRatio(), cfg.frontTire().corneringStiffness());
                case FRONT_DYN_RATIO -> withFrontTire(cfg, cfg.frontTire().frictionCoefficient(), v, cfg.frontTire().corneringStiffness());
                case FRONT_CORNERING -> withFrontTire(cfg, cfg.frontTire().frictionCoefficient(), cfg.frontTire().dynamicFrictionRatio(), v);
                case REAR_FRICTION -> withRearTire(cfg, v, cfg.rearTire().dynamicFrictionRatio(), cfg.rearTire().corneringStiffness());
                case REAR_DYN_RATIO -> withRearTire(cfg, cfg.rearTire().frictionCoefficient(), v, cfg.rearTire().corneringStiffness());
                case REAR_CORNERING -> withRearTire(cfg, cfg.rearTire().frictionCoefficient(), cfg.rearTire().dynamicFrictionRatio(), v);
            };
        }

        String format(double value) {
            if (this == DRIVE_TYPE) {
                int index = (int) Math.round(clamp(value, 0, 2));
                return DriveType.values()[index].name();
            }
            if (bigStep >= 100) return String.format("%.0f", value);
            if (bigStep >= 1) return String.format("%.1f", value);
            return String.format("%.2f", value);
        }

        // ---- Config builders ----

        private static CarConfig withVehicle(CarConfig c, double mass, double wb, double tw, double cg) {
            return new CarConfig(mass, wb, tw, cg, c.bodyLength(), c.bodyWidth(),
                    c.maxSteeringAngle(), c.engineForce(), c.brakeForce(), c.driveType(),
                    c.dragCoefficient(), c.rollingResistance(), c.angularDamping(),
                    c.steeringAssistTorque(), c.frontTire(), c.rearTire());
        }

        private static CarConfig withBody(CarConfig c, double bodyLength, double bodyWidth) {
            return new CarConfig(c.mass(), c.wheelbase(), c.trackWidth(), c.cgHeight(),
                    bodyLength, bodyWidth, c.maxSteeringAngle(), c.engineForce(),
                    c.brakeForce(), c.driveType(), c.dragCoefficient(), c.rollingResistance(),
                    c.angularDamping(), c.steeringAssistTorque(), c.frontTire(), c.rearTire());
        }

        private static CarConfig withDrive(CarConfig c, double engine, double brake, double steer) {
            return new CarConfig(c.mass(), c.wheelbase(), c.trackWidth(), c.cgHeight(),
                    c.bodyLength(), c.bodyWidth(), steer, engine, brake, c.driveType(),
                    c.dragCoefficient(), c.rollingResistance(), c.angularDamping(),
                    c.steeringAssistTorque(), c.frontTire(), c.rearTire());
        }

        private static CarConfig withDriveType(CarConfig c, DriveType driveType) {
            return new CarConfig(c.mass(), c.wheelbase(), c.trackWidth(), c.cgHeight(),
                    c.bodyLength(), c.bodyWidth(), c.maxSteeringAngle(), c.engineForce(),
                    c.brakeForce(), driveType, c.dragCoefficient(), c.rollingResistance(),
                    c.angularDamping(), c.steeringAssistTorque(), c.frontTire(), c.rearTire());
        }

        private static CarConfig withDrag(CarConfig c, double drag, double roll, double angDamp, double assist) {
            return new CarConfig(c.mass(), c.wheelbase(), c.trackWidth(), c.cgHeight(),
                    c.bodyLength(), c.bodyWidth(), c.maxSteeringAngle(), c.engineForce(),
                    c.brakeForce(), c.driveType(), drag, roll, angDamp, assist,
                    c.frontTire(), c.rearTire());
        }

        private static CarConfig withFrontTire(CarConfig c, double mu, double dyn, double corn) {
            return new CarConfig(c.mass(), c.wheelbase(), c.trackWidth(), c.cgHeight(),
                    c.bodyLength(), c.bodyWidth(), c.maxSteeringAngle(), c.engineForce(),
                    c.brakeForce(), c.driveType(), c.dragCoefficient(), c.rollingResistance(),
                    c.angularDamping(), c.steeringAssistTorque(), new Tire(mu, dyn, corn), c.rearTire());
        }

        private static CarConfig withRearTire(CarConfig c, double mu, double dyn, double corn) {
            return new CarConfig(c.mass(), c.wheelbase(), c.trackWidth(), c.cgHeight(),
                    c.bodyLength(), c.bodyWidth(), c.maxSteeringAngle(), c.engineForce(),
                    c.brakeForce(), c.driveType(), c.dragCoefficient(), c.rollingResistance(),
                    c.angularDamping(), c.steeringAssistTorque(), c.frontTire(), new Tire(mu, dyn, corn));
        }

        private static double clamp(double v, double min, double max) {
            return Math.max(min, Math.min(max, v));
        }
    }

    // ================================================================
    // Section headers for grouping parameters visually
    // ================================================================

    private static final int[] SECTION_BEFORE = {0, 6, 10, 14, 17}; // indices where sections start
    private static final String[] SECTION_NAMES = {
            "VEHICLE", "POWER & STEERING", "DRAG & DAMPING", "FRONT TIRE", "REAR TIRE"
    };

    // ================================================================
    // Colours
    // ================================================================

    private static final Color PANEL_BG = new Color(10, 10, 15, 220);
    private static final Color PANEL_BORDER = new Color(60, 80, 100, 150);
    private static final Color HEADER_COLOR = new Color(80, 160, 220);
    private static final Color SECTION_COLOR = new Color(100, 140, 170, 200);
    private static final Color PARAM_NAME = new Color(180, 185, 195);
    private static final Color PARAM_VALUE = new Color(230, 235, 240);
    private static final Color PARAM_UNIT = new Color(100, 110, 130);
    private static final Color SELECTED_BG = new Color(40, 70, 110, 180);
    private static final Color SELECTED_NAME = new Color(255, 255, 255);
    private static final Color BAR_BG = new Color(30, 30, 40);
    private static final Color BAR_FILL = new Color(60, 140, 200);
    private static final Color BAR_FILL_SELECTED = new Color(80, 180, 255);
    private static final Color HINT_COLOR = new Color(120, 130, 150, 180);
    private static final Color CHANGED_INDICATOR = new Color(255, 180, 50);
    private static final Color SEPARATOR_COLOR = new Color(60, 80, 100, 80);
    private static final Color DEFAULT_MARKER_COLOR = new Color(255, 255, 255, 60);

    // ================================================================
    // Cached fonts and strokes (avoid per-frame allocation)
    // ================================================================

    private static final Font HEADER_FONT = new Font("Consolas", Font.BOLD, 14);
    private static final Font PARAM_FONT = new Font("Consolas", Font.PLAIN, 12);
    private static final Font SECTION_FONT = new Font("Consolas", Font.BOLD, 11);
    private static final Font HINT_FONT = new Font("Consolas", Font.PLAIN, 10);
    private static final BasicStroke BORDER_STROKE = new BasicStroke(1.0f);

    // ================================================================
    // Reusable geometry objects (mutated in place each frame)
    // ================================================================

    private final RoundRectangle2D.Double panelRect = new RoundRectangle2D.Double();

    // ================================================================
    // State
    // ================================================================

    private final Car car;
    private final CarConfig defaults;
    private final TunableParam[] params = TunableParam.values();

    private boolean visible = false;
    private int selectedIndex = 0;

    public ParameterTuningOverlay(Car car) {
        this.car = car;
        this.defaults = car.getConfig();
    }

    public boolean isVisible() {
        return visible;
    }

    // ================================================================
    // Key handling
    // ================================================================

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_TAB) {
            visible = !visible;
            return;
        }

        if (!visible) return;

        boolean shift = e.isShiftDown();

        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> selectedIndex = Math.max(0, selectedIndex - 1);
            case KeyEvent.VK_DOWN -> selectedIndex = Math.min(params.length - 1, selectedIndex + 1);
            case KeyEvent.VK_LEFT -> adjustSelected(shift ? -1 : -0.2);
            case KeyEvent.VK_RIGHT -> adjustSelected(shift ? 1 : 0.2);
            case KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> adjustSelected(shift ? -1 : -0.2);
            case KeyEvent.VK_EQUALS, KeyEvent.VK_ADD -> adjustSelected(shift ? 1 : 0.2);
            case KeyEvent.VK_R -> {
                if (shift) {
                    resetAll();
                } else {
                    resetSelected();
                }
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) { }

    @Override
    public void keyTyped(KeyEvent e) { }

    /**
     * Adjusts the selected parameter by {@code multiplier * bigStep}.
     * Fractional multipliers use the small step instead.
     */
    private void adjustSelected(double multiplier) {
        TunableParam param = params[selectedIndex];
        CarConfig cfg = car.getConfig();
        double current = param.get(cfg);

        double step = Math.abs(multiplier) >= 1.0
                ? param.bigStep * Math.signum(multiplier)
                : param.smallStep * Math.signum(multiplier);

        double newValue = current + step;
        CarConfig updated = param.set(cfg, newValue);
        car.setConfig(updated);
    }

    private void resetSelected() {
        TunableParam param = params[selectedIndex];
        double defaultValue = param.get(defaults);
        CarConfig updated = param.set(car.getConfig(), defaultValue);
        car.setConfig(updated);
    }

    private void resetAll() {
        car.setConfig(defaults);
    }

    // ================================================================
    // Rendering
    // ================================================================

    /**
     * Draws the tuning overlay onto the given Graphics2D context.
     * Called by the renderer adapter at the end of each frame.
     */
    public void draw(Graphics2D g2d, int canvasWidth, int canvasHeight) {
        if (!visible) return;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Panel dimensions
        int panelW = 360;
        int lineH = 20;
        int sectionH = 24;
        int headerH = 32;
        int hintH = 50;

        // Calculate total height
        int contentH = headerH + hintH;
        for (int i = 0; i < params.length; i++) {
            contentH += lineH;
            for (int s = 0; s < SECTION_BEFORE.length; s++) {
                if (SECTION_BEFORE[s] == i) {
                    contentH += sectionH;
                }
            }
        }
        int panelH = contentH + 10; // padding

        int panelX = canvasWidth - panelW - 16;
        int panelY = 12;

        // Background
        g2d.setColor(PANEL_BG);
        panelRect.setRoundRect(panelX, panelY, panelW, panelH, 10, 10);
        g2d.fill(panelRect);
        g2d.setColor(PANEL_BORDER);
        g2d.setStroke(BORDER_STROKE);
        g2d.draw(panelRect);

        int curY = panelY + 6;

        // Header
        g2d.setFont(HEADER_FONT);
        g2d.setColor(HEADER_COLOR);
        curY += 18;
        g2d.drawString("PHYSICS TUNING", panelX + 12, curY);

        // Separator
        curY += 8;
        g2d.setColor(SEPARATOR_COLOR);
        g2d.drawLine(panelX + 10, curY, panelX + panelW - 10, curY);
        curY += 6;

        // Parameters
        CarConfig cfg = car.getConfig();
        int barX = panelX + 220;
        int barW = 110;
        int barH = 10;

        for (int i = 0; i < params.length; i++) {
            // Section header
            for (int s = 0; s < SECTION_BEFORE.length; s++) {
                if (SECTION_BEFORE[s] == i) {
                    curY += 6;
                    g2d.setFont(SECTION_FONT);
                    g2d.setColor(SECTION_COLOR);
                    g2d.drawString(SECTION_NAMES[s], panelX + 12, curY + 12);
                    curY += sectionH - 6;
                }
            }

            TunableParam param = params[i];
            double value = param.get(cfg);
            double defaultValue = param.get(defaults);
            boolean isSelected = (i == selectedIndex);
            boolean isChanged = Math.abs(value - defaultValue) > param.smallStep * 0.1;

            // Selected background
            if (isSelected) {
                g2d.setColor(SELECTED_BG);
                g2d.fillRoundRect(panelX + 4, curY, panelW - 8, lineH, 4, 4);
            }

            // Name
            g2d.setFont(PARAM_FONT);
            g2d.setColor(isSelected ? SELECTED_NAME : PARAM_NAME);
            g2d.drawString(param.displayName, panelX + 12, curY + 14);

            // Changed indicator dot
            if (isChanged) {
                g2d.setColor(CHANGED_INDICATOR);
                g2d.fillOval(panelX + panelW - 20, curY + 6, 6, 6);
            }

            // Value + unit
            String valueStr = param.format(value);
            if (!param.unit.isEmpty()) {
                valueStr += " " + param.unit;
            }

            g2d.setColor(PARAM_VALUE);
            int valueWidth = g2d.getFontMetrics().stringWidth(valueStr);
            g2d.drawString(valueStr, barX - valueWidth - 6, curY + 14);

            // Bar
            double ratio = (value - param.min) / (param.max - param.min);
            ratio = Math.max(0, Math.min(1, ratio));

            g2d.setColor(BAR_BG);
            g2d.fillRoundRect(barX, curY + 5, barW, barH, 3, 3);

            g2d.setColor(isSelected ? BAR_FILL_SELECTED : BAR_FILL);
            int fillW = (int) (barW * ratio);
            if (fillW > 0) {
                g2d.fillRoundRect(barX, curY + 5, fillW, barH, 3, 3);
            }

            // Default marker on bar
            double defaultRatio = (defaultValue - param.min) / (param.max - param.min);
            int defaultX = barX + (int) (barW * defaultRatio);
            g2d.setColor(DEFAULT_MARKER_COLOR);
            g2d.drawLine(defaultX, curY + 4, defaultX, curY + 5 + barH);

            curY += lineH;
        }

        // Hints
        curY += 10;
        g2d.setFont(HINT_FONT);
        g2d.setColor(HINT_COLOR);
        g2d.drawString("UP/DOWN select  |  LEFT/RIGHT adjust  |  SHIFT = big step", panelX + 12, curY + 10);
        g2d.drawString("R = reset param  |  SHIFT+R = reset all  |  TAB = close", panelX + 12, curY + 24);
    }
}
