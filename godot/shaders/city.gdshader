// Tower blocks of the bright future city round the land: pale blue glass with
// bands of light running up them and a glowing trim at the roof. Instance
// colour picks the neon.
shader_type spatial;

varying vec3 world;
varying vec3 nrm;

float hash(vec2 p) {
	return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void vertex() {
	world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	nrm = NORMAL;
}

void fragment() {
	vec3 c = COLOR.rgb;
	ALBEDO = vec3(0.1, 0.18, 0.45);
	ROUGHNESS = 0.15;
	METALLIC = 0.6;
	if (abs(nrm.y) < 0.5) {
		vec2 p = vec2(abs(nrm.x) > 0.5 ? world.z : world.x, world.y);
		vec2 cellp = floor(p / vec2(1.2, 1.4));
		vec2 f = fract(p / vec2(1.2, 1.4));
		float win = step(0.15, f.x) * step(f.x, 0.85) * step(0.25, f.y) * step(f.y, 0.8);
		float lit = step(0.45, hash(cellp + floor(world.xz * 0.01)));
		vec3 wc = mix(vec3(0.85, 0.95, 1.0), c, step(0.7, hash(cellp * 1.7)));
		// A vertical light band up the middle of some faces.
		float band = step(0.82, hash(floor(world.xz * 0.2))) * (1.0 - smoothstep(0.0, 0.25, abs(fract(p.x * 0.12) - 0.5)));
		EMISSION = vec3(0.04, 0.1, 0.32) + wc * win * lit * 0.8 + c * band * 1.5;
	} else {
		EMISSION = c * 0.8;
	}
}
