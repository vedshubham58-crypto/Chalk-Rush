class_name LaunchBay
extends Node3D
## The lobby backdrop: your bike turning slowly on a lit turntable inside a
## hologram ring, in front of a launch gate, under the bright hyperspeed sky.

const SKY_SHADER := preload("res://shaders/sky.gdshader")
const FLOOR_SHADER := preload("res://shaders/bay_floor.gdshader")
const HOLO_SHADER := preload("res://shaders/holo.gdshader")
const CITY_SHADER := preload("res://shaders/city.gdshader")

var bike: LightBike
var camera: Camera3D
var _table: Node3D
var _holo: MeshInstance3D
var _gate: Node3D
var _t := 0.0


func build(rider_color: Color, accent: Color, rider_name: String) -> void:
	var env := Environment.new()
	env.background_mode = Environment.BG_SKY
	var sky := Sky.new()
	var sm := ShaderMaterial.new()
	sm.shader = SKY_SHADER
	sm.set_shader_parameter("ahead", Vector3(0, 0, -1))
	sky.sky_material = sm
	env.sky = sky
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.6, 0.72, 1.0)
	env.ambient_light_energy = 0.8
	env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.tonemap_white = 5.0
	env.glow_enabled = true
	env.glow_intensity = 0.75
	env.glow_hdr_threshold = 1.1
	env.glow_blend_mode = Environment.GLOW_BLEND_MODE_ADDITIVE
	env.set_glow_level(0, 0.0)
	env.set_glow_level(1, 1.0)
	env.set_glow_level(2, 1.0)
	env.set_glow_level(3, 0.8)
	env.fog_enabled = true
	env.fog_light_color = Color(0.4, 0.55, 1.0)
	env.fog_density = 0.008
	env.fog_sky_affect = 0.0
	var we := WorldEnvironment.new()
	we.environment = env
	add_child(we)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-35, 160, 0)
	sun.light_color = Color(1.0, 0.85, 0.7)
	sun.light_energy = 1.2
	add_child(sun)
	var fill := DirectionalLight3D.new()
	fill.rotation_degrees = Vector3(-50, -20, 0)
	fill.light_color = Color(0.55, 0.75, 1.0)
	fill.light_energy = 0.8
	add_child(fill)

	# Floor.
	var floor := MeshInstance3D.new()
	var plane := PlaneMesh.new()
	plane.size = Vector2(80, 80)
	floor.mesh = plane
	var fm := ShaderMaterial.new()
	fm.shader = FLOOR_SHADER
	fm.set_shader_parameter("glow", accent)
	floor.material_override = fm
	add_child(floor)

	# Turntable: a silver disc with rings of light round its rim.
	_table = Node3D.new()
	add_child(_table)
	var disc := CylinderMesh.new()
	disc.top_radius = 2.2
	disc.bottom_radius = 2.3
	disc.height = 0.14
	disc.radial_segments = 64
	_mesh(disc, LightBike.shell_mat(Color(0.75, 0.82, 0.95), 0.7, 0.12, rider_color.darkened(0.7)), Vector3(0, 0.07, 0), _table)
	var rim := TorusMesh.new()
	rim.inner_radius = 2.18
	rim.outer_radius = 2.26
	rim.rings = 64
	_mesh(rim, LightBike.glow_mat(rider_color, 4.0), Vector3(0, 0.15, 0), _table)
	var inner := TorusMesh.new()
	inner.inner_radius = 1.45
	inner.outer_radius = 1.5
	inner.rings = 64
	_mesh(inner, LightBike.glow_mat(rider_color, 2.5), Vector3(0, 0.145, 0), _table)
	for k in 8:
		var a := k * TAU / 8.0
		var tick := BoxMesh.new()
		tick.size = Vector3(0.06, 0.02, 0.4)
		var mi := _mesh(tick, LightBike.glow_mat(rider_color, 3.0), Vector3(sin(a) * 1.85, 0.15, cos(a) * 1.85), _table)
		mi.rotation.y = a

	# Hologram ring round the bike.
	var tube := CylinderMesh.new()
	tube.top_radius = 2.4
	tube.bottom_radius = 2.4
	tube.height = 2.6
	tube.cap_top = false
	tube.cap_bottom = false
	tube.radial_segments = 64
	var hm := ShaderMaterial.new()
	hm.shader = HOLO_SHADER
	hm.set_shader_parameter("color", rider_color)
	hm.set_shader_parameter("strength", 0.3)
	_holo = _mesh(tube, hm, Vector3(0, 1.45, 0))

	# The launch gate behind, framed by light pillars.
	_gate = Node3D.new()
	_gate.position = Vector3(0, 0, -9)
	add_child(_gate)
	var arch := TorusMesh.new()
	arch.inner_radius = 5.2
	arch.outer_radius = 5.6
	arch.rings = 96
	_mesh(arch, LightBike.shell_mat(Color(0.7, 0.78, 0.95), 0.7, 0.15), Vector3(0, 1.5, 0), _gate).rotation.x = PI / 2
	var arch_glow := TorusMesh.new()
	arch_glow.inner_radius = 5.05
	arch_glow.outer_radius = 5.15
	arch_glow.rings = 96
	_mesh(arch_glow, LightBike.glow_mat(accent, 5.0), Vector3(0, 1.5, 0.1), _gate).rotation.x = PI / 2
	for x in [-7.5, -4.5, 4.5, 7.5]:
		var pillar := BoxMesh.new()
		pillar.size = Vector3(0.5, 9.0, 0.5)
		_mesh(pillar, LightBike.shell_mat(Color(0.3, 0.4, 0.7), 0.7, 0.2), Vector3(x, 4.5, 2.0 + absf(x) * 0.3), _gate)
		var strip := BoxMesh.new()
		strip.size = Vector3(0.08, 8.6, 0.08)
		_mesh(strip, LightBike.glow_mat(accent, 4.0), Vector3(x, 4.5, 2.27 + absf(x) * 0.3), _gate)

	# A ring of towers in the distance.
	var rng := RandomNumberGenerator.new()
	rng.seed = 99
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.use_colors = true
	mm.mesh = BoxMesh.new()
	mm.instance_count = 90
	for k in mm.instance_count:
		var a := rng.randf_range(PI * 0.6, PI * 2.4)
		var r := rng.randf_range(30.0, 55.0)
		var h := rng.randf_range(6.0, 30.0)
		var b := Basis().scaled(Vector3(rng.randf_range(3, 7), h, rng.randf_range(3, 7)))
		mm.set_instance_transform(k, Transform3D(b, Vector3(sin(a) * r, h / 2.0, cos(a) * r)))
		mm.set_instance_color(k, ArenaView.CITY_NEONS[k % ArenaView.CITY_NEONS.size()])
	var mmi := MultiMeshInstance3D.new()
	mmi.multimesh = mm
	var cm := ShaderMaterial.new()
	cm.shader = CITY_SHADER
	mmi.material_override = cm
	add_child(mmi)

	bike = LightBike.new()
	bike.setup(rider_color, rider_name, UI.body_font)
	bike.label.visible = false
	bike.scale = Vector3.ONE * 1.5
	bike.position = Vector3(0, 0.14, 0)
	_table.add_child(bike)

	camera = Camera3D.new()
	camera.fov = 52
	add_child(camera)
	_place_camera()


func _mesh(m: Mesh, mat: Material, pos: Vector3, parent: Node3D = null) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	mi.mesh = m
	mi.material_override = mat
	mi.position = pos
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	(parent if parent else self).add_child(mi)
	return mi


func _place_camera() -> void:
	# Drift gently side to side so the scene feels alive.
	var sway := sin(_t * 0.3) * 0.8
	camera.look_at_from_position(Vector3(sway, 3.0, 10.5), Vector3(0, 0.55, 0))


func _process(dt: float) -> void:
	_t += dt
	_table.rotation.y += dt * 0.45
	bike.animate(dt, 0.0, 0.0, false, false, false)
	bike.position.y = 0.14 + sin(_t * 1.6) * 0.03
	_holo.rotation.y -= dt * 0.2
	_place_camera()
