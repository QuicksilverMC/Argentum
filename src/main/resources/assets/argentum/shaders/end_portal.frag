#version 120

#import <sodium:include/fog.glsl>

uniform sampler2D uTexture;
uniform sampler2D uLightmap;
uniform int uAddAlpha;
#ifdef USE_FOG
uniform vec4 u_FogColor;
uniform int u_FogShape;
#ifdef USE_FOG_SMOOTH
uniform float u_FogStart;
uniform float u_FogEnd;
#endif
#if defined(USE_FOG_EXP) || defined(USE_FOG_EXP2)
uniform float u_FogDensity;
#endif
#endif
uniform vec4 uS[LAYER_COUNT];
uniform vec4 uT[LAYER_COUNT];
uniform vec4 uQ[LAYER_COUNT];
uniform vec4 uColors[LAYER_COUNT];
uniform vec4 uSettings;

varying vec4 vPosition;
varying vec3 vFogPosition;
varying vec2 vLight;

void main() {
    vec4 light = uSettings.y > 0.5 ? texture2D(uLightmap, vLight) : vec4(1.0);
#ifdef USE_FOG
    float fogDistance = getFragDistance(u_FogShape, vFogPosition);
#endif
    vec4 color = vec4(0.0);
    for (int i = 0; i < LAYER_COUNT; i++) {
        vec2 uv = vec2(dot(uS[i], vPosition), dot(uT[i], vPosition)) / dot(uQ[i], vPosition);
        vec4 layer = texture2D(uTexture, uv) * uColors[i] * light;
        if (layer.a > uSettings.x) {
#ifdef USE_FOG_EXP2
            layer = _exp2Fog(layer, fogDistance, u_FogColor, u_FogDensity);
#elif defined(USE_FOG_EXP)
            layer = _expFog(layer, fogDistance, u_FogColor, u_FogDensity);
#elif defined(USE_FOG_SMOOTH)
            layer = _linearFog(layer, fogDistance, u_FogColor, u_FogStart, u_FogEnd);
#endif
            color.rgb += layer.rgb;
            color.a = uAddAlpha != 0 ? color.a + layer.a : layer.a;
        }
    }
    if (uSettings.x >= 0.0 && color.a == 0.0) discard;
    gl_FragColor = color;
}
