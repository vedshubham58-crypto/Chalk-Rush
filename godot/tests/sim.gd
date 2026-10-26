extends SceneTree
## Plays whole rounds with no screen and checks the rules hold up.
## Run: godot --headless --path godot -s tests/sim.gd

var failures := 0


func check(ok: bool, what: String) -> void:
	if not ok:
		failures += 1
		print("FAIL: ", what)


func _init() -> void:
	var t0 := Time.get_ticks_msec()
	for li in GameData.LEVELS.size():
		var lv := GameData.level(li)
		var a := GameData.arena(lv.code)
		var g := NeonGame.new({"size": a.size, "mask": a.mask, "bots": lv.bots, "seed": 7 + li, "countdown": 0.0,
			"min_bold": lv.min_bold, "max_bold": lv.max_bold, "bot_speed": lv.bot_speed})
		# "You" just ride around in a square loop so the round goes the distance.
		var steps := 0
		var kos := 0
		var caps := 0
		var start := Time.get_ticks_msec()
		while not g.is_over() and steps < 45 * 60:
			if steps % 30 == 0:
				g.steer((g.me.dir + 1) % 4)
			g.update(1.0 / 60.0)
			for e in g.events:
				if e.type == "ko": kos += 1
				if e.type == "captured": caps += 1
			g.events.clear()
			g.changed.clear()
			steps += 1
		var ms := Time.get_ticks_msec() - start
		# Land totals add up and nobody owns a cell outside the land.
		var counted := {}
		for i in g.cells:
			if g.owner[i] != NeonGame.NONE:
				check(g.playable[i] == 1, "%s: owned cell outside the land" % lv.name)
				counted[g.owner[i]] = counted.get(g.owner[i], 0) + 1
		for p in g.players:
			check(counted.get(p.id, 0) == p.land, "%s: %s land count %d vs %d" % [lv.name, p.display_name(), p.land, counted.get(p.id, 0)])
			if p.alive:
				check(g.in_bounds(p.cell_x, p.cell_y), "%s: %s outside the land" % [lv.name, p.display_name()])
		print("STATS %s size=%d bots=%d steps=%d kos=%d captures=%d me=%.1f%% top=%.1f%% %dms (%.2f ms/frame)" % [
			lv.name, g.size, lv.bots, steps, kos, caps, g.percent(g.me), g.percent(g.ranking()[0]), ms, float(ms) / steps])
		check(caps > 10, "%s: bots hardly captured anything" % lv.name)

	# Your cut knocks out a shielded bot.
	var g2 := NeonGame.new({"size": 30, "bots": 1, "seed": 3, "countdown": 0.0, "power_ups": false})
	var bot := g2.players[1]
	bot.shield = 99.0
	var i := 15 * 30 + 15
	g2.trail_owner[i] = bot.id
	bot.trail.append(i)
	g2._enter(g2.me, 15, 15)
	check(not bot.alive, "your cut should knock out a shielded bot")

	print("sim done in %d ms, failures: %d" % [Time.get_ticks_msec() - t0, failures])
	quit(1 if failures > 0 else 0)
