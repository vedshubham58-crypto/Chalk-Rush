// One floor tile of the arena. Empty tiles are dark glass with a neon grid line
// round the edge; owned tiles rise up in the owner's colour with a bright rim.
// INSTANCE_CUSTOM: r = owned (0/1), g = when it changed (seconds, see `now`).
shader_type spatial;
render_mode cull_back;

uniform float now = 0.0;
uniform vec3 grid_color : source_color = vec3(0.0, 0.9, 1.0);
uniform float empty_height = 0.12;
uniform float owned_height = 0.42;
uniform float base_depth = 1.6;

varying vec3 local;
varying float top;
varying float owned;
varying float flash;

void vertex() {
	owned = INSTANCE_CUSTOM.r;
	float t = clamp((now - INSTANCE_CUSTOM.g) / 0.45, 0.0, 1.0);
	// Rise with a little bounce when captured, sink when lost.
	float rise = owned > 0.5 ? 1.0 - pow(1.0 - t, 3.0) + sin(t * 3.14159) * 0.35 : 1.0 - t;
	float h = mix(empty_height, owned_height, rise);
	flash = owned > 0.5 ? (1.0 - t) : 0.0;
	local = VERTEX;
	top = VERTEX.y > 0.0 ? 1.0 : 0.0;
	// The box mesh spans -0.5..0.5; stretch it from deep below up to the tile height.
	VERTEX.y = VERTEX.y > 0.0 ? h : -base_depth;
}

void fragment() {
	float edge = max(abs(local.x), abs(local.z));
	float line = smoothstep(0.43, 0.5, edge);
	vec3 c = COLOR.rgb;
	if (owned > 0.5) {
		float soft = smoothstep(0.3, 0.5, edge);
		ALBEDO = c * (top > 0.5 ? 0.8 - soft * 0.15 : 0.35);
		EMISSION = c * ((top > 0.5 ? 0.32 : 0.12) + line * 0.3 * top + flash * 2.5);
		ROUGHNESS = 0.35;
		METALLIC = 0.2;
	} else {
		ALBEDO = vec3(0.02, 0.025, 0.06);
		EMISSION = grid_color * line * 0.3 * top;
		ROUGHNESS = 0.15;
		METALLIC = 0.7;
	}
}
