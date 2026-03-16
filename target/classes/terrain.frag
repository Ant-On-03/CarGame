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
uniform float u_outlineThickness; // Kept this to control line width

const vec3 SHADOW_COLOR = vec3(0.05, 0.15, 0.1); 

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

void main() {
    vec4 texSample = texture2D(u_texture, v_texCoords);
    
    // 1. Determine Altitude Band
    float rawHeight = texSample.a;
    float pixelBand = floor(rawHeight * u_totalBands);
    if (pixelBand >= u_totalBands) pixelBand = u_totalBands - 1.0;
    
    if (pixelBand < u_targetBand) {
        discard;
    }

    // 2. Sample neighbors for Normals and Outlines
    vec2 offset = u_texelSize * u_outlineThickness;
    float hL = texture2D(u_texture, v_texCoords + vec2(-offset.x, 0.0)).a;
    float hR = texture2D(u_texture, v_texCoords + vec2(offset.x, 0.0)).a;
    float hU = texture2D(u_texture, v_texCoords + vec2(0.0, offset.y)).a;
    float hD = texture2D(u_texture, v_texCoords + vec2(0.0, -offset.y)).a;

    // 3. Real-time Normal Mapping
    float bumpScale = 8.0; 
    vec3 normal = normalize(vec3((hL - hR) * bumpScale, (hD - hU) * bumpScale, 1.0));
    
    float nDotL = max(dot(normal, u_lightDir), 0.0);
    float lightIntensity = 0.6 + (0.4 * nDotL); 
    
    // Procedural Texture
    float n = noise(v_texCoords * 60.0); 
    n = smoothstep(0.3, 0.7, n);
    float textureVariation = 0.65 + (0.6 * n);
    
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

    // DYNAMIC BORDER: If it's a ledge, take the base terrain color and darken it to 40% brightness!
    vec3 finalColor = isLedge ? (litColor * 0.4) : litColor;

    // 5. Atmospheric Scattering
    float depthRatio = u_targetBand / (u_totalBands - 1.0);
    finalColor = mix(SHADOW_COLOR, finalColor, depthRatio);
    
    gl_FragColor = vec4(finalColor, 1.0) * v_color;
}