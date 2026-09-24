#version 120

#import <sodium:include/fog.glsl>

attribute vec3 aPosition;
attribute vec2 aTexCoord;
attribute float aShade;

// vanilla's whole cloud texel xz, then the sub-texel remainder
uniform vec4 uFrame0;
// cloud color, cloud height relative to the camera
uniform vec4 uFrame1;

varying vec2 vTexCoord;
varying vec4 vColor;
#ifdef USE_FOG
varying vec3 vFogPosition;
#endif

void main() {
    vec3 position = aPosition + vec3(-uFrame0.z, uFrame1.w, -uFrame0.w);
    vec4 eyePosition = gl_ModelViewMatrix * vec4(position, 1.0);
    gl_Position = gl_ProjectionMatrix * eyePosition;
    vTexCoord = (gl_TextureMatrix[0] * vec4((aTexCoord + uFrame0.xy) * 0.00390625, 0.0, 1.0)).xy;
    vColor = floor(vec4(uFrame1.rgb * aShade, 0.8) * 255.0) / 255.0;
#ifdef USE_FOG
    vFogPosition = (gl_ModelViewMatrix * vec4(position.x, 0.0, position.z, 1.0)).xyz;
#endif
}
