class_name BonusRush
extends Control
## The bonus game after a win: coins, gems and mystery boxes fly up the screen
## for a few seconds and you tap or swipe to grab them. Then the boxes open, and
## you can watch a video for one golden box on top. Emits [signal finished].

signal finished(coins: int)

const GRAB_SECONDS := 8.0
const COIN_VALUE := 3
const GEM_VALUE := 15
const GOLDEN_BOX := 150
enum Loot { COIN, GEM, BOX }
enum Phase { GRAB, OPEN, DONE }

var total := 0
var _phase := Phase.GRAB
var _t := 0.0
var _spawn_in := 0.0
var _flyers: Array = []  # {pos, vel, kind, spin, taken}
var _pops: Array = []  # {pos, text, color, age}
var _boxes := 0
var _box_coins: Array[int] = []
var _rng := RandomNumberGenerator.new()
var _info: Label
var _score: Label
var _buttons: VBoxContainer


func _ready() -> void:
	_rng.randomize()
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP
	# A deep blue backdrop with a soft glow in the middle.
	var bg := TextureRect.new()
	var g := GradientTexture2D.new()
	g.gradient = Gradient.new()
	g.gradient.set_color(0, Color(0.12, 0.2, 0.6))
	g.gradient.set_color(1, Color(0.01, 0.02, 0.1))
	g.fill = GradientTexture2D.FILL_RADIAL
	g.fill_from = Vector2(0.5, 0.45)
	g.fill_to = Vector2(1.1, 1.0)
	bg.texture = g
	bg.stretch_mode = TextureRect.STRETCH_SCALE
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	bg.show_behind_parent = true
	add_child(bg)
	var head := VBoxContainer.new()
	head.set_anchors_preset(Control.PRESET_TOP_WIDE)
	head.offset_top = 80
	head.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(head)
	var title := UI.glow_label(tr("BONUS RUSH!"), 72, UI.GOLD)
	head.add_child(title)
	UI.pop(title, 0.3, 0.5)
	_info = UI.label(tr("Tap the coins, gems and boxes!"), 30, Color.WHITE)
	head.add_child(_info)
	_score = UI.label("● 0", 56, UI.GOLD, true)
	head.add_child(_score)
	_buttons = VBoxContainer.new()
	_buttons.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
	_buttons.offset_left = 60
	_buttons.offset_right = -60
	_buttons.offset_top = -330
	_buttons.offset_bottom = -80
	_buttons.add_theme_constant_override("separation", 18)
	_buttons.alignment = BoxContainer.ALIGNMENT_END
	add_child(_buttons)


func _process(dt: float) -> void:
	_t += dt
	if _phase == Phase.GRAB:
		_spawn_in -= dt
		if _spawn_in <= 0.0 and _t < GRAB_SECONDS:
			_spawn_in = _rng.randf_range(0.12, 0.3)
			_spawn()
		if _t >= GRAB_SECONDS + 1.2:
			_open_boxes()
	var g := 900.0
	for f in _flyers:
		f.vel.y += g * dt
		f.pos += f.vel * dt
		f.spin += dt * 3.0
	_flyers = _flyers.filter(func(f): return not f.taken and f.pos.y < size.y + 100)
	for p in _pops:
		p.age += dt
		p.pos.y -= dt * 90
	_pops = _pops.filter(func(p): return p.age < 0.9)
	queue_redraw()


func _spawn() -> void:
	var roll := _rng.randf()
	var kind := Loot.BOX if roll < 0.1 else (Loot.GEM if roll < 0.3 else Loot.COIN)
	var x := _rng.randf_range(80, size.x - 80)
	var vy := -_rng.randf_range(1050, 1350)
	var vx := _rng.randf_range(-160, 160)
	_flyers.append({"pos": Vector2(x, size.y + 40), "vel": Vector2(vx, vy), "kind": kind, "spin": _rng.randf() * TAU, "taken": false})


func _gui_input(event: InputEvent) -> void:
	if _phase != Phase.GRAB:
		return
	var at := Vector2.INF
	if event is InputEventScreenTouch and event.pressed:
		at = event.position
	elif event is InputEventScreenDrag:
		at = event.position
	elif event is InputEventMouseButton and event.pressed:
		at = event.position
	elif event is InputEventMouseMotion and event.button_mask != 0:
		at = event.position
	if at == Vector2.INF:
		return
	for f in _flyers:
		if f.taken or f.pos.distance_to(at) > 70.0:
			continue
		f.taken = true
		match f.kind:
			Loot.COIN:
				total += COIN_VALUE
				_pop(f.pos, "+%d" % COIN_VALUE, UI.GOLD)
				Audio.play("coin", -6.0, randf_range(0.95, 1.15))
			Loot.GEM:
				total += GEM_VALUE
				_pop(f.pos, "+%d" % GEM_VALUE, UI.CYAN)
				Audio.play("power", -4.0, 1.3)
			Loot.BOX:
				_boxes += 1
				_pop(f.pos, tr("BOX!"), UI.PINK)
				Audio.play("power", -2.0)
		_score.text = "● %d" % total
		UI.pop(_score, 1.25, 0.15)


func _pop(at: Vector2, text: String, c: Color) -> void:
	_pops.append({"pos": at, "text": text, "color": c, "age": 0.0})


## Opens the boxes you grabbed (always at least one), then offers the golden box.
func _open_boxes() -> void:
	_phase = Phase.OPEN
	_boxes = maxi(_boxes, 1)
	for k in _boxes:
		_box_coins.append(_rng.randi_range(2, 6) * 5)
	var from_boxes := 0
	for c in _box_coins:
		from_boxes += c
	total += from_boxes
	_info.text = tr("%d mystery boxes opened!") % _boxes if _boxes > 1 else tr("Mystery box opened!")
	_score.text = "● %d" % total
	UI.pop(_score, 1.5, 0.3)
	Audio.play("win", -4.0)
	if Ads.rewarded_ready():
		var gold := UI.button(tr("▶ GOLDEN BOX  +%d") % GOLDEN_BOX, UI.GOLD, 34, 110)
		gold.pressed.connect(func():
			Ads.show_rewarded(func():
				total += GOLDEN_BOX
				_score.text = "● %d" % total
				UI.pop(_score, 1.6, 0.3)
				gold.text = tr("GOLDEN BOX OPENED!")
				gold.disabled = true))
		_buttons.add_child(gold)
		UI.breathe(gold, 0.05, 0.8)
	var take := UI.button(tr("COLLECT"), UI.PINK, 40, 110)
	take.pressed.connect(func():
		_phase = Phase.DONE
		finished.emit(total))
	_buttons.add_child(take)


func _draw() -> void:
	for f in _flyers:
		var p: Vector2 = f.pos
		match f.kind:
			Loot.COIN:
				var w := absf(cos(f.spin)) * 34.0 + 6.0
				draw_circle(p, 46, Color(UI.GOLD, 0.18))
				draw_set_transform(p, 0, Vector2(w / 40.0, 1))
				draw_circle(Vector2.ZERO, 38, Color("ffb000"))
				draw_circle(Vector2.ZERO, 30, UI.GOLD)
				draw_set_transform(Vector2.ZERO)
			Loot.GEM:
				draw_circle(p, 50, Color(UI.CYAN, 0.2))
				# A cut diamond that turns side to side.
				var sx := absf(cos(f.spin * 0.7)) * 0.6 + 0.4
				draw_colored_polygon(PackedVector2Array([p + Vector2(-34 * sx, -10), p + Vector2(-18 * sx, -30),
					p + Vector2(18 * sx, -30), p + Vector2(34 * sx, -10)]), Color("9ff6ff"))
				draw_colored_polygon(PackedVector2Array([p + Vector2(-34 * sx, -10), p + Vector2(34 * sx, -10), p + Vector2(0, 36)]), UI.CYAN)
				draw_line(p + Vector2(-10 * sx, -26), p + Vector2(-4 * sx, -14), Color.WHITE, 4.0)
			Loot.BOX:
				draw_circle(p, 62, Color(UI.PINK, 0.22))
				draw_set_transform(p, sin(f.spin) * 0.2)
				draw_rect(Rect2(-40, -36, 80, 72), Color("8a1aa8"))
				draw_rect(Rect2(-40, -36, 80, 72), UI.PINK, false, 5.0)
				draw_rect(Rect2(-8, -36, 16, 72), UI.GOLD)
				draw_string(UI.title_font, Vector2(-12, 14), "?", HORIZONTAL_ALIGNMENT_LEFT, -1, 40, Color.WHITE)
				draw_set_transform(Vector2.ZERO)
	for p in _pops:
		var a: float = 1.0 - p.age / 0.9
		draw_string_outline(UI.title_font, p.pos, p.text, HORIZONTAL_ALIGNMENT_LEFT, -1, 40, 8, Color(0, 0, 0, a * 0.7))
		draw_string(UI.title_font, p.pos, p.text, HORIZONTAL_ALIGNMENT_LEFT, -1, 40, Color(p.color, a))
	if _phase == Phase.GRAB:
		# A bar draining as the grab time runs out.
		var left := clampf(1.0 - _t / GRAB_SECONDS, 0.0, 1.0)
		var w := size.x - 120
		draw_rect(Rect2(60, size.y - 90, w, 16), Color(0, 0, 0, 0.5))
		draw_rect(Rect2(60, size.y - 90, w * left, 16), UI.GOLD)
	else:
		# The opened boxes in a row, each with its coins.
		var n := _box_coins.size()
		var bw := minf(150.0, (size.x - 80) / n)
		var x0 := size.x / 2 - bw * n / 2
		for k in n:
			var c := Vector2(x0 + bw * (k + 0.5), size.y * 0.5)
			var bob := sin(_t * 3.0 + k) * 6
			draw_circle(c + Vector2(0, bob), bw * 0.42, Color(UI.GOLD, 0.2))
			draw_rect(Rect2(c + Vector2(-bw * 0.3, -bw * 0.15 + bob), Vector2(bw * 0.6, bw * 0.45)), Color("8a1aa8"))
			draw_rect(Rect2(c + Vector2(-bw * 0.36, -bw * 0.3 + bob), Vector2(bw * 0.72, bw * 0.14)), UI.PINK)
			var txt := "+%d" % _box_coins[k]
			var tw := UI.title_font.get_string_size(txt, HORIZONTAL_ALIGNMENT_LEFT, -1, 34).x
			draw_string(UI.title_font, c + Vector2(-tw / 2, -bw * 0.4 + bob), txt, HORIZONTAL_ALIGNMENT_LEFT, -1, 34, UI.GOLD)
