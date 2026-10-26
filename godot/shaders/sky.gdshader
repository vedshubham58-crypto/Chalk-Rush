// A bright hyperspeed sky: clear blue overhead, warm haze at the horizon, and a
// blazing light gate that always sits dead ahead of the camera, throwing beams
// of light outward that rush past as you ride.
shader_type sky;

uniform vec3 top_color : source_color = vec3(0.03, 0.15, 0.65);
uniform vec3 mid_color : source_color = vec3(0.2, 0.45, 1.0);
uniform vec3 horizon_color : source_color = vec3(1.0, 0.6, 0.45);
uniform vec3 core_color : source_color = vec3(1.0, 0.86, 0.6);
uniform vec3 halo_color : source_color = vec3(1.0, 0.45, 0.25);
uniform vec3 beam_color : source_color = vec3(0.55, 0.8, 1.0);
uniform vec3 ring_color : source_color = vec3(1.0, 0.4, 0.9);
/// Where the camera looks, flat on the ground. The gate sits that way.
uniform vec3 ahead = vec3(0.0, 0.0, -1.0);
/// 0 = cruising, 1 = boosting: beams rush faster and burn brighter.
uniform float speed = 0.0;

float hash(float p) {
	return fract(sin(p * 127.1) * 43758.5453);
}

void sky() {
	vec3 d = EYEDIR;
	float y = d.y;
	vec3 col = mix(mid_color, top_color, smoothstep(0.1, 0.7, y));
	col = mix(horizon_color, col, smoothstep(0.0, 0.3, y));
	if (y < 0.0) {
		col = mix(horizon_color * 0.8, vec3(0.06, 0.12, 0.35), smoothstep(0.0, -0.25, y));
	}

	// The gate's frame: forward, right and up, centred just above the horizon.
	vec3 f = normalize(vec3(ahead.x, 0.0, ahead.z));
	vec3 core = normalize(f + vec3(0.0, 0.16, 0.0));
	vec3 r = normalize(cross(core, vec3(0.0, 1.0, 0.0)));
	vec3 u = cross(r, core);
	float facing = dot(d, core);
	float ang = acos(clamp(facing, -1.0, 1.0));
	float theta = atan(dot(d, u), dot(d, r));
	float t = TIME * (1.0 + speed * 1.5);

	// Beams fanning out from the gate, each broken into dashes that fly outward.
	float spokes = 18.0;
	float sector = floor((theta / 6.2831 + 0.5) * spokes);
	float within = fract((theta / 6.2831 + 0.5) * spokes);
	float width = 0.18 + 0.12 * hash(sector);
	float beam = smoothstep(width, 0.0, abs(within - 0.5));
	float dash = smoothstep(0.35, 0.6, fract(ang * 9.0 - t * (1.2 + hash(sector + 3.0)) + hash(sector) * 5.0));
	float reach = smoothstep(0.12, 0.3, ang) * (1.0 - smoothstep(0.9, 1.6, ang));
	float ground = smoothstep(-0.02, 0.04, y);
	col += beam_color * beam * (0.2 + 0.8 * dash) * reach * (0.8 + speed * 0.6) * ground;

	// The ring of the gate, and the blinding light inside it.
	float ring = exp(-pow((ang - 0.2) * 40.0, 2.0));
	col += ring_color * ring * 1.6 * ground;
	col = mix(col, core_color * 2.0, smoothstep(0.15, 0.06, ang));
	col += halo_color * exp(-ang * 4.0) * 0.7;
	COLOR = col;
}
