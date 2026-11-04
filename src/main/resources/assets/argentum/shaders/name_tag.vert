#version 120

#import <sodium:include/fog.glsl>

attribute float aCorner;
// cached quad index, tag index
attribute vec2 aRef;

uniform sampler2D uQuads;
uniform sampler2D uTags;
// 1 / quad texture size, 1 / tag texture size
uniform vec4 uTexelSizes;

varying vec2 vTexCoord;
varying vec2 vLightCoord;
varying vec4 vColor;
#ifdef USE_FOG
varying float vFogDistance;
uniform int u_FogShape;
#endif

vec4 fetch(sampler2D texture, float x, float y, vec2 texelSize) {
    return texture2DLod(texture, (vec2(x, y) + 0.5) * texelSize, 0.0);
}

void main() {
    // quad is five texels: 4 vertices xyuv in emission order + color
    float quadRow = floor(aRef.x / 512.0);
    float quadColumn = (aRef.x - quadRow * 512.0) * 5.0;
    vec4 vertex = fetch(uQuads, quadColumn + aCorner, quadRow, uTexelSizes.xy);
    vec4 color = fetch(uQuads, quadColumn + 4.0, quadRow, uTexelSizes.xy);

    // tag is three texels: eye-space rows of its 2d transform with the light coordinates and alpha in w
    float tagRow = floor(aRef.y / 1024.0);
    float tagColumn = (aRef.y - tagRow * 1024.0) * 3.0;
    vec4 row0 = fetch(uTags, tagColumn, tagRow, uTexelSizes.zw);
    vec4 row1 = fetch(uTags, tagColumn + 1.0, tagRow, uTexelSizes.zw);
    vec4 row2 = fetch(uTags, tagColumn + 2.0, tagRow, uTexelSizes.zw);

    vec4 eyePosition = vec4(row0.x * vertex.x + row0.y * vertex.y + row0.z,
            row1.x * vertex.x + row1.y * vertex.y + row1.z,
            row2.x * vertex.x + row2.y * vertex.y + row2.z, 1.0);
    gl_Position = gl_ProjectionMatrix * eyePosition;
    vTexCoord = (gl_TextureMatrix[0] * vec4(vertex.zw, 0.0, 1.0)).xy;
    vLightCoord = (gl_TextureMatrix[1] * vec4(row0.w, row1.w, 0.0, 1.0)).xy;
    vColor = vec4(color.rgb, row2.w);
#ifdef USE_FOG
    vFogDistance = getFragDistance(u_FogShape, eyePosition.xyz);
#endif
}
