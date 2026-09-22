#version 130

#import <sodium:include/fog.glsl>

uniform sampler2D uTexture;
uniform sampler2D uLightmap;
#ifdef TEXTURE_ARRAY
uniform sampler2DArray uTextureArray;
uniform bool uTextureArrayEnabled;
#endif
uniform bool uEmissive;
uniform int uAlphaPass;
#define ALPHA_OPAQUE_CUTOFF (254.0 / 255.0)
#ifdef USE_FOG
uniform vec4 u_FogColor;
#ifdef USE_FOG_SMOOTH
uniform float u_FogStart;
uniform float u_FogEnd;
#endif
#ifdef USE_FOG_EXP2
uniform float u_FogDensity;
#endif
#endif

in vec2 vTexCoord;
in vec2 vLightCoord;
in float vTextureLayer;
in float vLighting;
#ifdef USE_FOG
in float vFogDistance;
#endif
in vec4 vColor;
in vec4 vOverlay;

#ifndef LEGACY
out vec4 fragColor;
#else
#define fragColor gl_FragColor
#endif

#ifdef LEGACY
#define textureArray texture2DArray
#else
#define textureArray texture
#endif

void main() {
    vec4 color = texture(uTexture, vTexCoord);
#ifdef TEXTURE_ARRAY
    if (uTextureArrayEnabled) {
        color = textureArray(uTextureArray, vec3(vTexCoord, vTextureLayer));
    }
#endif
    color *= vColor;
    if (color.a <= 0.1) {
        discard;
    }
    if (uAlphaPass == 1 && color.a >= ALPHA_OPAQUE_CUTOFF) {
        discard;
    }
    if (uAlphaPass == 2 && color.a < ALPHA_OPAQUE_CUTOFF) {
        discard;
    }

    if (!uEmissive) {
        color.rgb *= texture(uLightmap, vLightCoord).rgb * vLighting;
    }
    color.rgb = mix(color.rgb, vOverlay.rgb, vOverlay.a);
#ifdef USE_FOG_EXP2
    color = _exp2Fog(color, vFogDistance, u_FogColor, u_FogDensity);
#elif defined(USE_FOG_SMOOTH)
    color = _linearFog(color, vFogDistance, u_FogColor, u_FogStart, u_FogEnd);
#endif
    fragColor = color;
}
