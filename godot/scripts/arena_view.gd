class_name ArenaView
extends Node3D
## Draws a NeonGame in 3D, seen from a chase camera behind your bike.
## The country is one smooth glowing floor (claimed land rises in its owner's
## colour), set in a neon city under a synthwave sky. Light walls trail every
## bike. One cell is one unit; game y runs along world +Z.

const FLOOR_SHADER := preload("res://shaders/floor.gdshader")
const TRAIL_SHADER := preload("res://shaders/trail.gdshader")
const SKY_SHADER := preload("res://shaders/sky.gdshader")
const CITY_SHADER := preload("res://shaders/city.gdshader")
const LAND_H := 0.35
const TRAIL_H := 1.15
const TRAIL_W := 0.07
const BIKE_SCALE := 1.25
const CLAIM_SECONDS := 0.35
const POWER_COLORS := [Color("ffe14f"), Color("4fb7ff"), Color("ff4fd8"), Color("9bf6ff"), Color("ff5f5f")]
const CITY_NEONS := [Color("ff2bd6"), Color("00e5ff"), Color("b57cff"), Color("ff8a2b"), Color("39ff88")]

var game: NeonGame
var accent := Color.CYAN
var font: Font
var camera: Camera3D
var env: Environment
var bikes: Array[LightBike] = []
## The menu backdrop: the camera circles your demo rider instead of chasing.
var showcase := false
## The picture of who owns what: rgb = owner colour, a = how claimed. The minimap shows it too.
var land_image: Image
var land_texture: ImageTexture
var mask_texture: ImageTexture

var _floor_mat: ShaderMaterial
var _claiming := {}  # cell -> [from alpha, to alpha, progress]
var _trails: MultiMesh
var _now := 0.0
var _yaw: Array[float] = []
var _lean: Array[float] = []
var _ride_y: Array[float] = []
var _coins: Array[MeshInstance3D] = []
var _powers: Array[Node3D] = []
var _cam_yaw := 0.0
var _cam_pos := Vector3.ZERO
var _cam_look := Vector3.ZERO
var _orbit := 0.0
var _shake := 0.0
var _speed_lines: CPUParticles3D


func build(p_game: NeonGame, p_accent: Color, p_font: Font) -> void:
	game = p_game
	accent = p_accent
	font = p_font
	_build_environment()
	_build_floor()
	_build_city()
	_build_trails()
	for p in game.players:
		var b := LightBike.new()
		b.setup(p.color, p.display_name(), font)
		b.scale = Vector3.ONE * BIKE_SCALE
		add_child(b)
		bikes.append(b)
		_yaw.append(_dir_yaw(p.dir))
		_lean.append(0.0)
		_ride_y.append(LAND_H)
	for k in NeonGame.MAX_FIELD_COINS:
		_coins.append(_make_coin())
	for k in NeonGame.MAX_POWER_UPS:
		_powers.append(_make_power())
	_cam_yaw = _yaw[0]
	_place_camera(0.0, true)


func _build_environment() -> void:
	env = Environment.new()
	env.background_mode = Environment.BG_SKY
	var sky := Sky.new()
	var sm := ShaderMaterial.new()
	sm.shader = SKY_SHADER
	sky.sky_material = sm
	env.sky = sky
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.55, 0.45, 0.85)
	env.ambient_light_energy = 0.7
	env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.tonemap_white = 6.0
	env.glow_enabled = true
	env.glow_intensity = 0.85
	env.glow_strength = 1.0
	env.glow_bloom = 0.04
	env.glow_hdr_threshold = 0.95
	env.glow_blend_mode = Environment.GLOW_BLEND_MODE_ADDITIVE
	env.set_glow_level(0, 0.0)
	env.set_glow_level(1, 1.0)
	env.set_glow_level(2, 1.0)
	env.set_glow_level(3, 0.8)
	env.set_glow_level(4, 0.4)
	# Haze in the distance, so far-off towers melt into the pink horizon.
	env.fog_enabled = true
	env.fog_light_color = Color(0.45, 0.12, 0.45)
	env.fog_density = 0.012
	env.fog_sky_affect = 0.0
	var we := WorldEnvironment.new()
	we.environment = env
	add_child(we)

	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-35, 180, 0)
	sun.light_color = Color(1.0, 0.6, 0.75)
	sun.light_energy = 0.9
	add_child(sun)
	var fill := DirectionalLight3D.new()
	fill.rotation_degrees = Vector3(-60, 20, 0)
	fill.light_color = Color(0.5, 0.7, 1.0)
	fill.light_energy = 0.5
	add_child(fill)

	camera = Camera3D.new()
	camera.fov = 70
	camera.near = 0.1
	camera.far = 500
	add_child(camera)

	# Streaks rushing past the camera when you go fast.
	_speed_lines = CPUParticles3D.new()
	_speed_lines.amount = 60
	_speed_lines.lifetime = 0.35
	_speed_lines.local_coords = true
	_speed_lines.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	_speed_lines.emission_box_extents = Vector3(4, 2.5, 1)
	_speed_lines.position = Vector3(0, 0, -9)
	_speed_lines.direction = Vector3(0, 0, 1)
	_speed_lines.spread = 0.0
	_speed_lines.gravity = Vector3.ZERO
	_speed_lines.initial_velocity_min = 30.0
	_speed_lines.initial_velocity_max = 40.0
	var streak := BoxMesh.new()
	streak.size = Vector3(0.015, 0.015, 1.2)
	_speed_lines.mesh = streak
	var sl := StandardMaterial3D.new()
	sl.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	sl.albedo_color = Color(1, 1, 1, 0.5)
	sl.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	_speed_lines.material_override = sl
	_speed_lines.emitting = false
	camera.add_child(_speed_lines)


func _build_floor() -> void:
	var s := game.size
	land_image = Image.create(s, s, false, Image.FORMAT_RGBA8)
	land_image.fill(Color(0, 0, 0, 0))
	for i in game.cells:
		var o := game.owner[i]
		if o != NeonGame.NONE:
			land_image.set_pixel(i % s, i / s, Color(game.players[o].color, 1.0))
	land_texture = ImageTexture.create_from_image(land_image)
	var mask := Image.create(s, s, false, Image.FORMAT_L8)
	for i in game.cells:
		if game.playable[i]:
			mask.set_pixel(i % s, i / s, Color.WHITE)
	mask_texture = ImageTexture.create_from_image(mask)
	game.changed.clear()

	var plane := PlaneMesh.new()
	plane.size = Vector2(s, s)
	plane.subdivide_width = s - 1
	plane.subdivide_depth = s - 1
	_floor_mat = ShaderMaterial.new()
	_floor_mat.shader = FLOOR_SHADER
	_floor_mat.set_shader_parameter("land_tex", land_texture)
	_floor_mat.set_shader_parameter("mask_tex", mask_texture)
	_floor_mat.set_shader_parameter("size", float(s))
	_floor_mat.set_shader_parameter("land_height", LAND_H)
	_floor_mat.set_shader_parameter("accent", accent)
	var mi := MeshInstance3D.new()
	mi.mesh = plane
	mi.material_override = _floor_mat
	mi.position = Vector3(s / 2.0, 0, s / 2.0)
	mi.extra_cull_margin = 2.0
	add_child(mi)

	# The country stands on a block, so its edge reads as a cliff over the city.
	var under := MeshInstance3D.new()
	var slab := PlaneMesh.new()
	slab.size = Vector2(s * 6, s * 6)
	under.mesh = slab
	var sea := StandardMaterial3D.new()
	sea.albedo_color = Color(0.09, 0.05, 0.2)
	sea.metallic = 0.9
	sea.roughness = 0.08
	sea.emission_enabled = true
	sea.emission = Color(0.12, 0.04, 0.2)
	under.material_override = sea
	under.position = Vector3(s / 2.0, -2.5, s / 2.0)
	add_child(under)
	_build_cliffs()


## Glowing rails and a dark wall all round the country's outline.
func _build_cliffs() -> void:
	var rails: Array[Transform3D] = []
	var walls: Array[Transform3D] = []
	var s := game.size
	for i in game.playable_cells:
		var x := i % s
		var y := i / s
		for d in 4:
			if game.in_bounds(x + NeonGame.DX[d], y + NeonGame.DY[d]):
				continue
			var c := Vector3(x + 0.5 + NeonGame.DX[d] * 0.5, 0, y + 0.5 + NeonGame.DY[d] * 0.5)
			var basis := Basis() if d % 2 == 1 else Basis(Vector3.UP, PI / 2)
			rails.append(Transform3D(basis, c + Vector3(0, 0.12, 0)))
			walls.append(Transform3D(basis, c + Vector3(0, -1.25, 0)))
	var rail := BoxMesh.new()
	rail.size = Vector3(1.02, 0.1, 0.1)
	_multi(rail, rails, LightBike.glow_mat(accent, 3.5))
	var wall := BoxMesh.new()
	wall.size = Vector3(1.02, 2.5, 0.1)
	var wm := LightBike.shell_mat(Color(0.08, 0.05, 0.16), 0.8, 0.2)
	wm.emission_enabled = true
	wm.emission = accent.darkened(0.6)
	wm.emission_energy_multiplier = 0.4
	_multi(wall, walls, wm)


func _multi(mesh: Mesh, xf: Array[Transform3D], mat: Material, colors: Array[Color] = []) -> MultiMesh:
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.use_colors = not colors.is_empty()
	mm.mesh = mesh
	mm.instance_count = xf.size()
	for k in xf.size():
		mm.set_instance_transform(k, xf[k])
		if mm.use_colors:
			mm.set_instance_color(k, colors[k])
	var mmi := MultiMeshInstance3D.new()
	mmi.multimesh = mm
	mmi.material_override = mat
	add_child(mmi)
	return mm


## A ring of neon tower blocks round the country, so you ride through a city at night.
func _build_city() -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = game.size * 31 + 7
	var s := float(game.size)
	var c := Vector2(s / 2.0, s / 2.0)
	var xf: Array[Transform3D] = []
	var cols: Array[Color] = []
	for k in 260:
		var a := rng.randf() * TAU
		var r := s * 0.72 + 12.0 + rng.randf() * s * 0.9 + rng.randf() * 30.0
		var p := c + Vector2(cos(a), sin(a)) * r
		var w := rng.randf_range(3.0, 9.0)
		var d := rng.randf_range(3.0, 9.0)
		var h := rng.randf_range(4.0, 16.0) + (rng.randf() ** 3) * 40.0
		var basis := Basis().scaled(Vector3(w, h, d))
		xf.append(Transform3D(basis, Vector3(p.x, -2.5 + h / 2.0, p.y)))
		cols.append(CITY_NEONS[rng.randi() % CITY_NEONS.size()])
	var box := BoxMesh.new()
	var mat := ShaderMaterial.new()
	mat.shader = CITY_SHADER
	_multi(box, xf, mat, cols)


func _build_trails() -> void:
	var box := BoxMesh.new()
	box.size = Vector3(1, 1, 1)
	var mat := ShaderMaterial.new()
	mat.shader = TRAIL_SHADER
	_trails = MultiMesh.new()
	_trails.transform_format = MultiMesh.TRANSFORM_3D
	_trails.use_colors = true
	_trails.mesh = box
	_trails.instance_count = 3000
	_trails.visible_instance_count = 0
	var mmi := MultiMeshInstance3D.new()
	mmi.multimesh = _trails
	mmi.material_override = mat
	mmi.custom_aabb = AABB(Vector3(-10, -5, -10), Vector3(game.size + 20, 20, game.size + 20))
	add_child(mmi)


func _make_coin() -> MeshInstance3D:
	var m := CylinderMesh.new()
	m.top_radius = 0.32
	m.bottom_radius = 0.32
	m.height = 0.08
	var mi := MeshInstance3D.new()
	mi.mesh = m
	var mat := LightBike.glow_mat(Color(1.0, 0.8, 0.2), 1.8)
	mat.metallic = 1.0
	mat.roughness = 0.2
	mi.material_override = mat
	mi.visible = false
	add_child(mi)
	return mi


func _make_power() -> Node3D:
	var n := Node3D.new()
	var gem := MeshInstance3D.new()
	var s := SphereMesh.new()
	s.radius = 0.45
	s.height = 0.9
	s.radial_segments = 4
	s.rings = 2
	gem.mesh = s
	gem.name = "gem"
	gem.position.y = 1.1
	n.add_child(gem)
	var beam := MeshInstance3D.new()
	var c := CylinderMesh.new()
	c.top_radius = 0.05
	c.bottom_radius = 0.3
	c.height = 9.0
	c.cap_top = false
	c.cap_bottom = false
	beam.mesh = c
	beam.name = "beam"
	beam.position.y = 4.5
	n.add_child(beam)
	var ring := MeshInstance3D.new()
	var t := TorusMesh.new()
	t.inner_radius = 0.6
	t.outer_radius = 0.7
	ring.mesh = t
	ring.name = "ring"
	ring.position.y = 0.06
	n.add_child(ring)
	n.visible = false
	add_child(n)
	return n


func _style_power(n: Node3D, type: int) -> void:
	var c: Color = POWER_COLORS[type]
	(n.get_node("gem") as MeshInstance3D).material_override = LightBike.glow_mat(c, 3.0)
	(n.get_node("ring") as MeshInstance3D).material_override = LightBike.glow_mat(c, 2.0)
	var bm := StandardMaterial3D.new()
	bm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	bm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	bm.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
	bm.cull_mode = BaseMaterial3D.CULL_DISABLED
	bm.albedo_color = Color(c, 0.2)
	(n.get_node("beam") as MeshInstance3D).material_override = bm
	n.set_meta("type", type)


static func _dir_yaw(d: int) -> float:
	return -PI / 2.0 - d * PI / 2.0


func shake(amount: float) -> void:
	_shake = maxf(_shake, amount)


## How high the ground is at cell (x, y): claimed land stands a little higher.
func ground_at(x: int, y: int) -> float:
	if x < 0 or y < 0 or x >= game.size or y >= game.size:
		return 0.0
	return smoothstep(0.0, 1.0, land_image.get_pixel(x, y).a) * LAND_H


func sync(dt: float) -> void:
	_now += dt
	_floor_mat.set_shader_parameter("now", _now)
	_sync_land(dt)
	_sync_trails()
	_sync_bikes(dt)
	_sync_pickups()
	_place_camera(dt, false)


## Newly claimed land fades up (and lost land fades down) over a moment.
func _sync_land(dt: float) -> void:
	var s := game.size
	for i in game.changed:
		var o := game.owner[i]
		var cur := land_image.get_pixel(i % s, i / s)
		if o == NeonGame.NONE:
			_claiming[i] = [cur.a, 0.0, 0.0]
		else:
			land_image.set_pixel(i % s, i / s, Color(game.players[o].color, cur.a))
			_claiming[i] = [cur.a, 1.0, 0.0]
	game.changed.clear()
	if _claiming.is_empty():
		return
	var done: Array = []
	for i in _claiming:
		var c: Array = _claiming[i]
		c[2] = minf(c[2] + dt / CLAIM_SECONDS, 1.0)
		var px := land_image.get_pixel(i % s, i / s)
		px.a = lerpf(c[0], c[1], c[2])
		land_image.set_pixel(i % s, i / s, px)
		if c[2] >= 1.0:
			done.append(i)
	for i in done:
		_claiming.erase(i)
	land_texture.update(land_image)


func _sync_trails() -> void:
	var buf := PackedFloat32Array()
	var n := 0
	var s := game.size
	for k in game.players.size():
		var p := game.players[k]
		if not p.alive or p.trail.is_empty():
			continue
		var c := p.color
		var pts: Array[Vector2] = []
		# Start at the edge of home, where the trail left it.
		var first := Vector2(p.trail[0] % s + 0.5, p.trail[0] / s + 0.5)
		var back := Vector2(p.x, p.y) if p.trail.size() == 1 else Vector2(p.trail[1] % s + 0.5, p.trail[1] / s + 0.5)
		pts.append(first + (first - back).normalized() * 0.5)
		for i in p.trail:
			pts.append(Vector2(i % s + 0.5, i / s + 0.5))
		# End just behind the rear wheel.
		var fwd := Vector2(NeonGame.DX[p.dir], NeonGame.DY[p.dir])
		pts.append(Vector2(p.x, p.y) - fwd * 0.5 * BIKE_SCALE)
		for q in pts.size() - 1:
			var a := pts[q]
			var b := pts[q + 1]
			var len := a.distance_to(b)
			if len < 0.01 or n >= _trails.instance_count:
				continue
			var mid := (a + b) * 0.5
			var along_x := absf(b.x - a.x) > absf(b.y - a.y)
			var sx := len + TRAIL_W if along_x else TRAIL_W
			var sz := TRAIL_W if along_x else len + TRAIL_W
			buf.append_array([sx, 0, 0, mid.x, 0, TRAIL_H, 0, TRAIL_H * 0.5, 0, 0, sz, mid.y, c.r, c.g, c.b, 1.0])
			n += 1
	if n > 0:
		buf.resize(_trails.instance_count * 16)
		_trails.buffer = buf
	_trails.visible_instance_count = n


func _sync_bikes(dt: float) -> void:
	var leader := game.ranking()[0]
	for k in game.players.size():
		var p := game.players[k]
		var b := bikes[k]
		b.visible = p.alive
		if not p.alive:
			continue
		var target := _dir_yaw(p.dir)
		var diff := angle_difference(_yaw[k], target)
		_yaw[k] = lerp_angle(_yaw[k], target, minf(dt * 12.0, 1.0))
		_lean[k] = lerpf(_lean[k], clampf(-diff, -1.0, 1.0) * 0.55, minf(dt * 9.0, 1.0))
		_ride_y[k] = lerpf(_ride_y[k], ground_at(p.cell_x, p.cell_y), minf(dt * 10.0, 1.0))
		var moving := game.countdown <= 0.0
		var bob := sin(_now * 18.0 + k) * 0.015 if moving else 0.0
		b.position = Vector3(p.x, _ride_y[k] + bob, p.y)
		b.rotation.y = _yaw[k]
		b.crown.visible = p == leader and p.land > 0
		b.label.visible = p != game.me or showcase
		var boosting := p.boost > 0.0 or p.grinding
		# Nose up a touch when boosting.
		b.body.rotation.x = lerpf(b.body.rotation.x, 0.08 if boosting else 0.0, minf(dt * 6.0, 1.0))
		b.animate(dt, game.speed_of(p) if moving else 0.0, _lean[k], p.shielded(), boosting, p.frozen > 0.0)


func _sync_pickups() -> void:
	var t := _now
	var s := game.size
	for k in _coins.size():
		var c := _coins[k]
		c.visible = k < game.field_coins.size()
		if c.visible:
			var i := game.field_coins[k]
			c.position = Vector3(i % s + 0.5, 0.6 + sin(t * 3.0 + k) * 0.1, i / s + 0.5)
			c.rotation = Vector3(PI / 2, 0, t * 2.5 + k)
	for k in _powers.size():
		var n := _powers[k]
		n.visible = k < game.power_ups.size()
		if not n.visible:
			continue
		var pu: Dictionary = game.power_ups[k]
		if n.get_meta("type", -1) != pu.type:
			_style_power(n, pu.type)
		n.position = Vector3(pu.x + 0.5, 0, pu.y + 0.5)
		var gem := n.get_node("gem") as Node3D
		gem.rotation.y = t * 2.0
		gem.position.y = 1.1 + sin(t * 2.5 + k) * 0.15
		var ring := n.get_node("ring") as Node3D
		ring.scale = Vector3.ONE * (1.0 + 0.12 * sin(t * 4.0 + k))


## The chase camera: low behind your bike, swinging round smoothly when you turn.
## While you're knocked out it circles the spot; in the menu it circles your rider.
func _place_camera(dt: float, snap: bool) -> void:
	var p := game.me
	var b := bikes[0]
	var bike_pos := b.position if p.alive or showcase else Vector3(p.x, 0, p.y)
	var pos: Vector3
	var look: Vector3
	if showcase:
		_orbit += dt * 0.35
		var off := Vector3(sin(_orbit), 0, cos(_orbit)) * 5.5
		pos = bike_pos + off + Vector3(0, 2.0, 0)
		look = bike_pos + Vector3(0, 0.9, 0)
	elif not p.alive:
		_orbit += dt * 0.5
		pos = bike_pos + Vector3(sin(_orbit), 0, cos(_orbit)) * 9.0 + Vector3(0, 6.0, 0)
		look = bike_pos
	else:
		var ease := minf(dt * 5.0, 1.0)
		_cam_yaw = _yaw[0] if snap else lerp_angle(_cam_yaw, _yaw[0], ease)
		var fwd := Vector3(-sin(_cam_yaw), 0, -cos(_cam_yaw))
		var fast := p.boost > 0.0 or p.grinding
		var dist := 6.6 if not fast else 7.4
		pos = bike_pos - fwd * dist + Vector3(0, 3.6, 0)
		look = bike_pos + fwd * 7.0 + Vector3(0, 0.6, 0)
		camera.fov = lerpf(camera.fov, 80.0 if fast else 70.0, minf(dt * 4.0, 1.0))
		_speed_lines.emitting = fast and game.countdown <= 0.0
	if snap:
		_cam_pos = pos
		_cam_look = look
	else:
		_cam_pos = _cam_pos.lerp(pos, minf(dt * 8.0, 1.0))
		_cam_look = _cam_look.lerp(look, minf(dt * 10.0, 1.0))
	var jolt := Vector3.ZERO
	if _shake > 0.0:
		jolt = Vector3(randf_range(-1, 1), randf_range(-1, 1), randf_range(-1, 1)) * _shake * 0.25
		_shake = maxf(_shake - dt * 2.5, 0.0)
	# Tip the horizon a little into turns.
	var up := Vector3.UP
	if p.alive and not showcase:
		up = Vector3.UP.rotated(Vector3(-sin(_cam_yaw), 0, -cos(_cam_yaw)), _lean[0] * 0.18)
	camera.look_at_from_position(_cam_pos + jolt, _cam_look + jolt * 0.5, up)


## A burst of glowing shards, for knockouts and captures.
func burst(at: Vector3, c: Color, count := 40, speed := 6.0, life := 0.9) -> void:
	var p := CPUParticles3D.new()
	p.one_shot = true
	p.explosiveness = 1.0
	p.amount = count
	p.lifetime = life
	p.position = at
	p.direction = Vector3.UP
	p.spread = 180.0
	p.initial_velocity_min = speed * 0.4
	p.initial_velocity_max = speed
	p.gravity = Vector3(0, -12, 0)
	p.angular_velocity_min = -360
	p.angular_velocity_max = 360
	p.scale_amount_min = 0.6
	p.scale_amount_max = 1.4
	var m := BoxMesh.new()
	m.size = Vector3(0.12, 0.12, 0.12)
	p.mesh = m
	p.material_override = LightBike.glow_mat(c.lightened(0.2), 5.0)
	var curve := Curve.new()
	curve.add_point(Vector2(0, 1))
	curve.add_point(Vector2(1, 0))
	p.scale_amount_curve = curve
	add_child(p)
	p.emitting = true
	get_tree().create_timer(life + 0.2).timeout.connect(p.queue_free)


## An expanding ring of light on the ground.
func ring(at: Vector3, c: Color, radius := 6.0, life := 0.6) -> void:
	var mi := MeshInstance3D.new()
	var t := TorusMesh.new()
	t.inner_radius = 0.9
	t.outer_radius = 1.0
	mi.mesh = t
	var m := LightBike.glow_mat(c, 4.0)
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mi.material_override = m
	mi.position = at + Vector3(0, 0.1, 0)
	mi.scale = Vector3.ONE * 0.3
	add_child(mi)
	var tw := create_tween().set_parallel(true)
	tw.tween_property(mi, "scale", Vector3(radius, 1, radius), life).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_CUBIC)
	tw.tween_property(m, "albedo_color:a", 0.0, life)
	tw.tween_property(m, "emission_energy_multiplier", 0.0, life)
	tw.chain().tween_callback(mi.queue_free)


## A bright flash of light, for knockouts.
func flash(at: Vector3, c: Color) -> void:
	var l := OmniLight3D.new()
	l.light_color = c
	l.light_energy = 6.0
	l.omni_range = 9.0
	l.position = at + Vector3(0, 1.5, 0)
	add_child(l)
	var tw := create_tween()
	tw.tween_property(l, "light_energy", 0.0, 0.5)
	tw.tween_callback(l.queue_free)
