#version 130

#import <sodium:include/fog.glsl>

in vec3 aPosition;
in vec2 aTexCoord;
in vec3 aNormal;
in vec3 aModel0;
in vec3 aModel1;
in vec3 aModel2;
in vec3 aModel3;
in vec3 aLightCoord;
in vec4 aColor;
in vec4 aVertexColor;
in float aEffectTime;
in vec4 aOverlay;
in vec4 aBoxTexture;
in vec3 aBoxSize;
uniform int uBoxInstancing;
uniform int uGlintPass;
uniform int uChargePass;
uniform int uItemGlintPass;
uniform mat4 uItemGlintMatrix;
uniform vec3 uLightDirection0;
uniform vec3 uLightDirection1;

out vec2 vTexCoord;
out vec2 vLightCoord;
out float vTextureLayer;
out float vLighting;
#ifdef USE_FOG
out float vFogDistance;
uniform int u_FogShape;
#endif
out vec4 vColor;
out vec4 vOverlay;

void main() {
    mat4 model = mat4(vec4(aModel0, 0.0), vec4(aModel1, 0.0), vec4(aModel2, 0.0), vec4(aModel3, 1.0));
    bool box = uBoxInstancing != 0;
    // the shared cube spans 0..1 and its matrix maps it onto the box, so aPosition is already its corner
    // selector; it reuses the colour slot for uv coefficients and has no colour of its own
    vec4 vertexColor = box ? vec4(1.0) : aVertexColor;
    vec4 eyePosition = gl_ModelViewMatrix * model * vec4(aPosition, 1.0);
    vec3 normal = normalize(gl_NormalMatrix * mat3(model) * aNormal);
    float light0 = max(dot(normal, uLightDirection0), 0.0);
    float light1 = max(dot(normal, uLightDirection1), 0.0);

    gl_Position = gl_ProjectionMatrix * eyePosition;
    // rebuild the uv layout Box bakes: each face's rectangle is affine in the texture origin and the box size
    vec2 textureSize = max(aBoxTexture.zw, vec2(1.0));
    vec2 baseTexCoord = box
            ? vec2((aBoxTexture.x + aVertexColor.x * aBoxSize.x + aVertexColor.y * aBoxSize.z) / textureSize.x,
                   (aBoxTexture.y + aVertexColor.z * aBoxSize.y + aVertexColor.w * aBoxSize.z) / textureSize.y)
            : aTexCoord;
    if (uGlintPass >= 0) {
        float angle = radians(30.0 - float(uGlintPass) * 60.0);
        vec2 uv = baseTexCoord + vec2(0.0, aEffectTime * (0.001 + float(uGlintPass) * 0.003) * 20.0);
        vTexCoord = vec2(cos(angle) * uv.x - sin(angle) * uv.y,
                sin(angle) * uv.x + cos(angle) * uv.y) / 3.0;
    } else if (uItemGlintPass >= 0) {
        vTexCoord = (uItemGlintMatrix * vec4(baseTexCoord, 0.0, 1.0)).xy;
    } else if (uChargePass == 1) {
        vTexCoord = baseTexCoord + vec2(aEffectTime * 0.01);
    } else if (uChargePass == 2) {
        vTexCoord = baseTexCoord + vec2(cos(aEffectTime * 0.02) * 3.0, aEffectTime * 0.01);
    } else {
        vTexCoord = (gl_TextureMatrix[0] * vec4(baseTexCoord, 0.0, 1.0)).xy;
    }
    vLightCoord = (gl_TextureMatrix[1] * vec4(aLightCoord.xy, 0.0, 1.0)).xy;
    vTextureLayer = aLightCoord.z;
    vLighting = min(1.0, (light0 + light1) * 0.6 + 0.4);
#ifdef USE_FOG
    vFogDistance = getFragDistance(u_FogShape, eyePosition.xyz);
#endif
    vColor = aColor * vertexColor;
    vOverlay = aOverlay;
}
