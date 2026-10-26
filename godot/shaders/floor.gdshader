// The land's floor: one smooth, glossy surface of electric blue glass. Lanes of
// light run across it with pulses racing along them, and it mirrors the bright
// sky and the light gate ahead. Claimed land rises gently and glows in its
// owner's colour; the outline is cut smooth from a blurred mask.
// land_tex: rgb = owner colour, a = how claimed (0..1). mask_tex: r = inside the land.
shader_type spatial;
render_mode cull_back;

uniform sampler2D land_tex : filter_linear, repeat_disable;
uniform sampler2D mask_tex : filter_linear, repeat_disable;
uniform float size = 100.0;
uniform float land_height = 0.35;
uniform vec3 floor_color : source_color = vec3(0.0, 0.28, 1.0);
uniform vec3 floor_color2 : source_color = vec3(0.06, 0.12, 1.0);
uniform vec3 lane_color : source_color = vec3(0.55, 0.9, 1.0);
uniform vec3 accent : source_color = vec3(0.0, 0.9, 1.0);
uniform vec3 glow_color : source_color = vec3(1.0, 0.7, 0.45);
uniform vec3 ahead = vec3(0.0, 0.0, -1.0);
uniform float now = 0.0;

varying vec2 world;

float lane(float v, float every, float w) {
	float d = abs(fract(v / every + 0.5) - 0.5) * every;
	return 1.0 - smoothstep(0.0, w, d);
}

float hash(float p) {
	return fract(sin(p * 91.7) * 43758.5453);
}

void vertex() {
	world = UV * size;
	float a = texture(land_tex, UV).a;
	VERTEX.y += smoothstep(0.0, 1.0, a) * land_height;
}

void fragment() {
	float m = texture(mask_tex, UV).r;
	if (m < 0.5) {
		discard;
	}
	vec4 land = texture(land_tex, UV);
	float owned = smoothstep(0.35, 0.65, land.a);
	float rim = smoothstep(0.5, 0.56, m) * (1.0 - smoothstep(0.56, 0.7, m));

	// Mirror: what the floor reflects, a fake of the sky and its gate.
	vec3 eye = normalize((INV_VIEW_MATRIX * vec4(VIEW, 0.0)).xyz);
	vec3 refl = vec3(-eye.x, eye.y, -eye.z);
	vec3 f = normalize(vec3(ahead.x, 0.0, ahead.z));
	float toward = max(dot(normalize(vec3(refl.x, 0.0, refl.z)), f), 0.0);
	float graze = pow(1.0 - clamp(eye.y, 0.0, 1.0), 4.0);
	vec3 mirror = glow_color * pow(toward, 8.0) * graze * 1.1 + vec3(0.3, 0.55, 1.0) * graze * 0.25;

	// Open floor: blue glass with a slow colour drift and a faint panel seam grid.
	float drift = 0.5 + 0.5 * sin(world.x * 0.045 + world.y * 0.03);
	vec3 base = mix(floor_color, floor_color2, drift);
	float seam = max(lane(world.x, 2.0, 0.03), lane(world.y, 2.0, 0.03));
	// Lanes of light every few cells, with pulses racing along them.
	float lx = lane(world.x, 6.0, 0.06);
	float ly = lane(world.y, 6.0, 0.06);
	float px = smoothstep(0.75, 1.0, fract(world.y * 0.04 - now * 0.9 + hash(floor(world.x / 6.0 + 0.5))));
	float py = smoothstep(0.75, 1.0, fract(world.x * 0.04 - now * 0.9 + hash(floor(world.y / 6.0 + 0.5) + 7.0)));
	float lanes = lx * (0.45 + px * 2.5) + ly * (0.45 + py * 2.5);

	// Claimed land: the owner's colour, brightest at its edge.
	float edge = 1.0 - abs(land.a - 0.5) * 2.0;
	edge = smoothstep(0.2, 0.9, edge);
	vec3 c = land.rgb;

	vec3 open_glow = base * 0.6 + lane_color * lanes + lane_color * seam * 0.08 + mirror;
	vec3 land_glow = c * (0.45 + seam * 0.1) + mix(c, vec3(1.0), 0.5) * lanes * 0.5 + mirror * 0.8;
	ALBEDO = mix(base, c, owned) * 0.25;
	ROUGHNESS = 0.2;
	METALLIC = 0.0;
	SPECULAR = 0.0;
	EMISSION = mix(open_glow, land_glow, owned) + c * edge * 2.4 + accent * rim * 3.0;
}
