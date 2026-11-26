#version 130

#if defined(LEGACY) && defined(TEXTURE_ARRAY)
#extension GL_EXT_texture_array : require
#endif

#import <sodium:include/fog.glsl>

uniform sampler2D uTexture;
#ifdef TEXTURE_ARRAY
uniform sampler2DArray uTextureArray;
uniform bool uTextureArrayEnabled;
#endif
uniform float uAlphaMin;
uniform float uAlphaMax;

in vec2 vTexCoord;
in vec2 vGlintCoord;
in float vTextureLayer;
#ifdef USE_FOG
in float vFogDistance;
#endif
in vec4 vTint;
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

vec4 tinted(vec2 texCoord) {
#ifdef TEXTURE_ARRAY
    vec4 color = uTextureArrayEnabled ? textureArray(uTextureArray, vec3(texCoord, vTextureLayer)) : texture(uTexture, texCoord);
#else
    vec4 color = texture(uTexture, texCoord);
#endif
    return color * vTint;
}

vec4 shaded(vec4 color) {
    color.rgb = mix(color.rgb, vOverlay.rgb, vOverlay.a);
#ifdef USE_FOG_EXP2
    color = _exp2Fog(color, vFogDistance, u_FogColor, u_FogDensity);
#elif defined(USE_FOG_EXP)
    color = _expFog(color, vFogDistance, u_FogColor, u_FogDensity);
#elif defined(USE_FOG_SMOOTH)
    color = _linearFog(color, vFogDistance, u_FogColor, u_FogStart, u_FogEnd);
#endif
    return color;
}

void main() {
    vec4 color = tinted(vTexCoord);
#ifdef GLINT
    // both layers blend with (SRC_COLOR, ONE), each adding its square, so one pass adds the sum of squares
    vec4 second = tinted(vGlintCoord);
    color = color.a <= 0.1 ? vec4(0.0) : shaded(color);
    second = second.a <= 0.1 ? vec4(0.0) : shaded(second);
    fragColor = sqrt(color * color + second * second);
#else
    if (color.a <= 0.1 || color.a < uAlphaMin || color.a >= uAlphaMax) {
        discard;
    }
    fragColor = shaded(color);
#endif
}
