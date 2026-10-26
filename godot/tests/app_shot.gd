extends SceneTree
## Opens the real app, optionally starts a round, and saves screenshots.
## Args: <out prefix> <mode: menu|play|results|story> <seconds...>
## In story mode the intro moves on a page after each screenshot.

func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	var out: String = args[0]
	var mode: String = args[1]
	var main = load("res://scenes/main.tscn").instantiate()
	root.add_child(main)
	await process_frame
	if mode == "menu":
		main.show_menu()
	if mode == "story":
		main.show_story()
	if mode == "play" or mode == "results":
		main.start_round()
	if mode == "results":
		for k in 200:
			await process_frame
		main.game.elapsed = main.game.round_seconds
	var t := 0.0
	for k in range(2, args.size()):
		var at := float(args[k])
		while t < at:
			if mode != "menu" and main.game and int(t * 10) % 7 == 0:
				main.game.steer((main.game.me.dir + 1) % 4)
			await process_frame
			t += 1.0 / 30.0
		root.get_viewport().get_texture().get_image().save_png("%s_%s.png" % [out, args[k]])
		print("saved ", args[k])
		if mode == "story":
			main.root_ui.get_child(0)._advance()
	quit()
