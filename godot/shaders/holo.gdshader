// A see-through hologram: scan lines drifting upward, fading out at the top
// and bottom edges.
shader_type spatial;
render_mode unshaded, blend_add, cull_disabled, depth_draw_never;

uniform vec3 color : source_color = vec3(0.0, 0.9, 1.0);
uniform float strength = 0.6;

void fragment() {
	float v = UV.y;
	float lines = 0.35 + 0.65 * step(0.5, fract(v * 40.0 - TIME * 0.8));
	float band = smoothstep(0.0, 0.08, fract(v * 3.0 - TIME * 0.35)) * (1.0 - smoothstep(0.08, 0.3, fract(v * 3.0 - TIME * 0.35)));
	float edge = smoothstep(0.0, 0.15, v) * smoothstep(1.0, 0.75, v);
	ALBEDO = color * (lines * 0.25 + band * 0.9) * edge * strength;
}
