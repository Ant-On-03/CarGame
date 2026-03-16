#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_targetBand; // The altitude layer we are currently drawing
uniform float u_totalBands; // The total max layers (e.g., 24.0)

const vec3 SHADOW_COLOR = vec3(0.1, 0.12, 0.2); 

void main() {
    vec4 texSample = texture2D(u_texture, v_texCoords);
    
    // The alpha channel stores height. Convert it back to an integer band (0 to 23)
    float pixelBand = floor(texSample.a * u_totalBands);
    
    // Clamp it to prevent edge cases where alpha is exactly 1.0
    if (pixelBand >= u_totalBands) {
        pixelBand = u_totalBands - 1.0;
    }
    
    // SPRITE STACKING LOGIC:
    // If this pixel's elevation is lower than the current drawing layer, discard it.
    if (pixelBand < u_targetBand) {
        discard;
    }
    
    // Atmospheric Scattering: Deep areas are purple, peaks are natural color
    float depthRatio = u_targetBand / (u_totalBands - 1.0);
    vec3 finalColor = mix(SHADOW_COLOR, texSample.rgb, depthRatio);
    
    // Output the solid pixel
    gl_FragColor = vec4(finalColor, 1.0) * v_color;
}