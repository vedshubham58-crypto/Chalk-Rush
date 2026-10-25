extends SceneTree
## Renders a few frames of a round to PNGs so the look can be checked without a phone.
## Run under a virtual display: xvfb-run godot --path godot -s tests/shot.gd -- <level> <seconds> <out.png>

func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	var li := int(args[0]) if args.size() > 0 else 0
	var secs := float(args[1]) if args.size() > 1 else 6.0
	var out := args[2] if args.size() > 2 else "/tmp/shot.png"
	var lv := GameData.level(li)
	var a := GameData.arena(lv.code)
	var g := NeonGame.new({"size": a.size, "mask": a.mask, "bots": lv.bots, "seed": 11, "countdown": 0.0})
	var view := ArenaView.new()
	root.add_child(view)
	view.build(g, lv.accent, load("res://fonts/Exo2.ttf"))
	var steps := int(secs * 30)
	for k in steps:
		# Ride out of home, turn twice and come back, again and again.
		if k % 20 == 0:
			g.steer((g.me.dir + (1 if (k / 20) % 4 != 3 else 3)) % 4)
		g.update(1.0 / 30.0)
		g.events.clear()
		view.sync(1.0 / 30.0)
	for k in 6:
		g.update(1.0 / 30.0)
		view.sync(1.0 / 30.0)
		await process_frame
	root.get_viewport().get_texture().get_image().save_png(out)
	print("saved ", out)
	quit()
