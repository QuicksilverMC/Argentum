#version 120

#import <sodium:include/fog.glsl>

uniform sampler2D uTexture;

varying vec2 vTexCoord;
varying vec4 vColor;
#ifdef USE_FOG
varying vec3 vFogPosition;
uniform int u_FogShape;
uniform vec4 u_FogColor;
#ifdef USE_FOG_SMOOTH
uniform float u_FogStart;
uniform float u_FogEnd;
#endif
#ifdef USE_FOG_EXP2
uniform float u_FogDensity;
#endif
#endif

void main() {
    vec4 color = texture2D(uTexture, vTexCoord) * vColor;
#ifdef USE_FOG
    float fogDistance = getFragDistance(u_FogShape, vFogPosition);
#endif
#ifdef USE_FOG_EXP2
    color = _exp2Fog(color, fogDistance, u_FogColor, u_FogDensity);
#elif defined(USE_FOG_SMOOTH)
    color = _linearFog(color, fogDistance, u_FogColor, u_FogStart, u_FogEnd);
#endif
    gl_FragColor = color;
}
