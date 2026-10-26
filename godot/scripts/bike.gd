class_name LightBike
extends Node3D
## A chunky light bike with its rider, built from simple shapes: a bright
## silver shell, trims in the rider's colour and a cluster of red tail lights
## for the chase camera to look at. It points along -Z, about one and a half
## cells long.

const SHIELD_SHADER := preload("res://shaders/shield.gdshader")

var color := Color.CYAN
var body: Node3D
var wheels: Array[MeshInstance3D] = []
var shield: MeshInstance3D
var exhaust: CPUParticles3D
var label: Label3D
var crown: Node3D
var frost: MeshInstance3D
var _spin := 0.0


static func glow_mat(c: Color, energy := 2.5) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.emission_enabled = true
	m.emission = c
	m.emission_energy_multiplier = energy
	return m


static func shell_mat(c: Color, metal := 0.85, rough := 0.25, rim_tint: Color = Color.BLACK) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.metallic = metal
	m.roughness = rough
	m.metallic_specular = 0.8
	if rim_tint != Color.BLACK:
		# A faint glow in the rider's colour so dark parts still read against the night.
		m.emission_enabled = true
		m.emission = rim_tint
		m.emission_energy_multiplier = 0.35
		m.rim_enabled = true
		m.rim = 1.0
		m.rim_tint = 0.6
	return m


func _add(mesh: Mesh, mat: Material, pos: Vector3, rot := Vector3.ZERO, scl := Vector3.ONE, parent: Node3D = null) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	mi.mesh = mesh
	mi.material_override = mat
	mi.position = pos
	mi.rotation = rot
	mi.scale = scl
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	(parent if parent else body).add_child(mi)
	return mi


func setup(c: Color, name_text := "", font: Font = null) -> void:
	color = c
	body = Node3D.new()
	add_child(body)
	var dark := shell_mat(c.darkened(0.82), 0.8, 0.22, c.darkened(0.6))
	var silver := shell_mat(Color(0.84, 0.88, 0.95).lerp(c, 0.12), 0.55, 0.16, c.darkened(0.75))
	var red := glow_mat(Color(1.0, 0.12, 0.1), 3.0)
	var trim := glow_mat(c, 3.0)
	var white := glow_mat(Color(1, 1, 1), 4.0)

	# Wheels: glowing rims on dark hubs.
	var rim := TorusMesh.new()
	rim.inner_radius = 0.19
	rim.outer_radius = 0.32
	rim.rings = 24
	rim.ring_segments = 8
	var hub := CylinderMesh.new()
	hub.top_radius = 0.21
	hub.bottom_radius = 0.21
	hub.height = 0.1
	for z in [-0.55, 0.55]:
		var w := Node3D.new()
		w.position = Vector3(0, 0.3, z)
		body.add_child(w)
		_add(rim, trim, Vector3.ZERO, Vector3(0, 0, PI / 2), Vector3.ONE, w)
		_add(hub, dark, Vector3.ZERO, Vector3(0, 0, PI / 2), Vector3.ONE, w)
		wheels.append(w.get_child(1))

	# The long shell that wraps over both wheels.
	var shell := CapsuleMesh.new()
	shell.radius = 0.22
	shell.height = 1.5
	_add(shell, silver, Vector3(0, 0.42, 0), Vector3(PI / 2, 0, 0), Vector3(0.85, 1, 0.85))
	var nose := SphereMesh.new()
	nose.radius = 0.24
	nose.height = 0.48
	_add(nose, silver, Vector3(0, 0.5, -0.42), Vector3.ZERO, Vector3(0.8, 0.7, 1.5))
	# Light stripes down both sides, and along the spine.
	var stripe := BoxMesh.new()
	stripe.size = Vector3(0.025, 0.05, 1.15)
	for x in [-0.165, 0.165]:
		_add(stripe, trim, Vector3(x, 0.44, 0.02))
	var spine := BoxMesh.new()
	spine.size = Vector3(0.05, 0.03, 0.6)
	_add(spine, trim, Vector3(0, 0.6, 0.35))
	var lamp := SphereMesh.new()
	lamp.radius = 0.06
	lamp.height = 0.12
	_add(lamp, white, Vector3(0, 0.5, -0.78))
	# The back end you see from the chase camera: a swept fin, a wide tail-light
	# bar, and glowing fender arcs over the rear wheel.
	var cowl := BoxMesh.new()
	cowl.size = Vector3(0.5, 0.3, 0.32)
	_add(cowl, silver, Vector3(0, 0.56, 0.66))
	var cowl_trim := BoxMesh.new()
	cowl_trim.size = Vector3(0.52, 0.04, 0.34)
	_add(cowl_trim, trim, Vector3(0, 0.72, 0.66))
	# Tail lights: two big pods either side and a grid of small lamps between.
	var pod := BoxMesh.new()
	pod.size = Vector3(0.1, 0.18, 0.04)
	for x in [-0.2, 0.2]:
		_add(pod, red, Vector3(x, 0.56, 0.83))
	var lamp_small := BoxMesh.new()
	lamp_small.size = Vector3(0.05, 0.05, 0.03)
	for x in [-0.08, 0.0, 0.08]:
		for y in [0.5, 0.6]:
			_add(lamp_small, red, Vector3(x, y, 0.83))
	var tail := BoxMesh.new()
	tail.size = Vector3(0.44, 0.04, 0.04)
	_add(tail, glow_mat(c, 6.0), Vector3(0, 0.43, 0.83))
	var fin := PrismMesh.new()
	fin.size = Vector3(0.04, 0.28, 0.5)
	_add(fin, trim, Vector3(0, 0.8, 0.45), Vector3(0, 0, 0), Vector3(1, 1, 1))
	var fender := TorusMesh.new()
	fender.inner_radius = 0.33
	fender.outer_radius = 0.37
	for x in [-0.12, 0.12]:
		_add(fender, trim, Vector3(x, 0.3, 0.55), Vector3(0, 0, PI / 2), Vector3(1, 1, 1))
	var exhaust_glow := SphereMesh.new()
	exhaust_glow.radius = 0.06
	exhaust_glow.height = 0.12
	for x in [-0.1, 0.1]:
		_add(exhaust_glow, glow_mat(c.lightened(0.4), 7.0), Vector3(x, 0.36, 0.82))

	# The rider, tucked low over the bike.
	var suit := shell_mat(Color(0.08, 0.09, 0.14), 0.4, 0.4, c.darkened(0.75))
	var torso := CapsuleMesh.new()
	torso.radius = 0.13
	torso.height = 0.56
	_add(torso, suit, Vector3(0, 0.78, 0.12), Vector3(-1.05, 0, 0))
	# Light lines down the rider's back and across the shoulders.
	var back_line := BoxMesh.new()
	back_line.size = Vector3(0.035, 0.5, 0.02)
	_add(back_line, trim, Vector3(0, 0.84, 0.2), Vector3(-1.05, 0, 0))
	var shoulders := BoxMesh.new()
	shoulders.size = Vector3(0.3, 0.035, 0.02)
	_add(shoulders, trim, Vector3(0, 0.93, 0.0), Vector3(-1.05, 0, 0))
	var helmet := SphereMesh.new()
	helmet.radius = 0.15
	helmet.height = 0.3
	_add(helmet, shell_mat(c.darkened(0.45), 0.9, 0.15, c.darkened(0.5)), Vector3(0, 0.92, -0.17))
	var visor := BoxMesh.new()
	visor.size = Vector3(0.22, 0.06, 0.06)
	_add(visor, white, Vector3(0, 0.94, -0.3))
	var crest := BoxMesh.new()
	crest.size = Vector3(0.03, 0.04, 0.28)
	_add(crest, trim, Vector3(0, 1.06, -0.15))
	var arm := CapsuleMesh.new()
	arm.radius = 0.05
	arm.height = 0.42
	for x in [-0.14, 0.14]:
		_add(arm, suit, Vector3(x, 0.72, -0.18), Vector3(-0.9, 0, 0))
	# A soft pool of light on the ground under the bike.
	var pool := QuadMesh.new()
	pool.size = Vector2(1.6, 2.4)
	var pool_mat := StandardMaterial3D.new()
	pool_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	pool_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	pool_mat.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
	pool_mat.albedo_texture = _radial()
	pool_mat.albedo_color = Color(c, 0.55)
	pool_mat.no_depth_test = false
	var pm := MeshInstance3D.new()
	pm.mesh = pool
	pm.material_override = pool_mat
	pm.rotation = Vector3(-PI / 2, 0, 0)
	pm.position = Vector3(0, 0.02, 0)
	add_child(pm)

	# Shield bubble, off until needed.
	var bubble := SphereMesh.new()
	bubble.radius = 1.0
	bubble.height = 2.0
	var sm := ShaderMaterial.new()
	sm.shader = SHIELD_SHADER
	sm.set_shader_parameter("color", Color(0.35, 0.75, 1.0))
	shield = MeshInstance3D.new()
	shield.mesh = bubble
	shield.material_override = sm
	shield.position = Vector3(0, 0.55, 0)
	shield.visible = false
	add_child(shield)

	# Ice crust while frozen.
	frost = MeshInstance3D.new()
	frost.mesh = bubble
	var fm := StandardMaterial3D.new()
	fm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	fm.albedo_color = Color(0.7, 0.92, 1.0, 0.35)
	fm.emission_enabled = true
	fm.emission = Color(0.5, 0.85, 1.0)
	fm.emission_energy_multiplier = 0.6
	frost.material_override = fm
	frost.scale = Vector3(0.6, 0.6, 0.95)
	frost.position = Vector3(0, 0.5, 0)
	frost.visible = false
	add_child(frost)

	# Sparks off the back wheel when boosting.
	exhaust = CPUParticles3D.new()
	exhaust.amount = 40
	exhaust.lifetime = 0.45
	exhaust.emitting = false
	exhaust.local_coords = false
	exhaust.position = Vector3(0, 0.35, 0.8)
	exhaust.direction = Vector3(0, 0.4, 1)
	exhaust.spread = 25.0
	exhaust.initial_velocity_min = 2.0
	exhaust.initial_velocity_max = 4.0
	exhaust.gravity = Vector3(0, -4, 0)
	exhaust.scale_amount_min = 0.5
	exhaust.scale_amount_max = 1.0
	var spark := BoxMesh.new()
	spark.size = Vector3(0.06, 0.06, 0.06)
	exhaust.mesh = spark
	exhaust.material_override = glow_mat(c.lightened(0.4), 6.0)
	add_child(exhaust)

	label = Label3D.new()
	label.text = name_text
	label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	label.no_depth_test = true
	label.fixed_size = true
	label.pixel_size = 0.0011
	label.font_size = 22
	label.outline_size = 8
	label.outline_modulate = Color(0, 0, 0, 0.85)
	label.modulate = c.lightened(0.3)
	label.position = Vector3(0, 1.3, 0)
	label.offset = Vector2(0, 14)
	if font:
		label.font = font
	add_child(label)

	crown = Node3D.new()
	crown.position = Vector3(0, 1.45, 0)
	crown.visible = false
	add_child(crown)
	var gold := glow_mat(Color(1.0, 0.78, 0.15), 2.2)
	var band := CylinderMesh.new()
	band.top_radius = 0.2
	band.bottom_radius = 0.16
	band.height = 0.12
	_add(band, gold, Vector3.ZERO, Vector3.ZERO, Vector3.ONE, crown)
	var spike := PrismMesh.new()
	spike.size = Vector3(0.1, 0.16, 0.04)
	for k in 5:
		var a := k * TAU / 5.0
		var s := _add(spike, gold, Vector3(sin(a) * 0.18, 0.12, cos(a) * 0.18), Vector3(0, a, 0), Vector3.ONE, crown)
		s.rotation.y = a


func _radial() -> GradientTexture2D:
	var g := Gradient.new()
	g.set_color(0, Color(1, 1, 1, 1))
	g.set_color(1, Color(1, 1, 1, 0))
	var t := GradientTexture2D.new()
	t.gradient = g
	t.fill = GradientTexture2D.FILL_RADIAL
	t.fill_from = Vector2(0.5, 0.5)
	t.fill_to = Vector2(1.0, 0.5)
	t.width = 64
	t.height = 64
	return t


## Per-frame look: wheels spin with [param speed], the body leans into turns by [param lean].
func animate(dt: float, speed: float, lean: float, shielded: bool, boosting: bool, frozen: bool) -> void:
	_spin += speed * dt * 3.3
	for w in wheels:
		w.get_parent().rotation.x = -_spin
	body.rotation.z = lean
	shield.visible = shielded
	if shielded:
		shield.rotation.y += dt * 1.5
	frost.visible = frozen
	exhaust.emitting = boosting
	if crown.visible:
		crown.rotation.y += dt * 2.0
		crown.position.y = 1.45 + sin(Time.get_ticks_msec() * 0.004) * 0.06
