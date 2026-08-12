#version 330 core

in vec2 fragUv;

uniform sampler2D atlas;
uniform vec4 color = vec4(1.0, 1.0, 1.0, 1.0);

out vec4 outColor;

void main() {
    vec4 texColor = texture(atlas, fragUv);
    if (texColor.a < 0.1) {
        discard;
    }
    outColor = texColor * color;
}
