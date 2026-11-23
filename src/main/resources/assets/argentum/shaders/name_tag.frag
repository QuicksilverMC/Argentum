#version 120

#import <sodium:include/fog.glsl>

uniform sampler2D uTexture;
uniform sampler2D uLightmap;
uniform bool uLightmapped;
uniform float uAlphaRef;

varying vec2 vTexCoord;
varying vec2 vLightCoord;
varying vec4 vColor;
#ifdef USE_FOG
varying float vFogDistance;
#endif

void main() {
    vec4 color = texture2D(uTexture, vTexCoord) * vColor;
    if (uLightmapped) {
        color *= texture2D(uLightmap, vLightCoord);
    }
    if (color.a <= uAlphaRef) {
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
