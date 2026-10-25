extends SceneTree
## Renders one bike up close, to check its look.

func _initialize() -> void:
	var out := OS.get_cmdline_user_args()[0]
	var we := WorldEnvironment.new()
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.02, 0.02, 0.06)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.35, 0.4, 0.6)
	env.ambient_light_energy = 0.6
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.glow_enabled = true
	env.glow_hdr_threshold = 0.9
	env.glow_blend_mode = Environment.GLOW_BLEND_MODE_ADDITIVE
	we.environment = env
	root.add_child(we)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-55, 25, 0)
	root.add_child(sun)
	var floor := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(10, 10)
	floor.mesh = pm
	floor.material_override = LightBike.shell_mat(Color(0.03, 0.035, 0.07), 0.7, 0.15)
	root.add_child(floor)
	for k in 2:
		var b := LightBike.new()
		b.setup(Color("ff4fd8") if k else Color("00e5ff"), "Pip" if k else "You", load("res://fonts/Exo2.ttf"))
		b.position = Vector3(k * 1.6 - 0.8, 0, 0)
		b.rotation.y = -0.9 + k * 1.2
		b.crown.visible = k == 0
		root.add_child(b)
		b.animate(0.1, 0.0, 0.0, k == 1, false, false)
	var cam := Camera3D.new()
	root.add_child(cam)
	cam.look_at_from_position(Vector3(0, 2.2, 3.2), Vector3(0, 0.5, 0))
	for k in 5:
		await process_frame
	root.get_viewport().get_texture().get_image().save_png(out)
	quit()
