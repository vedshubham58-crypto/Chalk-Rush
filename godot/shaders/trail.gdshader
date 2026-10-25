// A light wall: a glowing slab in the rider's colour with a white-hot top edge.
shader_type spatial;
render_mode unshaded;

varying float h;

void vertex() {
	h = VERTEX.y + 0.5;
}

void fragment() {
	vec3 c = COLOR.rgb;
	float core = smoothstep(0.75, 1.0, h);
	ALBEDO = mix(c * (0.8 + h * 1.4), vec3(1.0), core * 0.35);
}
