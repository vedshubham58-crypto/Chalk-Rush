// The country's floor: one smooth surface, no tiles. Claimed land rises gently
// and glows in its owner's colour; the country's outline is cut smooth from a
// blurred mask, so edges curve instead of stepping.
// land_tex: rgb = owner colour, a = how claimed (0..1). mask_tex: r = inside the country.
shader_type spatial;
render_mode cull_back;

uniform sampler2D land_tex : filter_linear, repeat_disable;
uniform sampler2D mask_tex : filter_linear, repeat_disable;
uniform float size = 100.0;
uniform float land_height = 0.35;
uniform vec3 floor_color : source_color = vec3(0.13, 0.1, 0.3);
uniform vec3 floor_color2 : source_color = vec3(0.07, 0.12, 0.3);
uniform vec3 accent : source_color = vec3(0.0, 0.9, 1.0);
uniform float now = 0.0;

varying vec2 world;

float hex_lines(vec2 p) {
	// Distance to the nearest edge of a hexagon grid, for a faint honeycomb.
	vec2 r = vec2(1.0, 1.732);
	vec2 h = r * 0.5;
	vec2 a = mod(p, r) - h;
	vec2 b = mod(p - h, r) - h;
	vec2 g = dot(a, a) < dot(b, b) ? a : b;
	vec2 q = abs(g);
	return 0.5 - max(dot(q, normalize(vec2(1.0, 1.732))), q.x);
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

	// Open floor: glossy indigo with a soft colour drift and a faint honeycomb.
	float drift = 0.5 + 0.5 * sin(world.x * 0.05 + world.y * 0.035);
	vec3 base = mix(floor_color, floor_color2, drift);
	float hex = 1.0 - smoothstep(0.0, 0.06, hex_lines(world * 0.5));
	// Slow bands of light sweeping across, so the ground feels alive.
	float sweep = smoothstep(0.92, 1.0, sin(world.x * 0.08 + world.y * 0.08 - now * 1.5));

	// Claimed land: the owner's colour, brightest at its edge.
	float edge = 1.0 - abs(land.a - 0.5) * 2.0;
	edge = smoothstep(0.2, 0.9, edge);
	vec3 c = land.rgb;

	ALBEDO = mix(base, c * (0.5 + hex * 0.1), owned);
	ROUGHNESS = mix(0.12, 0.35, owned);
	METALLIC = mix(0.55, 0.15, owned);
	SPECULAR = 0.7;
	EMISSION = mix(accent * (hex * 0.18 + sweep * 0.12) + base * 0.25, c * (0.16 + hex * 0.12 + sweep * 0.1), owned)
		+ c * edge * 2.2 + accent * rim * 3.0;
}
