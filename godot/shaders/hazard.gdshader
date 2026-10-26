// Barrier blocks at the land's edge: diagonal yellow and black hazard stripes,
// glossy, with the yellow glowing a little so the edge reads at speed.
shader_type spatial;

varying vec3 world;

void vertex() {
	world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

void fragment() {
	float s = step(0.5, fract((world.x + world.z + world.y * 1.4) * 1.6));
	vec3 yellow = vec3(1.0, 0.78, 0.1);
	vec3 black = vec3(0.04, 0.04, 0.06);
	ALBEDO = mix(black, yellow, s);
	ROUGHNESS = 0.25;
	METALLIC = 0.2;
	EMISSION = yellow * s * 0.45;
}
