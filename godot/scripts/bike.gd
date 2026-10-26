class_name LightBike
extends Node3D
## A futuristic light bike with its rider, built from simple shapes: hubless
## wheels of light, a long silver hull with blade fairings, an energy core, a
## rear wing over twin thrusters and red tail lights for the chase camera to
## look at. It points along -Z, about one and a half cells long.

const SHIELD_SHADER := preload("res://shaders/shield.gdshader")
const HULL_SHADER := preload("res://shaders/bike_hull.gdshader")
## The hull's side view, nose to tail: [z, half width, bottom, top].
const HULL_PROFILE := [
	[-1.25, 0.02, 0.64, 0.66], [-1.05, 0.11, 0.58, 0.74], [-0.75, 0.16, 0.6, 0.84],
	[-0.45, 0.19, 0.62, 0.92], [-0.15, 0.18, 0.56, 0.9], [0.15, 0.16, 0.52, 0.84],
	[0.45, 0.19, 0.58, 0.9], [0.75, 0.22, 0.62, 0.96], [1.0, 0.22, 0.64, 0.96],
	[1.08, 0.18, 0.68, 0.92],
]

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

	var glass := shell_mat(Color(0.02, 0.03, 0.08), 0.9, 0.05, c.darkened(0.3))
	var core_glow := glow_mat(c.lightened(0.25), 6.0)

	# Hubless wheels: a dark tyre round an open ring of light, with tread lights
	# that spin and a faint energy disc where the hub would be.
	var tyre := TorusMesh.new()
	tyre.inner_radius = 0.245
	tyre.outer_radius = 0.34
	tyre.rings = 32
	tyre.ring_segments = 10
	var inner := TorusMesh.new()
	inner.inner_radius = 0.205
	inner.outer_radius = 0.245
	inner.rings = 32
	inner.ring_segments = 6
	var tread := BoxMesh.new()
	tread.size = Vector3(0.1, 0.02, 0.06)
	var disc := CylinderMesh.new()
	disc.top_radius = 0.21
	disc.bottom_radius = 0.21
	disc.height = 0.01
	var disc_mat := StandardMaterial3D.new()
	disc_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	disc_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	disc_mat.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
	disc_mat.cull_mode = BaseMaterial3D.CULL_DISABLED
	disc_mat.albedo_color = Color(c, 0.22)
	for z in [-0.6, 0.6]:
		var w := Node3D.new()
		w.position = Vector3(0, 0.34, z)
		body.add_child(w)
		_add(inner, glow_mat(c, 4.5), Vector3.ZERO, Vector3(0, 0, PI / 2), Vector3.ONE, w)
		var spin := _add(tyre, dark, Vector3.ZERO, Vector3(0, 0, PI / 2), Vector3.ONE, w)
		wheels.append(spin)
		for k in 6:
			var a := k * TAU / 6.0
			_add(tread, glow_mat(c.lightened(0.5), 3.0), Vector3(0, cos(a) * 0.335, sin(a) * 0.335), Vector3(a, 0, 0), Vector3.ONE, w)
		_add(disc, disc_mat, Vector3.ZERO, Vector3(0, 0, PI / 2), Vector3.ONE, w)

	# A sculpted hull lofted from nose to tail: low wedge nose, a tank that
	# rises over the front wheel, a dip for the seat and a high, wide tail.
	var hull := MeshInstance3D.new()
	hull.mesh = _hull_mesh()
	var hm := ShaderMaterial.new()
	hm.shader = HULL_SHADER
	hm.set_shader_parameter("base", Color(0.86, 0.9, 0.97).lerp(c, 0.12))
	hm.set_shader_parameter("glow", c)
	hull.material_override = hm
	hull.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	body.add_child(hull)
	# Underglow strip and the energy core between the wheels.
	var under := BoxMesh.new()
	under.size = Vector3(0.1, 0.02, 1.2)
	_add(under, glow_mat(c, 5.0), Vector3(0, 0.53, 0))
	var core := SphereMesh.new()
	core.radius = 0.1
	core.height = 0.2
	_add(core, core_glow, Vector3(0, 0.46, 0))
	# Dark glass windscreen, and V-shaped headlight strips on the nose.
	var screen := SphereMesh.new()
	screen.radius = 0.5
	screen.height = 1.0
	_add(screen, glass, Vector3(0, 0.9, -0.62), Vector3(0.2, 0, 0), Vector3(0.26, 0.14, 0.55))
	var head := BoxMesh.new()
	head.size = Vector3(0.18, 0.02, 0.02)
	for x in [-0.08, 0.08]:
		_add(head, white, Vector3(x * 0.8, 0.66, -1.12), Vector3(0, 0.6 * signf(x), 0))

	# The back end the chase camera looks at: a rear wing on two struts, twin
	# thrusters and a cluster of red tail lights.
	var strut := BoxMesh.new()
	strut.size = Vector3(0.03, 0.22, 0.08)
	for x in [-0.12, 0.12]:
		_add(strut, silver, Vector3(x, 1.02, 0.9), Vector3(-0.3, 0, 0))
	var wing := BoxMesh.new()
	wing.size = Vector3(0.62, 0.035, 0.16)
	_add(wing, silver, Vector3(0, 1.12, 0.94))
	var wing_edge := BoxMesh.new()
	wing_edge.size = Vector3(0.64, 0.02, 0.02)
	_add(wing_edge, glow_mat(c, 6.0), Vector3(0, 1.12, 1.02))
	var thruster := CylinderMesh.new()
	thruster.top_radius = 0.06
	thruster.bottom_radius = 0.08
	thruster.height = 0.3
	var nozzle := SphereMesh.new()
	nozzle.radius = 0.055
	nozzle.height = 0.11
	for x in [-0.2, 0.2]:
		_add(thruster, silver, Vector3(x, 0.6, 0.9), Vector3(PI / 2, 0, 0))
		_add(nozzle, glow_mat(c.lightened(0.4), 7.0), Vector3(x, 0.6, 1.05))
	var pod := BoxMesh.new()
	pod.size = Vector3(0.07, 0.035, 0.03)
	for x in [-0.07, 0.07]:
		for y in [0.76, 0.84]:
			_add(pod, red, Vector3(x, y, 1.085))
	var tail := BoxMesh.new()
	tail.size = Vector3(0.3, 0.025, 0.03)
	_add(tail, red, Vector3(0, 0.7, 1.085))

	# The rider: an armoured pilot tucked low over the hull, in a dark suit with
	# silver plates, light lines, a glowing back core and a finned helmet.
	# Built on its own node, lifted to sit on the hull's seat.
	var bike_body := body
	body = Node3D.new()
	body.position = Vector3(0, 0.1, 0.0)
	bike_body.add_child(body)
	var suit := shell_mat(Color(0.16, 0.18, 0.26), 0.7, 0.3, c.darkened(0.6))
	var plate := shell_mat(Color(0.86, 0.9, 0.97).lerp(c, 0.2), 0.6, 0.15, c.darkened(0.6))
	var torso := CapsuleMesh.new()
	torso.radius = 0.13
	torso.height = 0.56
	_add(torso, suit, Vector3(0, 0.86, 0.2), Vector3(-1.1, 0, 0))
	var back := BoxMesh.new()
	back.size = Vector3(0.24, 0.36, 0.06)
	_add(back, plate, Vector3(0, 0.96, 0.24), Vector3(-1.1, 0, 0))
	var back_line := BoxMesh.new()
	back_line.size = Vector3(0.02, 0.34, 0.02)
	for x in [-0.075, 0.075]:
		_add(back_line, trim, Vector3(x, 0.995, 0.25), Vector3(-1.1, 0, 0))
	var back_core := SphereMesh.new()
	back_core.radius = 0.045
	back_core.height = 0.09
	_add(back_core, core_glow, Vector3(0, 1.0, 0.3))
	var pad := SphereMesh.new()
	pad.radius = 0.5
	pad.height = 1.0
	var pad_edge := BoxMesh.new()
	pad_edge.size = Vector3(0.02, 0.02, 0.18)
	for x in [-0.155, 0.155]:
		_add(pad, plate, Vector3(x, 0.99, 0.04), Vector3.ZERO, Vector3(0.2, 0.12, 0.24))
		_add(pad_edge, trim, Vector3(x * 1.35, 0.98, 0.04))
	var arm := CapsuleMesh.new()
	arm.radius = 0.05
	arm.height = 0.44
	var glove := SphereMesh.new()
	glove.radius = 0.05
	glove.height = 0.1
	for x in [-0.15, 0.15]:
		_add(arm, plate, Vector3(x, 0.85, -0.12), Vector3(-1.0, 0, 0))
		_add(glove, trim, Vector3(x * 0.95, 0.76, -0.33))
	var thigh := CapsuleMesh.new()
	thigh.radius = 0.065
	thigh.height = 0.44
	var boot := BoxMesh.new()
	boot.size = Vector3(0.07, 0.07, 0.2)
	var boot_line := BoxMesh.new()
	boot_line.size = Vector3(0.075, 0.015, 0.2)
	for x in [-0.15, 0.15]:
		_add(thigh, suit, Vector3(x * 1.3, 0.8, 0.5), Vector3(-1.35, 0, 0))
		_add(boot, plate, Vector3(x * 1.55, 0.72, 0.72))
		_add(boot_line, trim, Vector3(x * 1.55, 0.69, 0.72))
	var helmet := SphereMesh.new()
	helmet.radius = 0.15
	helmet.height = 0.3
	_add(helmet, plate, Vector3(0, 1.0, -0.14), Vector3.ZERO, Vector3(0.9, 0.85, 1.3))
	var visor := SphereMesh.new()
	visor.radius = 0.5
	visor.height = 1.0
	_add(visor, glass, Vector3(0, 0.99, -0.24), Vector3.ZERO, Vector3(0.25, 0.14, 0.2))
	var visor_band := BoxMesh.new()
	visor_band.size = Vector3(0.24, 0.025, 0.06)
	_add(visor_band, white, Vector3(0, 1.0, -0.33))
	var crest := PrismMesh.new()
	crest.size = Vector3(0.12, 0.3, 0.025)
	_add(crest, trim, Vector3(0, 1.12, -0.1), Vector3(-PI / 2, 0, 0), Vector3(0.3, 1, 2.5))
	var ear := BoxMesh.new()
	ear.size = Vector3(0.02, 0.05, 0.1)
	for x in [-0.135, 0.135]:
		_add(ear, trim, Vector3(x, 1.0, -0.12))
	body = bike_body
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
	exhaust.position = Vector3(0, 0.6, 1.1)
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


## Lofts [constant HULL_PROFILE] into a smooth closed hull. Each slice is a
## rounded-square cross-section; UV.x runs round it and UV.y nose to tail.
static func _hull_mesh() -> ArrayMesh:
	var slices: Array = []
	var steps := 4
	for k in HULL_PROFILE.size() - 1:
		var a: Array = HULL_PROFILE[maxi(k - 1, 0)]
		var b: Array = HULL_PROFILE[k]
		var c2: Array = HULL_PROFILE[k + 1]
		var d: Array = HULL_PROFILE[mini(k + 2, HULL_PROFILE.size() - 1)]
		for n in steps:
			var t := float(n) / steps
			var row: Array[float] = []
			for j in 4:
				row.append(cubic_interpolate(b[j], c2[j], a[j], d[j], t))
			slices.append(row)
	slices.append(HULL_PROFILE[-1])
	var ring := 24
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var z0: float = HULL_PROFILE[0][0]
	var z1: float = HULL_PROFILE[-1][0]
	var pts: Array = []
	for s in slices:
		var row: Array[Vector3] = []
		var mid: float = (s[2] + s[3]) * 0.5
		var h: float = (s[3] - s[2]) * 0.5
		for r in ring + 1:
			var th := r * TAU / ring
			var cx := cos(th)
			var sy := sin(th)
			row.append(Vector3(s[1] * signf(cx) * pow(absf(cx), 0.6), mid + h * signf(sy) * pow(absf(sy), 0.6), s[0]))
		pts.append(row)
	for i in pts.size() - 1:
		var va: float = (slices[i][0] - z0) / (z1 - z0)
		var vb: float = (slices[i + 1][0] - z0) / (z1 - z0)
		for r in ring:
			var ua := float(r) / ring
			var ub := float(r + 1) / ring
			var quad := [[pts[i][r], ua, va], [pts[i + 1][r], ua, vb], [pts[i + 1][r + 1], ub, vb], [pts[i][r + 1], ub, va]]
			for idx in [0, 2, 1, 0, 3, 2]:
				st.set_uv(Vector2(quad[idx][1], quad[idx][2]))
				st.add_vertex(quad[idx][0])
	# Cap the tail.
	var last: Array = pts[-1]
	var centre := Vector3(0, (slices[-1][2] + slices[-1][3]) * 0.5, slices[-1][0])
	for r in ring:
		for v in [centre, last[r + 1], last[r]]:
			st.set_uv(Vector2(0.75, 1.0))
			st.add_vertex(v)
	st.generate_normals()
	return st.commit()


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
