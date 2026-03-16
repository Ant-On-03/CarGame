#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_targetBand; 
uniform float u_totalBands; 

// --- NEW CUSTOMIZATION UNIFORMS ---
uniform vec2 u_texelSize;       // Size of one texture pixel (e.g., 1.0 / 256.0)
uniform vec3 u_lightDir;        // Direction of the sun
uniform vec3 u_outlineColor;    // Color of the ledge borders
uniform float u_outlineThickness; // Thickness of the border (1.0 = standard)

const vec3 SHADOW_COLOR = vec3(0.1, 0.12, 0.2); 

void main() {
    vec4 texSample = texture2D(u_texture, v_texCoords);
    
    // 1. Determine Altitude Band
    float rawHeight = texSample.a;
    float pixelBand = floor(rawHeight * u_totalBands);
    if (pixelBand >= u_totalBands) pixelBand = u_totalBands - 1.0;
    
    // Discard pixels lower than the current 3D slice
    if (pixelBand < u_targetBand) {
        discard;
    }

    // 2. Sample the 4 immediate neighbors (Left, Right, Up, Down)
    // We scale the offset by u_outlineThickness for wider lines
    vec2 offset = u_texelSize * u_outlineThickness;
    float hL = texture2D(u_texture, v_texCoords + vec2(-offset.x, 0.0)).a;
    float hR = texture2D(u_texture, v_texCoords + vec2(offset.x, 0.0)).a;
    float hU = texture2D(u_texture, v_texCoords + vec2(0.0, offset.y)).a;
    float hD = texture2D(u_texture, v_texCoords + vec2(0.0, -offset.y)).a;

    // 3. Real-time Normal Mapping (Slope calculation)
    // We scale the difference to make the shading more pronounced
    float bumpScale = 8.0; 
    vec3 normal = normalize(vec3((hL - hR) * bumpScale, (hD - hU) * bumpScale, 1.0));
    
    // Calculate Diffuse Lighting
    float nDotL = max(dot(normal, u_lightDir), 0.0);
    // Ambient light base (0.6) + directional light (0.4)
    float lightIntensity = 0.6 + (0.4 * nDotL); 
    
    vec3 litColor = texSample.rgb * lightIntensity;

    // 4. Edge Detection (Inner Outline)
    // Check if we are on the very top of the current layer
    bool isLedge = false;
    if (pixelBand == u_targetBand) {
        // Convert neighbor heights to bands
        float bL = floor(hL * u_totalBands);
        float bR = floor(hR * u_totalBands);
        float bU = floor(hU * u_totalBands);
        float bD = floor(hD * u_totalBands);
        
        // If any neighbor is lower than us, this pixel is a ledge!
        if (bL < u_targetBand || bR < u_targetBand || bU < u_targetBand || bD < u_targetBand) {
            isLedge = true;
        }
    }

    // Apply the outline color if it's an edge
    vec3 finalColor = isLedge ? u_outlineColor : litColor;

    // 5. Atmospheric Scattering (Depth Fog)
    float depthRatio = u_targetBand / (u_totalBands - 1.0);
    finalColor = mix(SHADOW_COLOR, finalColor, depthRatio);
    
    gl_FragColor = vec4(finalColor, 1.0) * v_color;
}