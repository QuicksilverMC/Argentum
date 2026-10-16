#version 120

varying vec4 vPosition;
varying vec3 vFogPosition;
varying vec2 vLight;

void main() {
    vec4 eyePosition = gl_ModelViewMatrix * gl_Vertex;
    gl_Position = ftransform();
    gl_ClipVertex = eyePosition;
    vPosition = gl_Vertex;
    vFogPosition = eyePosition.xyz;
    vLight = gl_MultiTexCoord0.xy;
}
