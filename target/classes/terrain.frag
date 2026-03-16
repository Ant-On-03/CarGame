#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_targetBand; 
uniform float u_totalBands; 

uniform vec2 u_texelSize;       
uniform vec3 u_lightDir;        
uniform vec3 u_outlineColor;    
uniform float u_outlineThickness; 

// A lush, deep forest green for the atmospheric fog/abyss
const vec3 SHADOW_COLOR = vec3(0.05, 0.15, 0.1); 

// --- PROCEDURAL NOISE FUNCTION ---
// Generates a pseudo-random value between 0.0 and 1.0 based on coordinates
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

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
    vec2 offset = u_texelSize * u_outlineThickness;
    float hL = texture2D(u_texture, v_texCoords + vec2(-offset.x, 0.0)).a;
    float hR = texture2D(u_texture, v_texCoords + vec2(offset.x, 0.0)).a;
    float hU = texture2D(u_texture, v_texCoords + vec2(0.0, offset.y)).a;
    float hD = texture2D(u_texture, v_texCoords + vec2(0.0, -offset.y)).a;

    // 3. Real-time Normal Mapping (Slope calculation)
    float bumpScale = 8.0; 
    vec3 normal = normalize(vec3((hL - hR) * bumpScale, (hD - hU) * bumpScale, 1.0));
    
    // Calculate Diffuse Lighting
    float nDotL = max(dot(normal, u_lightDir), 0.0);
    float lightIntensity = 0.6 + (0.4 * nDotL); 
    
    // --- APPLY PROCEDURAL TEXTURE ---
    // Multiply UVs by a large number (1500.0) to make the noise tiny and dense like grain/grass
    float grain = hash(v_texCoords * 1500.0); 
    
    // Map the grain from [0.0 to 1.0] into a subtle variation [0.85 to 1.15]
    float textureVariation = 0.85 + (0.3 * grain);
    
    // Combine base color, lighting, and texture variation
    vec3 litColor = texSample.rgb * lightIntensity * textureVariation;

    // 4. Edge Detection (Inner Outline)
    bool isLedge = false;
    if (pixelBand == u_targetBand) {
        float bL = floor(hL * u_totalBands);
        float bR = floor(hR * u_totalBands);
        float bU = floor(hU * u_totalBands);
        float bD = floor(hD * u_totalBands);
        
        if (bL < u_targetBand || bR < u_targetBand || bU < u_targetBand || bD < u_targetBand) {
            isLedge = true;
        }
    }

    vec3 finalColor = isLedge ? u_outlineColor : litColor;

    // 5. Atmospheric Scattering (Depth Fog)
    float depthRatio = u_targetBand / (u_totalBands - 1.0);
    finalColor = mix(SHADOW_COLOR, finalColor, depthRatio);
    
    gl_FragColor = vec4(finalColor, 1.0) * v_color;
}