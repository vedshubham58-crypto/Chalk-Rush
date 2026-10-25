// The endless dark floor far below the arena, with a faint neon grid fading into fog.
shader_type spatial;
render_mode unshaded;

uniform vec3 line_color : source_color = vec3(0.0, 0.9, 1.0);
uniform float spacing = 4.0;
uniform vec3 focus = vec3(0.0);

varying vec3 world;

void vertex() {
	world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

void fragment() {
	vec2 g = abs(fract(world.xz / spacing - 0.5) - 0.5) * spacing;
	float w = fwidth(world.x) * 1.5 + 0.04;
	float line = 1.0 - smoothstep(0.0, w, min(g.x, g.y));
	float fade = 1.0 - smoothstep(20.0, 120.0, distance(world.xz, focus.xz));
	ALBEDO = vec3(0.004, 0.006, 0.02) + line_color * line * 0.35 * fade;
}
