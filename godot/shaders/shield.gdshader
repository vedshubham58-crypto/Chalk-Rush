// A see-through energy bubble that glows at its rim.
shader_type spatial;
render_mode unshaded, blend_add, cull_back, depth_draw_never;

uniform vec3 color : source_color = vec3(0.3, 0.7, 1.0);
uniform float strength = 1.0;

void fragment() {
	float rim = pow(1.0 - abs(dot(NORMAL, VIEW)), 2.5);
	float bands = 0.5 + 0.5 * sin(UV.y * 60.0 + TIME * 4.0);
	ALBEDO = color * (rim * 1.4 + bands * 0.05) * strength;
}
