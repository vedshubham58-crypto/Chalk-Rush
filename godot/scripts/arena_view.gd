class_name ArenaView
extends Node3D
## Draws a NeonGame in 3D: the country as a slab of glass tiles that rise up in
## colour when claimed, light walls behind every bike, coins, power-ups and
## effects. One cell is one unit; game y runs along world +Z.

const TILE_SHADER := preload("res://shaders/tile.gdshader")
const TRAIL_SHADER := preload("res://shaders/trail.gdshader")
const VOID_SHADER := preload("res://shaders/void.gdshader")
const OWNED_H := 0.42
const EMPTY_H := 0.12
const TRAIL_H := 0.85
const TRAIL_W := 0.16
const BIKE_SCALE := 1.7
const POWER_COLORS := [Color("ffe14f"), Color("4fb7ff"), Color("ff4fd8"), Color("9bf6ff"), Color("ff5f5f")]

var game: NeonGame
var accent := Color.CYAN
var font: Font
var camera: Camera3D
var env: Environment
var bikes: Array[LightBike] = []
## Set false for the menu backdrop: the camera drifts over the map instead of chasing you.
var follow_me := true
## The menu backdrop: a wider, slower camera on your demo rider.
var showcase := false

var _tiles: MultiMesh
var _tile_mat: ShaderMaterial
var _tile_index := PackedInt32Array()  # cell -> instance, -1 outside
var _trails: MultiMesh
var _now := 0.0
var _yaw: Array[float] = []
var _lean: Array[float] = []
var _ride_y: Array[float] = []
var _coins: Array[MeshInstance3D] = []
var _powers: Array[Node3D] = []
var _cam_target := Vector3.ZERO
var _shake := 0.0
var _zoom := 1.0
var _drift := 0.0
var _void: MeshInstance3D


func build(p_game: NeonGame, p_accent: Color, p_font: Font) -> void:
	game = p_game
	accent = p_accent
	font = p_font
	_build_environment()
	_build_tiles()
	_build_border()
	_build_trails()
	for p in game.players:
		var b := LightBike.new()
		b.setup(p.color, p.display_name(), font)
		b.scale = Vector3.ONE * BIKE_SCALE
		add_child(b)
		bikes.append(b)
		_yaw.append(_dir_yaw(p.dir))
		_lean.append(0.0)
		_ride_y.append(OWNED_H)
	for k in NeonGame.MAX_FIELD_COINS:
		_coins.append(_make_coin())
	for k in NeonGame.MAX_POWER_UPS:
		_powers.append(_make_power())
	var c := Vector3(game.center_x + 0.5, 0, game.center_y + 0.5)
	_cam_target = Vector3(game.me.x, 0, game.me.y) if follow_me else c
	_place_camera(0.0, true)


func _build_environment() -> void:
	env = Environment.new()
	env.background_mode = Environment.BG_SKY
	var sky := Sky.new()
	var sm := ProceduralSkyMaterial.new()
	sm.sky_top_color = Color(0.02, 0.01, 0.06)
	sm.sky_horizon_color = accent.darkened(0.55).lerp(Color(0.35, 0.05, 0.4), 0.5)
	sm.ground_bottom_color = Color(0, 0, 0)
	sm.ground_horizon_color = accent.darkened(0.7)
	sm.sky_curve = 0.08
	sm.sun_angle_max = 0.0
	sky.sky_material = sm
	env.sky = sky
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.35, 0.4, 0.6)
	env.ambient_light_energy = 0.6
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.tonemap_white = 6.0
	env.glow_enabled = true
	env.glow_intensity = 0.9
	env.glow_strength = 1.0
	env.glow_bloom = 0.05
	env.glow_hdr_threshold = 0.9
	env.glow_blend_mode = Environment.GLOW_BLEND_MODE_ADDITIVE
	env.set_glow_level(0, 0.0)
	env.set_glow_level(1, 1.0)
	env.set_glow_level(2, 1.0)
	env.set_glow_level(3, 0.8)
	env.set_glow_level(4, 0.5)
	env.fog_enabled = true
	env.fog_light_color = accent.darkened(0.8)
	env.fog_density = 0.006
	env.fog_sky_affect = 0.0
	var we := WorldEnvironment.new()
	we.environment = env
	add_child(we)

	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-55, 25, 0)
	sun.light_color = Color(0.75, 0.8, 1.0)
	sun.light_energy = 0.9
	add_child(sun)

	camera = Camera3D.new()
	camera.fov = 52
	camera.far = 400
	add_child(camera)

	# A grid floor far below, so the country floats in the dark.
	_void = MeshInstance3D.new()
	var plane := PlaneMesh.new()
	plane.size = Vector2(900, 900)
	_void.mesh = plane
	var vm := ShaderMaterial.new()
	vm.shader = VOID_SHADER
	vm.set_shader_parameter("line_color", accent)
	_void.material_override = vm
	_void.position = Vector3(game.size / 2.0, -9.0, game.size / 2.0)
	add_child(_void)


func _build_tiles() -> void:
	var box := BoxMesh.new()
	box.size = Vector3(1, 1, 1)
	_tile_mat = ShaderMaterial.new()
	_tile_mat.shader = TILE_SHADER
	_tile_mat.set_shader_parameter("grid_color", accent)
	_tile_mat.set_shader_parameter("owned_height", OWNED_H)
	_tile_mat.set_shader_parameter("empty_height", EMPTY_H)
	_tiles = MultiMesh.new()
	_tiles.transform_format = MultiMesh.TRANSFORM_3D
	_tiles.use_colors = true
	_tiles.use_custom_data = true
	_tiles.mesh = box
	_tiles.instance_count = game.area
	_tile_index.resize(game.cells)
	_tile_index.fill(-1)
	for k in game.area:
		var i := game.playable_cells[k]
		_tile_index[i] = k
		_tiles.set_instance_transform(k, Transform3D(Basis(), Vector3(i % game.size + 0.5, 0, i / game.size + 0.5)))
		_set_tile(k, i, -100.0)
	var mmi := MultiMeshInstance3D.new()
	mmi.multimesh = _tiles
	mmi.material_override = _tile_mat
	add_child(mmi)
	game.changed.clear()


func _set_tile(k: int, i: int, at: float) -> void:
	var o := game.owner[i]
	if o == NeonGame.NONE:
		_tiles.set_instance_custom_data(k, Color(0, at, 0, 0))
	else:
		_tiles.set_instance_color(k, game.players[o].color)
		_tiles.set_instance_custom_data(k, Color(1, at, 0, 0))


## Glowing rails along the country's outline.
func _build_border() -> void:
	var xf: Array[Transform3D] = []
	var s := game.size
	for i in game.playable_cells:
		var x := i % s
		var y := i / s
		for d in 4:
			var nx: int = x + NeonGame.DX[d]
			var ny: int = y + NeonGame.DY[d]
			if game.in_bounds(nx, ny):
				continue
			var c := Vector3(x + 0.5 + NeonGame.DX[d] * 0.5, 0.3, y + 0.5 + NeonGame.DY[d] * 0.5)
			var basis := Basis() if d % 2 == 1 else Basis(Vector3.UP, PI / 2)
			xf.append(Transform3D(basis, c))
	var rail := BoxMesh.new()
	rail.size = Vector3(1.04, 0.14, 0.14)
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.mesh = rail
	mm.instance_count = xf.size()
	for k in xf.size():
		mm.set_instance_transform(k, xf[k])
	var mmi := MultiMeshInstance3D.new()
	mmi.multimesh = mm
	mmi.material_override = LightBike.glow_mat(accent, 3.0)
	add_child(mmi)


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
	m.top_radius = 0.3
	m.bottom_radius = 0.3
	m.height = 0.08
	var mi := MeshInstance3D.new()
	mi.mesh = m
	var mat := LightBike.glow_mat(Color(1.0, 0.8, 0.2), 1.6)
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
	s.radius = 0.4
	s.height = 0.8
	s.radial_segments = 4
	s.rings = 2
	gem.mesh = s
	gem.name = "gem"
	gem.position.y = 1.0
	n.add_child(gem)
	var beam := MeshInstance3D.new()
	var c := CylinderMesh.new()
	c.top_radius = 0.06
	c.bottom_radius = 0.25
	c.height = 6.0
	c.cap_top = false
	c.cap_bottom = false
	beam.mesh = c
	beam.name = "beam"
	beam.position.y = 3.0
	n.add_child(beam)
	var ring := MeshInstance3D.new()
	var t := TorusMesh.new()
	t.inner_radius = 0.55
	t.outer_radius = 0.65
	ring.mesh = t
	ring.name = "ring"
	ring.position.y = EMPTY_H + 0.03
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
	bm.albedo_color = Color(c, 0.18)
	(n.get_node("beam") as MeshInstance3D).material_override = bm
	n.set_meta("type", type)


static func _dir_yaw(d: int) -> float:
	return -PI / 2.0 - d * PI / 2.0


func shake(amount: float) -> void:
	_shake = maxf(_shake, amount)


func cell_pos(x: float, y: float, h := OWNED_H) -> Vector3:
	return Vector3(x, h, y)


func sync(dt: float) -> void:
	_now += dt
	_tile_mat.set_shader_parameter("now", _now)
	for i in game.changed:
		var k := _tile_index[i]
		if k >= 0:
			_set_tile(k, i, _now)
	game.changed.clear()
	_sync_trails()
	_sync_bikes(dt)
	_sync_pickups()
	_place_camera(dt, false)


func _sync_trails() -> void:
	var buf := PackedFloat32Array()
	var n := 0
	var s := game.size
	for p in game.players:
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
		pts.append(Vector2(p.x, p.y))
		for k in pts.size() - 1:
			var a := pts[k]
			var b := pts[k + 1]
			var len := a.distance_to(b)
			if len < 0.01:
				continue
			if n >= _trails.instance_count:
				break
			var mid := (a + b) * 0.5
			var along_x := absf(b.x - a.x) > absf(b.y - a.y)
			var sx := len + TRAIL_W if along_x else TRAIL_W
			var sz := TRAIL_W if along_x else len + TRAIL_W
			# Transform rows: basis is a plain scale, then position.
			buf.append_array([sx, 0, 0, mid.x, 0, TRAIL_H, 0, 0.05 + TRAIL_H * 0.5, 0, 0, sz, mid.y, c.r, c.g, c.b, 1.0])
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
		var turn := clampf(diff, -1.0, 1.0)
		_yaw[k] = lerp_angle(_yaw[k], target, minf(dt * 14.0, 1.0))
		_lean[k] = lerpf(_lean[k], -turn * 0.45, minf(dt * 10.0, 1.0))
		var i := p.cell_y * game.size + p.cell_x
		var ground := OWNED_H if game.owner[i] != NeonGame.NONE else EMPTY_H
		_ride_y[k] = lerpf(_ride_y[k], ground, minf(dt * 12.0, 1.0))
		b.position = Vector3(p.x, _ride_y[k], p.y)
		b.rotation.y = _yaw[k]
		b.crown.visible = p == leader and p.land > 0
		b.animate(dt, game.speed_of(p) if game.countdown <= 0.0 else 0.0, _lean[k], p.shielded(), p.boost > 0.0 or p.grinding, p.frozen > 0.0)


func _sync_pickups() -> void:
	var t := _now
	var s := game.size
	for k in _coins.size():
		var c := _coins[k]
		c.visible = k < game.field_coins.size()
		if c.visible:
			var i := game.field_coins[k]
			c.position = Vector3(i % s + 0.5, EMPTY_H + 0.55 + sin(t * 3.0 + k) * 0.08, i / s + 0.5)
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
		gem.position.y = 1.0 + sin(t * 2.5 + k) * 0.15
		var ring := n.get_node("ring") as Node3D
		ring.scale = Vector3.ONE * (1.0 + 0.12 * sin(t * 4.0 + k))


func _place_camera(dt: float, snap: bool) -> void:
	var focus: Vector3
	if follow_me:
		var p := game.me
		focus = Vector3(p.x, 0, p.y)
		# A little ahead of the bike, so you see where you're going.
		focus += Vector3(NeonGame.DX[p.dir], 0, NeonGame.DY[p.dir]) * 2.5
		var want_zoom := 1.0 + clampf(game.percent(p) / 60.0, 0.0, 0.5) + (0.12 if p.boost > 0.0 else 0.0)
		if showcase:
			want_zoom = 0.75
			focus.z -= 2.5
		_zoom = lerpf(_zoom, want_zoom, minf(dt * 2.0, 1.0))
	else:
		_drift += dt * 0.12
		var r := game.size * 0.22
		focus = Vector3(game.center_x + cos(_drift) * r, 0, game.center_y + sin(_drift * 1.3) * r * 0.6)
		_zoom = 1.9
	_cam_target = focus if snap else _cam_target.lerp(focus, minf(dt * 5.0, 1.0))
	var offset := Vector3(0, 15.0, 12.0) * _zoom
	var jolt := Vector3.ZERO
	if _shake > 0.0:
		jolt = Vector3(randf_range(-1, 1), randf_range(-1, 1), 0) * _shake * 0.35
		_shake = maxf(_shake - dt * 2.5, 0.0)
	camera.look_at_from_position(_cam_target + offset + jolt, _cam_target + jolt * 0.5, Vector3.UP)
	_void.material_override.set_shader_parameter("focus", _cam_target)


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
	m.size = Vector3(0.14, 0.14, 0.14)
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
