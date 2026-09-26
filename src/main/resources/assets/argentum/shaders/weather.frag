#version 120

#import <sodium:include/fog.glsl>

uniform sampler2D uTexture;
uniform sampler2D uLightmap;

varying vec2 vTexCoord;
varying vec2 vLightCoord;
varying float vAlpha;
#ifdef USE_FOG
varying float vFogDistance;
uniform vec4 u_FogColor;
#ifdef USE_FOG_SMOOTH
uniform float u_FogStart;
uniform float u_FogEnd;
#endif
#if defined(USE_FOG_EXP) || defined(USE_FOG_EXP2)
uniform float u_FogDensity;
#endif
#endif

void main() {
    vec4 color = texture2D(uTexture, vTexCoord) * texture2D(uLightmap, vLightCoord);
    color.a *= vAlpha;
    if (color.a <= 0.1) {
        discard;
    }

#ifdef USE_FOG_EXP2
    color = _exp2Fog(color, vFogDistance, u_FogColor, u_FogDensity);
#elif defined(USE_FOG_EXP)
    color = _expFog(color, vFogDistance, u_FogColor, u_FogDensity);
#elif defined(USE_FOG_SMOOTH)
    color = _linearFog(color, vFogDistance, u_FogColor, u_FogStart, u_FogEnd);
#endif
    gl_FragColor = color;
}
