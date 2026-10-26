extends Node
## Runs the app: the home menu over a live arena, the round with its HUD, and results.

enum Screen { MENU, PLAYING, RESULTS }

var screen := Screen.MENU
var game: NeonGame
var view: ArenaView
## The lobby's 3D backdrop: your bike on a turntable in the launch bay.
var bay: LaunchBay
var ui: CanvasLayer
var root_ui: Control
var paused := false

# HUD parts, made when a round starts.
var _timer_pill: PanelContainer
var _timer_label: Label
## The see-through arrow pads in the bottom corners; they flash when you turn.
var _pads: Array[Control] = []
var _percent_label: Label
var _percent_bar: ProgressBar
var _board: VBoxContainer
var _center: Label
var _toasts: VBoxContainer
var _feed: VBoxContainer
var _drop_btn: Button
var _pause_menu: Control
var _revive_card: Control
var _minimap: TextureRect
var _dots: Array[ColorRect] = []

var _swipe_from := Vector2.ZERO
var _swiping := false
var _swipe_used := false
var _turns: Array[int] = []
var _last_count := -1
var _ko_by_me := 0
var _slowmo := 0.0
var _round_over_handled := false
var _revive_offered := false
## Ad bookkeeping for this round: one ad when you first pause, and no ad after
## the round if you already watched a reward video in it.
var _pause_ad_shown := false
var _watched_video := false
var _revive_watching := false


func _ready() -> void:
	UI.fonts()
	ui = CanvasLayer.new()
	add_child(ui)
	get_tree().root.size_changed.connect(_fit_ui)
	if Save.seen_story:
		show_menu()
	else:
		show_story()


func _fit_ui() -> void:
	if root_ui:
		root_ui.size = get_viewport().get_visible_rect().size


func _new_ui() -> Control:
	if root_ui:
		root_ui.queue_free()
	root_ui = Control.new()
	root_ui.set_anchors_preset(Control.PRESET_FULL_RECT)
	root_ui.mouse_filter = Control.MOUSE_FILTER_IGNORE
	ui.add_child(root_ui)
	_fit_ui()
	return root_ui


func _new_view(g: NeonGame, follow: bool) -> void:
	_clear_scene()
	game = g
	view = ArenaView.new()
	view.showcase = not follow
	add_child(view)
	move_child(view, 0)
	var lv := GameData.level(Save.level)
	view.build(g, lv.accent, UI.body_font)


func _make_game(attract: bool) -> NeonGame:
	var lv := GameData.level(Save.level)
	var a := GameData.arena(lv.code)
	var g := NeonGame.new({
		"size": a.size, "mask": a.mask, "bots": lv.bots, "rider": Save.selected,
		"countdown": 0.0 if attract else 3.0, "min_bold": lv.min_bold, "max_bold": lv.max_bold,
		"bot_speed": lv.bot_speed, "win": lv.win, "nickname": Save.nickname,
		"round_seconds": 10000.0 if attract else 45.0,
	})
	if attract:
		# In the menu backdrop "you" ride on your own, like a demo.
		g.me.brain = BotBrain.new(1.0)
		g.me.nickname = GameData.rider(Save.selected).name
	return g


## Removes the 3D scene behind the UI: the round's arena or the lobby's launch bay.
func _clear_scene() -> void:
	if view:
		view.queue_free()
		view = null
	if bay:
		bay.queue_free()
		bay = null
	game = null


func _show_bay() -> void:
	_clear_scene()
	var rider := GameData.rider(Save.selected)
	bay = LaunchBay.new()
	add_child(bay)
	move_child(bay, 0)
	bay.build(rider.color, GameData.level(Save.level).accent, rider.name)


# ---------------------------------------------------------------- story

## The story intro: shown on first launch, and from the lobby's STORY button.
func show_story() -> void:
	screen = Screen.MENU
	if not bay:
		_show_bay()
	Audio.music("menu")
	var r := _new_ui()
	var story := StoryScreen.new()
	r.add_child(story)
	story.finished.connect(func():
		Save.seen_story = true
		Save.save()
		show_menu())


# ---------------------------------------------------------------- menu

func show_menu() -> void:
	screen = Screen.MENU
	paused = false
	get_tree().paused = false
	Engine.time_scale = 1.0
	_show_bay()
	Audio.music("menu")
	var r := _new_ui()
	var lv := GameData.level(Save.level)
	var rider := GameData.rider(Save.selected)

	# Light vignettes top and bottom so the text and cards stand out from the bay.
	for top_side in [true, false]:
		var fade := TextureRect.new()
		var g := GradientTexture2D.new()
		g.gradient = Gradient.new()
		g.gradient.set_color(0, Color(0.0, 0.02, 0.12, 0.7))
		g.gradient.set_color(1, Color(0.0, 0.02, 0.12, 0.0))
		g.fill_from = Vector2(0, 0) if top_side else Vector2(0, 1)
		g.fill_to = Vector2(0, 1) if top_side else Vector2(0, 0)
		fade.texture = g
		fade.stretch_mode = TextureRect.STRETCH_SCALE
		fade.set_anchors_preset(Control.PRESET_TOP_WIDE if top_side else Control.PRESET_BOTTOM_WIDE)
		fade.offset_top = 0 if top_side else -620
		fade.offset_bottom = 380 if top_side else 0
		fade.mouse_filter = Control.MOUSE_FILTER_IGNORE
		r.add_child(fade)

	var col := VBoxContainer.new()
	col.set_anchors_preset(Control.PRESET_FULL_RECT)
	col.offset_left = 28
	col.offset_right = -28
	col.offset_top = 40
	col.offset_bottom = -40
	col.add_theme_constant_override("separation", 18)
	r.add_child(col)

	var top := HBoxContainer.new()
	top.add_theme_constant_override("separation", 12)
	col.add_child(top)
	top.add_child(UI.pill("● %d" % Save.coins, UI.GOLD, 30))
	top.add_child(UI.pill("LV %d" % Save.player_level(), UI.CYAN, 30))
	var gap := Control.new()
	gap.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	top.add_child(gap)
	var story := UI.button("STORY", Color("394070"), 24, 72)
	story.custom_minimum_size.x = 130
	story.pressed.connect(show_story)
	top.add_child(story)
	var snd := UI.icon_button("♪" if Save.sound else "×", UI.CYAN, 72)
	snd.pressed.connect(func():
		Audio.set_sound(not Save.sound)
		snd.text = "♪" if Save.sound else "×")
	top.add_child(snd)

	var title := UI.glow_label("NEON\nRUSH", 104, UI.PINK)
	title.add_theme_constant_override("line_spacing", -24)
	col.add_child(title)
	var tag := UI.label("LIGHT BIKE LAND WARS", 28, UI.CYAN, true)
	col.add_child(tag)
	var sub := UI.label("Loop the land. Cut their trails. Rule the Grid.", 24, Color(1, 1, 1, 0.85))
	col.add_child(sub)

	var spacer := Control.new()
	spacer.size_flags_vertical = Control.SIZE_EXPAND_FILL
	col.add_child(spacer)

	# Rider picker.
	var rc := UI.card(rider.color)
	col.add_child(rc)
	var rrow := HBoxContainer.new()
	rrow.add_theme_constant_override("separation", 10)
	rc.add_child(rrow)
	var prev := UI.icon_button("◀", rider.color, 76)
	prev.pressed.connect(func(): _pick_rider(-1))
	rrow.add_child(prev)
	var rinfo := VBoxContainer.new()
	rinfo.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	rrow.add_child(rinfo)
	rinfo.add_child(UI.label(rider.name.to_upper(), 40, rider.color, true))
	rinfo.add_child(UI.label(rider.perk_text, 24, Color(1, 1, 1, 0.8)))
	var next := UI.icon_button("▶", rider.color, 76)
	next.pressed.connect(func(): _pick_rider(1))
	rrow.add_child(next)
	var owned: bool = Save.selected in Save.unlocked

	# Land picker.
	var lc := UI.card(lv.accent)
	col.add_child(lc)
	var lrow := HBoxContainer.new()
	lrow.add_theme_constant_override("separation", 10)
	lc.add_child(lrow)
	var lprev := UI.icon_button("◀", lv.accent, 76)
	lprev.disabled = Save.level <= 0
	lprev.pressed.connect(func(): _pick_level(-1))
	lrow.add_child(lprev)
	var linfo := VBoxContainer.new()
	linfo.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	lrow.add_child(linfo)
	var star := " ★" if Save.level in Save.conquered else ""
	linfo.add_child(UI.label("LEVEL %d / %d%s" % [Save.level + 1, GameData.LEVELS.size(), star], 24, Color(1, 1, 1, 0.75)))
	linfo.add_child(UI.label(lv.name.to_upper(), 40, lv.accent, true))
	linfo.add_child(UI.label("Claim %d%% of the land to win" % int(lv.win), 22, Color(1, 1, 1, 0.7)))
	var lnext := UI.icon_button("▶", lv.accent, 76)
	lnext.disabled = Save.level >= Save.best_level
	lnext.pressed.connect(func(): _pick_level(1))
	lrow.add_child(lnext)

	var play: Button
	if owned:
		play = UI.button("PLAY", UI.PINK, 54, 130)
		play.pressed.connect(start_round)
	else:
		play = UI.button("UNLOCK  ● %d" % rider.cost, UI.GOLD, 40, 130)
		play.disabled = Save.coins < rider.cost
		play.pressed.connect(func():
			if Save.buy(rider.id):
				Audio.play("power")
				show_menu())
	col.add_child(play)
	UI.breathe(play, 0.04)
	if not owned:
		col.add_child(UI.label("Win rounds to earn coins", 22, Color(1, 1, 1, 0.6)))

	title.modulate.a = 0.0
	title.create_tween().tween_property(title, "modulate:a", 1.0, 0.6)


func _pick_rider(step: int) -> void:
	var n := GameData.RIDERS.size()
	Save.selected = posmod(Save.selected + step, n)
	Save.save()
	show_menu()


func _pick_level(step: int) -> void:
	Save.level = clampi(Save.level + step, 0, Save.best_level)
	Save.save()
	show_menu()


# ---------------------------------------------------------------- round

func start_round() -> void:
	screen = Screen.PLAYING
	_last_count = -1
	_pause_ad_shown = false
	_watched_video = false
	_turns.clear()
	_ko_by_me = 0
	_round_over_handled = false
	_revive_offered = false
	_new_view(_make_game(false), true)
	Audio.music("race")
	_build_hud()


func _build_hud() -> void:
	var r := _new_ui()
	var top := HBoxContainer.new()
	top.set_anchors_preset(Control.PRESET_TOP_WIDE)
	top.offset_left = 20
	top.offset_right = -20
	top.offset_top = 36
	top.add_theme_constant_override("separation", 12)
	top.mouse_filter = Control.MOUSE_FILTER_IGNORE
	r.add_child(top)

	# Pause on the left and your share on the right, equally wide, so the clock sits dead centre.
	var left := HBoxContainer.new()
	left.custom_minimum_size = Vector2(130, 0)
	left.mouse_filter = Control.MOUSE_FILTER_IGNORE
	top.add_child(left)
	var pause := UI.icon_button("II", UI.CYAN, 72)
	pause.pressed.connect(_toggle_pause)
	left.add_child(pause)
	var mid := VBoxContainer.new()
	mid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	mid.add_theme_constant_override("separation", 6)
	mid.mouse_filter = Control.MOUSE_FILTER_IGNORE
	top.add_child(mid)
	# A big digital stopwatch counting down to the hundredth, top and centre.
	_timer_pill = PanelContainer.new()
	var ts := UI.box(Color(0.02, 0.04, 0.1, 0.78), Color(UI.GREEN, 0.5), 14, 2, 8)
	ts.content_margin_left = 26
	ts.content_margin_right = 26
	ts.content_margin_top = 2
	ts.content_margin_bottom = 2
	_timer_pill.add_theme_stylebox_override("panel", ts)
	_timer_pill.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	_timer_pill.mouse_filter = Control.MOUSE_FILTER_IGNORE
	mid.add_child(_timer_pill)
	_timer_label = UI.glow_label("45.00", 58, UI.GREEN)
	_timer_label.add_theme_color_override("font_color", UI.GREEN.lightened(0.3))
	_timer_pill.add_child(_timer_label)
	_percent_bar = ProgressBar.new()
	_percent_bar.show_percentage = false
	_percent_bar.custom_minimum_size = Vector2(0, 14)
	_percent_bar.max_value = game.win_percent
	var bg := UI.box(Color(0, 0, 0, 0.5), Color(1, 1, 1, 0.3), 7, 1)
	bg.content_margin_top = 0
	bg.content_margin_bottom = 0
	_percent_bar.add_theme_stylebox_override("background", bg)
	_percent_bar.add_theme_stylebox_override("fill", UI.box(game.me.color, game.me.color, 7, 0, 6))
	mid.add_child(_percent_bar)
	_percent_label = UI.label("0%", 34, game.me.color, true)
	_percent_label.custom_minimum_size = Vector2(130, 0)
	_percent_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	top.add_child(_percent_label)

	# Minimap: the whole land, with a dot for every rider.
	var frame := PanelContainer.new()
	var fs := UI.box(Color(0.03, 0.02, 0.1, 0.55), Color(UI.CYAN, 0.6), 18, 2, 6)
	fs.set_content_margin_all(6)
	frame.add_theme_stylebox_override("panel", fs)
	frame.position = Vector2(20, 160)
	frame.mouse_filter = Control.MOUSE_FILTER_IGNORE
	r.add_child(frame)
	_minimap = TextureRect.new()
	_minimap.texture = view.land_texture
	_minimap.custom_minimum_size = Vector2(210, 210)
	_minimap.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_minimap.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	var mm := ShaderMaterial.new()
	mm.shader = preload("res://shaders/minimap.gdshader")
	mm.set_shader_parameter("mask_tex", view.mask_texture)
	_minimap.material = mm
	_minimap.mouse_filter = Control.MOUSE_FILTER_IGNORE
	frame.add_child(_minimap)
	_dots.clear()
	for p in game.players:
		var d := ColorRect.new()
		var big := p == game.me
		d.size = Vector2(14, 14) if big else Vector2(9, 9)
		d.color = Color.WHITE if big else p.color.lightened(0.3)
		d.mouse_filter = Control.MOUSE_FILTER_IGNORE
		_minimap.add_child(d)
		_dots.append(d)

	_board = VBoxContainer.new()
	_board.set_anchors_preset(Control.PRESET_TOP_RIGHT)
	_board.offset_left = -250
	_board.offset_right = -20
	_board.offset_top = 170
	_board.add_theme_constant_override("separation", 2)
	_board.mouse_filter = Control.MOUSE_FILTER_IGNORE
	r.add_child(_board)

	_center = UI.glow_label("", 150, UI.CYAN)
	_center.set_anchors_preset(Control.PRESET_CENTER)
	_center.offset_left = -360
	_center.offset_right = 360
	_center.offset_top = -260
	_center.offset_bottom = -60
	_center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	r.add_child(_center)

	_toasts = VBoxContainer.new()
	_toasts.set_anchors_preset(Control.PRESET_CENTER_TOP)
	_toasts.offset_left = -330
	_toasts.offset_right = 330
	_toasts.offset_top = 260
	_toasts.alignment = BoxContainer.ALIGNMENT_BEGIN
	_toasts.mouse_filter = Control.MOUSE_FILTER_IGNORE
	r.add_child(_toasts)

	_feed = VBoxContainer.new()
	_feed.set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
	_feed.offset_left = 20
	_feed.offset_right = 520
	_feed.offset_top = -260
	_feed.offset_bottom = -40
	_feed.alignment = BoxContainer.ALIGNMENT_END
	_feed.mouse_filter = Control.MOUSE_FILTER_IGNORE
	r.add_child(_feed)

	_drop_btn = UI.button("▶ POWER DROP", UI.GOLD, 32, 88)
	_drop_btn.set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
	_drop_btn.offset_left = -190
	_drop_btn.offset_right = 190
	_drop_btn.offset_top = -200
	_drop_btn.offset_bottom = -112
	_drop_btn.visible = false
	_drop_btn.pressed.connect(func():
		_drop_btn.visible = false
		Ads.show_rewarded(func():
			_watched_video = true
			game.power_drop(), func(): pass))
	r.add_child(_drop_btn)
	UI.breathe(_drop_btn, 0.06, 0.7)

	_pads.clear()
	for side in [-1, 1]:
		var pad := _arrow_pad(side)
		r.add_child(pad)
		_pads.append(pad)

	var hint := UI.label("Tap left or right to turn", 30, Color(1, 1, 1, 0.8))
	hint.set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
	hint.offset_left = -300
	hint.offset_right = 300
	hint.offset_top = -170
	hint.offset_bottom = -120
	r.add_child(hint)
	var tw := hint.create_tween()
	tw.tween_interval(4.0)
	tw.tween_property(hint, "modulate:a", 0.0, 0.6)


## A see-through square with an arrow, in a bottom corner. Only a picture: a tap
## anywhere on that half of the screen turns.
func _arrow_pad(side: int) -> Control:
	var pad := Panel.new()
	pad.add_theme_stylebox_override("panel", UI.box(Color(0.75, 0.9, 1.0, 0.16), Color(1, 1, 1, 0.55), 18, 3))
	pad.size = Vector2(124, 124)
	pad.set_anchors_preset(Control.PRESET_BOTTOM_LEFT if side < 0 else Control.PRESET_BOTTOM_RIGHT)
	pad.offset_top = -170
	pad.offset_bottom = -46
	pad.offset_left = 36 if side < 0 else -160
	pad.offset_right = pad.offset_left + 124
	pad.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var arrow := Polygon2D.new()
	var pts := PackedVector2Array([Vector2(-30, -16), Vector2(4, -16), Vector2(4, -34), Vector2(36, 0), Vector2(4, 34), Vector2(4, 16), Vector2(-30, 16)])
	if side < 0:
		for k in pts.size():
			pts[k].x = -pts[k].x
	arrow.polygon = pts
	arrow.color = Color(1, 1, 1, 0.8)
	arrow.position = Vector2(62, 62)
	pad.add_child(arrow)
	pad.modulate.a = 0.75
	return pad


func _process(dt: float) -> void:
	if not game or not view:
		return
	if paused:
		return
	if _slowmo > 0.0:
		_slowmo -= dt / maxf(Engine.time_scale, 0.01)
		if _slowmo <= 0.0:
			Engine.time_scale = 1.0
	if screen == Screen.PLAYING:
		_apply_turns()
	game.update(dt)
	_handle_events()
	view.sync(dt)
	if screen == Screen.PLAYING:
		_update_hud()
		if game.is_over() and not _round_over_handled:
			_round_over_handled = true
			_end_round()
		elif not game.me.alive and not game.revived and not _revive_offered:
			if Ads.rewarded_ready():
				_offer_revive()
			else:
				_revive_offered = true


func _handle_events() -> void:
	var playing := screen == Screen.PLAYING
	for e in game.events:
		match e.type:
			"go":
				if playing:
					_big("GO!", UI.GREEN)
					Audio.play("go")
			"captured":
				var p := game.players[e.player]
				var at := Vector3(e.x, 0.5, e.y)
				view.ring(at, p.color, 4.0 + minf(e.gained / 12.0, 10.0))
				if p.id == game.me.id and playing and e.gained > 0:
					view.burst(at, p.color, 30, 7.0)
					Audio.play("capture", -2.0, 1.0 + minf(e.gained / 400.0, 0.3))
					_toast("+%.1f%%" % (e.gained * 100.0 / game.area), UI.GREEN)
			"ko":
				var v := game.players[e.victim]
				var at := Vector3(e.x, 0.6, e.y)
				view.burst(at, v.color, 70, 11.0, 1.1)
				view.flash(at, v.color)
				var near := Vector2(e.x - game.me.x, e.y - game.me.y).length() < 25.0
				if playing and near:
					Audio.play("ko", -3.0)
				if not playing:
					continue
				if e.killer == game.me.id:
					_ko_by_me += 1
					_big("KO!", UI.PINK)
					_toast("You knocked out %s" % v.display_name(), UI.PINK)
					view.shake(0.8)
					_slow_motion()
				elif v.id == game.me.id:
					view.shake(1.2)
					_slow_motion()
			"picked":
				if e.player == game.me.id and playing:
					Audio.play("power")
					_toast(NeonGame.POWER_NAMES[e.power] + "!", ArenaView.POWER_COLORS[e.power])
					view.ring(Vector3(e.x, 0.5, e.y), ArenaView.POWER_COLORS[e.power], 3.0)
			"coin":
				if playing:
					Audio.play("coin", -8.0, randf_range(0.95, 1.1))
			"blocked":
				if playing and Vector2(e.x - game.me.x, e.y - game.me.y).length() < 20.0:
					Audio.play("blocked", -4.0)
					view.ring(Vector3(e.x, 0.6, e.y), Color(0.4, 0.8, 1.0), 2.5)
			"streak":
				if playing:
					Audio.play("streak")
					var words := ["", "", "DOUBLE KO!", "TRIPLE KO!", "UNSTOPPABLE!"]
					_toast(words[mini(e.count, 4)], UI.GOLD)
	game.events.clear()


func _slow_motion() -> void:
	Engine.time_scale = 0.3
	_slowmo = 0.45


func _update_hud() -> void:
	var t := game.time_left()
	_timer_label.text = "%05.2f" % t
	var tc := UI.PINK if t < 10.0 else UI.GREEN
	_timer_label.add_theme_color_override("font_color", tc.lightened(0.3))
	_timer_label.add_theme_color_override("font_shadow_color", Color(tc, 0.85))
	_timer_label.add_theme_color_override("font_outline_color", Color(tc, 0.9))
	Audio.intense(t < 10.0 and game.countdown <= 0.0)
	var pct := game.percent(game.me)
	_percent_label.text = "%.1f%%" % pct
	_percent_bar.value = pct
	if game.countdown > 0.0:
		var n := ceili(game.countdown)
		if n != _last_count:
			_last_count = n
			_big(str(n), UI.CYAN)
			Audio.play("count")
	if game.power_drop_offered() and not Ads.rewarded_ready():
		game.skip_power_drop()
	_drop_btn.visible = game.power_drop_offered()
	var scale := _minimap.size.x / game.size
	for k in game.players.size():
		var p := game.players[k]
		_dots[k].visible = p.alive
		_dots[k].position = Vector2(p.x, p.y) * scale - _dots[k].size / 2

	# Leaderboard: top four, plus you if you're not in it.
	for c in _board.get_children():
		c.queue_free()
	var rank := game.ranking()
	var shown := 0
	for k in rank.size():
		var p: NeonGame.Player = rank[k]
		if shown >= 4 and p != game.me:
			continue
		if shown >= 5:
			break
		shown += 1
		var l := UI.label("%d  %s  %.0f%%" % [k + 1, p.display_name(), game.percent(p)], 24 if p != game.me else 27, p.color, false, HORIZONTAL_ALIGNMENT_RIGHT)
		if not p.alive:
			l.modulate.a = 0.45
		_board.add_child(l)

	for c in _feed.get_children():
		c.queue_free()
	for f in game.feed:
		_feed.add_child(UI.label(f.text, 22, Color(1, 1, 1, 0.85), false, HORIZONTAL_ALIGNMENT_LEFT))


func _big(text: String, color: Color) -> void:
	_center.text = text
	_center.add_theme_color_override("font_shadow_color", Color(color, 0.85))
	_center.add_theme_color_override("font_outline_color", Color(color, 0.9))
	_center.modulate.a = 1.0
	_center.pivot_offset = _center.size / 2
	_center.scale = Vector2.ONE * 1.8
	var tw := _center.create_tween()
	tw.tween_property(_center, "scale", Vector2.ONE, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	tw.tween_interval(0.45)
	tw.tween_property(_center, "modulate:a", 0.0, 0.25)


func _toast(text: String, color: Color) -> void:
	var l := UI.glow_label(text, 44, color)
	_toasts.add_child(l)
	if _toasts.get_child_count() > 3:
		_toasts.get_child(0).queue_free()
	UI.pop(l, 0.3, 0.3)
	var tw := l.create_tween()
	tw.tween_interval(1.0)
	tw.tween_property(l, "modulate:a", 0.0, 0.4)
	tw.tween_callback(l.queue_free)


func _unhandled_input(event: InputEvent) -> void:
	if screen != Screen.PLAYING or paused or not game:
		return
	if event is InputEventScreenTouch:
		if event.pressed:
			_swiping = true
			_swipe_from = event.position
			_swipe_used = false
		else:
			# A tap: left half turns left, right half turns right.
			if _swiping and not _swipe_used:
				_turn(-1 if event.position.x < get_viewport().get_visible_rect().size.x / 2.0 else 1)
			_swiping = false
	elif event is InputEventScreenDrag and _swiping and not _swipe_used:
		var d: Vector2 = event.position - _swipe_from
		if absf(d.x) > 40.0 and absf(d.x) > absf(d.y):
			_turn(1 if d.x > 0 else -1)
			_swipe_used = true
	elif event is InputEventKey and event.pressed and not event.echo:
		match event.keycode:
			KEY_LEFT, KEY_A: _turn(-1)
			KEY_RIGHT, KEY_D: _turn(1)
			KEY_ESCAPE, KEY_P: _toggle_pause()


## Turns left (-1) or right (+1) of the way you're heading. Quick double taps queue
## up, so you can zig-zag or loop back alongside your own trail.
func _turn(side: int) -> void:
	if _turns.size() < 2:
		_turns.append(side)
	if _pads.size() == 2:
		var pad := _pads[0 if side < 0 else 1]
		pad.modulate.a = 1.0
		pad.pivot_offset = pad.size / 2
		pad.scale = Vector2.ONE * 0.9
		var tw := pad.create_tween().set_parallel(true)
		tw.tween_property(pad, "scale", Vector2.ONE, 0.18)
		tw.tween_property(pad, "modulate:a", 0.75, 0.3)


func _apply_turns() -> void:
	var me := game.me
	if _turns.is_empty() or not me.alive:
		_turns.clear()
		return
	# Wait until the last turn has been taken at a cell centre.
	if me.want != me.dir:
		return
	me.want = posmod(me.dir + _turns.pop_front(), 4)


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_GO_BACK_REQUEST:
		if screen == Screen.PLAYING:
			_toggle_pause()
		elif screen == Screen.RESULTS:
			show_menu()
		else:
			get_tree().quit()
	elif what == NOTIFICATION_APPLICATION_PAUSED and screen == Screen.PLAYING and not paused:
		_toggle_pause()


func _toggle_pause() -> void:
	if screen != Screen.PLAYING:
		return
	paused = not paused
	if paused:
		var menu := func():
			_pause_menu = _overlay("PAUSED", UI.CYAN, [
				["RESUME", UI.PINK, _toggle_pause],
				["QUIT ROUND", Color("394070"), show_menu],
			])
		if _pause_ad_shown:
			menu.call()
		else:
			_pause_ad_shown = true
			Ads.show_interstitial(menu)
	elif _pause_menu:
		_pause_menu.queue_free()


## A dim full-screen card with a title and buttons: [[text, color, callable], ...].
func _overlay(title: String, color: Color, buttons: Array, extra: Control = null) -> Control:
	var o := ColorRect.new()
	o.set_anchors_preset(Control.PRESET_FULL_RECT)
	o.color = Color(0.0, 0.0, 0.05, 0.6)
	root_ui.add_child(o)
	var c := UI.card(color)
	c.set_anchors_preset(Control.PRESET_CENTER)
	c.offset_left = -300
	c.offset_right = 300
	c.offset_top = -260
	c.offset_bottom = 260
	c.grow_vertical = Control.GROW_DIRECTION_BOTH
	o.add_child(c)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 20)
	v.alignment = BoxContainer.ALIGNMENT_CENTER
	c.add_child(v)
	v.add_child(UI.glow_label(title, 60, color))
	if extra:
		v.add_child(extra)
	for b in buttons:
		var btn := UI.button(b[0], b[1], 34, 100)
		btn.pressed.connect(b[2])
		v.add_child(btn)
	UI.pop(c, 0.6, 0.3)
	return o


func _offer_revive() -> void:
	_revive_offered = true
	var by := game.me.knocked_out_by
	var info := UI.label(("Knocked out by %s" % by) if by != "" else "Knocked out!", 30, Color(1, 1, 1, 0.85))
	var secs := 5
	var count := UI.label("%d" % secs, 64, UI.GOLD, true)
	var box := VBoxContainer.new()
	box.add_child(info)
	box.add_child(count)
	_revive_card = _overlay("KNOCKED OUT", UI.PINK, [
		["▶ REVIVE", UI.GREEN, func():
			_revive_watching = true
			Ads.show_rewarded(func():
				_watched_video = true
				game.revive(), func():
				_revive_watching = false
				if _revive_card:
					_revive_card.queue_free()
					_revive_card = null
				if not game.me.alive:
					game.dead_for = NeonGame.DEATH_PAUSE)],
		["NO THANKS", Color("394070"), func():
			_revive_card.queue_free()
			_revive_card = null
			game.dead_for = NeonGame.DEATH_PAUSE],
	], box)
	# Hold the end of the round while you decide.
	game.dead_for = -100.0
	var tick := func():
		secs -= 1
		count.text = str(secs)
		if secs <= 0 and _revive_card and not _revive_watching:
			_revive_card.queue_free()
			_revive_card = null
			game.dead_for = NeonGame.DEATH_PAUSE
	for k in 5:
		get_tree().create_timer(k + 1.0, true, false, true).timeout.connect(func():
			if _revive_card and is_instance_valid(count):
				tick.call())


# ---------------------------------------------------------------- results

func _end_round() -> void:
	screen = Screen.RESULTS
	Engine.time_scale = 1.0
	Audio.intense(false)
	var won := game.won()
	var lv := GameData.level(Save.level)
	var pct := game.percent(game.me)
	var coins := 5 + game.me.coins + int(pct * 2.0) + _ko_by_me * 10 + (50 if won else 0)
	if game.me.rider.perk == GameData.Perk.MORE_COINS:
		coins = int(coins * 1.5)
	var was_best := Save.best_level
	var first_win: bool = won and not (Save.level in Save.conquered)
	var xp := Save.finish_round(won, coins, _ko_by_me, game.best_streak, pct)
	Audio.play("win" if won else "lose")
	await get_tree().create_timer(0.6).timeout
	# An ad between rounds, unless you already watched a video this round.
	if not _watched_video:
		var shown := [false]
		Ads.show_interstitial(func(): shown[0] = true)
		while not shown[0]:
			await get_tree().process_frame

	var r := _new_ui()
	var shade := ColorRect.new()
	shade.set_anchors_preset(Control.PRESET_FULL_RECT)
	shade.color = Color(0, 0, 0.05, 0.55)
	r.add_child(shade)
	var col := VBoxContainer.new()
	col.set_anchors_preset(Control.PRESET_FULL_RECT)
	col.offset_left = 36
	col.offset_right = -36
	col.offset_top = 90
	col.offset_bottom = -60
	col.add_theme_constant_override("separation", 18)
	r.add_child(col)

	var title := "VICTORY!" if won else ("KNOCKED OUT" if not game.me.alive else "TIME UP")
	var tcolor := UI.GOLD if won else (UI.PINK if not game.me.alive else UI.CYAN)
	var t := UI.glow_label(title, 76, tcolor)
	col.add_child(t)
	UI.pop(t, 0.3, 0.5)
	if first_win:
		var cq := UI.glow_label("%s CONQUERED!" % lv.name.to_upper(), 36, UI.GREEN)
		col.add_child(cq)
	col.add_child(UI.label("#%d of %d riders" % [game.my_rank(), game.players.size()], 34, Color.WHITE))

	var stats := UI.card(tcolor)
	col.add_child(stats)
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation", 30)
	grid.add_theme_constant_override("v_separation", 8)
	stats.add_child(grid)
	for row in [["Land", "%.1f%%" % pct], ["Knockouts", str(_ko_by_me)], ["Coins", "+%d" % coins], ["XP", "+%d" % xp]]:
		var a := UI.label(row[0], 32, Color(1, 1, 1, 0.75), false, HORIZONTAL_ALIGNMENT_LEFT)
		a.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		grid.add_child(a)
		grid.add_child(UI.label(row[1], 40, UI.GOLD if row[0] == "Coins" else Color.WHITE, false, HORIZONTAL_ALIGNMENT_RIGHT))

	var lvl := UI.label("Rider level %d" % Save.player_level(), 28, UI.CYAN)
	col.add_child(lvl)
	var bar := ProgressBar.new()
	bar.show_percentage = false
	bar.custom_minimum_size = Vector2(0, 16)
	bar.max_value = 1.0
	bar.step = 0.001
	bar.add_theme_stylebox_override("background", UI.box(Color(0, 0, 0, 0.5), Color.TRANSPARENT, 8))
	bar.add_theme_stylebox_override("fill", UI.box(UI.CYAN, UI.CYAN, 8, 0, 8))
	col.add_child(bar)
	bar.create_tween().tween_property(bar, "value", Save.level_progress(), 0.8).set_trans(Tween.TRANS_CUBIC)

	var spacer := Control.new()
	spacer.size_flags_vertical = Control.SIZE_EXPAND_FILL
	col.add_child(spacer)

	if Ads.rewarded_ready() and coins > 0:
		var dbl := UI.button("▶ DOUBLE COINS  +%d" % coins, UI.GOLD, 32, 104)
		dbl.pressed.connect(func():
			Ads.show_rewarded(func():
				Save.coins += coins
				Save.save()
				dbl.text = "COINS DOUBLED!"
				dbl.disabled = true))
		col.add_child(dbl)
		UI.breathe(dbl, 0.05, 0.8)
	if won and Save.best_level > was_best:
		var nb := UI.button("NEXT: %s ▶" % GameData.level(Save.best_level).name.to_upper(), UI.GREEN, 36, 120)
		nb.pressed.connect(func():
			Save.level = Save.best_level
			Save.save()
			start_round())
		col.add_child(nb)
		UI.breathe(nb, 0.04)
	var again := UI.button("PLAY AGAIN", UI.PINK, 40, 120)
	again.pressed.connect(start_round)
	col.add_child(again)
	if not won:
		UI.breathe(again, 0.04)
	var home := UI.button("HOME", Color("394070"), 32, 96)
	home.pressed.connect(show_menu)
	col.add_child(home)
