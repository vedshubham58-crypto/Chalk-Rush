// The launch bay floor: dark blue glass with rings of light pulsing outward
// from the turntable and spokes of light running away from it.
shader_type spatial;

uniform vec3 base : source_color = vec3(0.03, 0.1, 0.4);
uniform vec3 glow : source_color = vec3(0.0, 0.9, 1.0);

varying vec3 world;

void vertex() {
	world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

void fragment() {
	float r = length(world.xz);
	float a = atan(world.z, world.x);
	float ring = 1.0 - smoothstep(0.0, 0.04, abs(fract(r * 0.5 - TIME * 0.25) - 0.5) * 2.0);
	float spoke = 1.0 - smoothstep(0.0, 0.05, abs(fract(a / 6.2831 * 16.0) - 0.5) * 2.0 * r * 0.3);
	float fade = smoothstep(26.0, 6.0, r) * smoothstep(2.0, 3.0, r);
	float tiles = step(0.96, fract(world.x * 0.5)) + step(0.96, fract(world.z * 0.5));
	ALBEDO = base * 0.5;
	ROUGHNESS = 0.06;
	METALLIC = 0.4;
	SPECULAR = 0.6;
	EMISSION = base * 0.5 + glow * (ring * 1.2 + spoke * 0.35) * fade + vec3(0.3, 0.5, 1.0) * clamp(tiles, 0.0, 1.0) * 0.12 * fade;
}
