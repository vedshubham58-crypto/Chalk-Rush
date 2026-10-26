class_name Promo
extends Node
## A round played by a bot for you, made to be recorded as a vertical Short.
##
## Started with user arguments after "--", for example:
##   godot --path godot --write-movie clip.avi --fixed-fps 30 --resolution 1080x1920 \
##     -- --promo --seed=7 --level=3 --rider=2 --seconds=24 --hook="Can you take 30%?" --cta="FREE on Google Play" --stats=clip.json
## It shows the hook line, lets the round run, then an end card, writes the
## round's stats as JSON and quits. tools/make_shorts.py drives it.

const OUTRO_SECONDS := 3.0

var opts := {}
var main: Node
var _outro_at := -1.0
var _clock := 0.0
var _peak := 0.0


static func wanted() -> bool:
	return "--promo" in OS.get_cmdline_user_args()


static func parse() -> Dictionary:
	var o := {"seed": 1, "level": 0, "rider": 0, "seconds": 24.0, "hook": "", "cta": "FREE on Google Play", "stats": ""}
	for a in OS.get_cmdline_user_args():
		if not a.begins_with("--") or not "=" in a:
			continue
		var k := a.substr(2, a.find("=") - 2)
		var v := a.substr(a.find("=") + 1)
		if k in ["seed", "level", "rider"]:
			o[k] = int(v)
		elif k == "seconds":
			o[k] = float(v)
		elif o.has(k):
			o[k] = v
	return o


func _init(p_main: Node) -> void:
	main = p_main
	opts = parse()
	process_mode = Node.PROCESS_MODE_ALWAYS


func _ready() -> void:
	# Play the asked land and rider for this clip only; nothing is saved.
	Save.level = clampi(opts.level, 0, GameData.LEVELS.size() - 1)
	Save.selected = clampi(opts.rider, 0, GameData.RIDERS.size() - 1)
	Save.nickname = "YOU"
	Save.sound = true
	main.start_round()
	if opts.hook != "":
		_hook(opts.hook)


func _process(dt: float) -> void:
	_clock += dt / maxf(Engine.time_scale, 0.01)
	var g: NeonGame = main.game
	if not g:
		return
	_peak = maxf(_peak, g.percent(g.me))
	if _outro_at < 0.0:
		# Knocked out once: come straight back, like watching a revive video.
		if not g.me.alive and g.dead_for > 1.0 and not g.revived:
			g.revive()
		elif g.is_over() or (not g.me.alive and g.revived and g.dead_for > 1.5):
			_outro()
	elif _clock - _outro_at >= OUTRO_SECONDS:
		_finish()


func _hook(text: String) -> void:
	var l := UI.glow_label(text, 64, UI.GOLD)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.set_anchors_preset(Control.PRESET_CENTER)
	l.offset_left = -330
	l.offset_right = 330
	l.offset_top = -250
	l.offset_bottom = -30
	main.ui.add_child(l)
	UI.pop(l, 0.3, 0.3)
	var tw := l.create_tween()
	tw.tween_interval(2.6)
	tw.tween_property(l, "modulate:a", 0.0, 0.4)
	tw.tween_callback(l.queue_free)


func _outro() -> void:
	_outro_at = _clock
	Engine.time_scale = 1.0
	var g: NeonGame = main.game
	var card := ColorRect.new()
	card.color = Color(UI.INK, 0.78)
	card.set_anchors_preset(Control.PRESET_FULL_RECT)
	card.mouse_filter = Control.MOUSE_FILTER_IGNORE
	main.ui.add_child(card)
	card.modulate.a = 0.0
	card.create_tween().tween_property(card, "modulate:a", 1.0, 0.35)
	var col := VBoxContainer.new()
	col.set_anchors_preset(Control.PRESET_FULL_RECT)
	col.alignment = BoxContainer.ALIGNMENT_CENTER
	col.add_theme_constant_override("separation", 26)
	card.add_child(col)
	col.add_child(UI.glow_label("NEON RUSH", 76, UI.CYAN))
	var score := "%d%% land" % roundi(_peak)
	if main._ko_by_me > 0:
		score += "  ·  %d KO" % main._ko_by_me
	col.add_child(UI.label(score, 46, UI.GOLD))
	col.add_child(UI.label("Beat it?", 54, Color.WHITE, true))
	var cta := UI.glow_label(opts.cta, 38, UI.GREEN)
	cta.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	cta.custom_minimum_size = Vector2(640, 0)
	col.add_child(cta)
	Audio.play("win")


func _finish() -> void:
	var g: NeonGame = main.game
	if opts.stats != "":
		var f := FileAccess.open(opts.stats, FileAccess.WRITE)
		if f:
			f.store_string(JSON.stringify({
				"seed": opts.seed, "level": opts.level, "rider": opts.rider,
				"land": snappedf(_peak, 0.1), "kos": main._ko_by_me, "rank": g.my_rank(),
				"loops": main._loops_closed, "knocked_out": g.revived,
			}))
	get_tree().quit()
