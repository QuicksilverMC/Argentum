#version 120

#import <sodium:include/fog.glsl>

attribute vec2 aCorner;
// column offset from the camera block xz, then vanilla's rainSize half extents
attribute vec4 aColumn;
// bottom y, top y, block light, sky light
attribute vec4 aSpan;
// rain: speed, hash & 31; snow: u offset, u drift, v offset, v drift
attribute vec4 aJitter;

// camera block origin relative to the camera, 1 / radius
uniform vec4 uFrame0;
// camera entity offset within its block xz, rain strength, tick delta
uniform vec4 uFrame1;
// ticks + tick delta, ticks & 31, ticks & 511, snow
uniform vec4 uFrame2;

varying vec2 vTexCoord;
varying vec2 vLightCoord;
varying float vAlpha;
#ifdef USE_FOG
varying float vFogDistance;
uniform int u_FogShape;
#endif

void main() {
    float side = aCorner.x * 2.0 - 1.0;
    float y = mix(aSpan.x, aSpan.y, aCorner.y);
    vec3 position = uFrame0.xyz + vec3(aColumn.x + 0.5 + side * aColumn.z, y, aColumn.y + 0.5 + side * aColumn.w);
    vec4 eyePosition = gl_ModelViewMatrix * vec4(position, 1.0);
    gl_Position = gl_ProjectionMatrix * eyePosition;

    vec2 offset = aColumn.xy + 0.5 - uFrame1.xy;
    float distance = length(offset) * uFrame0.w;
    float falloff = 1.0 - distance * distance;
    vec2 texCoord;
    float alpha;
    if (uFrame2.w > 0.5) {
        float scroll = (uFrame2.z + uFrame1.w) / 512.0;
        texCoord = vec2(aCorner.x + aJitter.x + uFrame2.x * 0.01 * aJitter.y,
                y * 0.25 + scroll + aJitter.z + uFrame2.x * aJitter.w * 0.001);
        alpha = (falloff * 0.3 + 0.5) * uFrame1.z;
    } else {
        float scroll = (mod(uFrame2.y + aJitter.y, 32.0) + uFrame1.w) / 32.0 * (3.0 + aJitter.x);
        texCoord = vec2(aCorner.x, y * 0.25 + scroll);
        alpha = (falloff * 0.5 + 0.5) * uFrame1.z;
    }

    float alphaByte = alpha < 0.0 ? mod(256.0 - floor(-alpha * 255.0), 256.0) : floor(alpha * 255.0);

    vTexCoord = (gl_TextureMatrix[0] * vec4(texCoord, 0.0, 1.0)).xy;
    vLightCoord = (gl_TextureMatrix[1] * vec4(aSpan.zw, 0.0, 1.0)).xy;
    vAlpha = alphaByte / 255.0;
#ifdef USE_FOG
    vFogDistance = getFragDistance(u_FogShape, eyePosition.xyz);
#endif
}
