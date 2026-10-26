// A light wall: a see-through ribbon of light in the rider's colour, with a
// white-hot top edge and a bright foot.
shader_type spatial;
render_mode unshaded, blend_add, cull_disabled, depth_draw_never;

varying float h;

void vertex() {
	h = VERTEX.y + 0.5;
}

void fragment() {
	vec3 c = COLOR.rgb;
	float top = smoothstep(0.86, 1.0, h);
	float foot = 1.0 - smoothstep(0.0, 0.18, h);
	float body = 0.22 + 0.18 * h;
	ALBEDO = c * (body + foot * 0.9) + mix(c, vec3(1.0), 0.35) * top * 1.5;
}
