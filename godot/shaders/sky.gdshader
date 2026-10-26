// A synthwave sky: deep blue overhead fading to hot pink at the horizon, a big
// striped sun to the north and a scatter of stars.
shader_type sky;

uniform vec3 top_color : source_color = vec3(0.04, 0.02, 0.16);
uniform vec3 mid_color : source_color = vec3(0.28, 0.06, 0.42);
uniform vec3 horizon_color : source_color = vec3(1.0, 0.32, 0.55);
uniform vec3 sun_top : source_color = vec3(1.0, 0.85, 0.3);
uniform vec3 sun_bottom : source_color = vec3(1.0, 0.2, 0.55);
uniform vec3 sun_dir = vec3(0.0, 0.12, -1.0);

float hash(vec2 p) {
	return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void sky() {
	vec3 d = EYEDIR;
	float y = d.y;
	vec3 col = mix(mid_color, top_color, smoothstep(0.05, 0.6, y));
	col = mix(horizon_color, col, smoothstep(-0.02, 0.18, y));
	if (y < 0.0) {
		col = mix(horizon_color * 0.6, vec3(0.05, 0.02, 0.12), smoothstep(0.0, -0.3, y));
	}
	// Stars.
	vec2 g = floor(vec2(atan(d.x, d.z) * 180.0, y * 200.0));
	float s = step(0.996, hash(g)) * smoothstep(0.1, 0.5, y);
	col += vec3(s) * (0.6 + 0.4 * sin(TIME * 2.0 + hash(g) * 30.0));
	// The sun, with stripes cut out of its lower half.
	vec3 sd = normalize(sun_dir);
	float r = acos(clamp(dot(d, sd), -1.0, 1.0));
	float disk = 1.0 - smoothstep(0.26, 0.27, r);
	float rel = (d.y - sd.y) / 0.27;
	float stripes = rel < 0.1 ? step(0.35 + rel * 1.5, fract(rel * 9.0)) : 1.0;
	vec3 sun = mix(sun_bottom, sun_top, clamp(rel * 0.5 + 0.5, 0.0, 1.0));
	col = mix(col, sun * 2.2, disk * stripes * step(-0.01, y));
	col += sun_bottom * 0.35 * exp(-r * 4.0) * step(-0.05, y);
	COLOR = col;
}
