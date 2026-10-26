// The bike's sculpted hull: glossy pearl-silver, with light lines down both
// flanks and along the spine, and thin panel seams across it.
// UV.x runs round the hull (0 = right flank, 0.25 = top, 0.5 = left flank),
// UV.y runs nose (0) to tail (1).
shader_type spatial;

uniform vec3 base : source_color = vec3(0.86, 0.9, 0.97);
uniform vec3 glow : source_color = vec3(0.0, 0.9, 1.0);
uniform float energy = 3.5;

float line(float v, float at, float w) {
	return 1.0 - smoothstep(0.0, w, abs(v - at));
}

void fragment() {
	float u = UV.x;
	float z = UV.y;
	// Flank lines sit a little below the middle of each side.
	float flank = max(line(u, 0.97, 0.01), line(u, 0.53, 0.01));
	float spine = line(u, 0.25, 0.008) * smoothstep(0.45, 0.6, z);
	float seams = (line(fract(z * 6.0), 0.5, 0.012)) * step(0.0, sin(u * 6.2831)) * 0.6;
	float lit = clamp(flank + spine, 0.0, 1.0);
	// Underside darker, like a belly pan.
	float belly = smoothstep(0.58, 0.7, u) * (1.0 - smoothstep(0.8, 0.92, u));
	ALBEDO = mix(base, base * 0.25, belly * 0.8) * (1.0 - seams * 0.5);
	METALLIC = 0.6;
	ROUGHNESS = 0.14;
	RIM = 0.6;
	RIM_TINT = 0.5;
	EMISSION = glow * lit * energy + glow * 0.04;
}
