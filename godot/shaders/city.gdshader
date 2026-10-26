// Tower blocks of the neon city round the country: dark glass with lit windows
// and a glowing trim at the roof. Instance colour picks the neon.
shader_type spatial;

varying vec3 world;
varying vec3 nrm;
varying float roof;

float hash(vec2 p) {
	return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void vertex() {
	world = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	nrm = NORMAL;
	roof = VERTEX.y > 0.49 ? 1.0 : 0.0;
}

void fragment() {
	vec3 c = COLOR.rgb;
	ALBEDO = vec3(0.05, 0.04, 0.1);
	ROUGHNESS = 0.2;
	METALLIC = 0.8;
	if (abs(nrm.y) < 0.5) {
		vec2 p = vec2(abs(nrm.x) > 0.5 ? world.z : world.x, world.y);
		vec2 cellp = floor(p / vec2(1.4, 1.8));
		vec2 f = fract(p / vec2(1.4, 1.8));
		float win = step(0.25, f.x) * step(f.x, 0.75) * step(0.3, f.y) * step(f.y, 0.75);
		float lit = step(0.7, hash(cellp + floor(world.xz * 0.01)));
		vec3 wc = mix(vec3(1.0, 0.8, 0.5), c, step(0.5, hash(cellp * 1.7)));
		EMISSION = wc * win * lit * 1.1;
	} else {
		EMISSION = c * 0.4;
	}
}
