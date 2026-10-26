// The minimap: the land picture, with everything outside the country hidden.
shader_type canvas_item;

uniform sampler2D mask_tex : filter_linear;
uniform vec3 floor_color : source_color = vec3(0.12, 0.1, 0.3);

void fragment() {
	float m = texture(mask_tex, UV).r;
	vec4 land = texture(TEXTURE, UV);
	vec3 c = mix(floor_color, land.rgb, smoothstep(0.3, 0.7, land.a));
	COLOR = vec4(c, smoothstep(0.45, 0.55, m) * 0.92);
}
