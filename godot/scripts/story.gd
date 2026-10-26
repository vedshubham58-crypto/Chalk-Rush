class_name StoryScreen
extends Control
## A short story intro that also teaches the game: five holo panels, each with
## a little animated picture drawn in code and a line of story underneath.
## Tap NEXT to move on, or SKIP. Emits [signal finished] at the end.

signal finished

const CYAN := Color("00e5ff")
const PINK := Color("ff2bd6")
const GOLD := Color("ffc93c")
const GREEN := Color("39ff88")
const GRID := 12
const PAGES := [
	{"kind": "city", "title": "NEO GRID, 2099",
		"text": "The floating lands of Neo Grid run on pure light. Whoever holds the most land rules the city."},
	{"kind": "trail", "title": "RIDE THE LIGHT",
		"text": "Riders race light bikes across the lands. Every bike leaves a wall of light behind it."},
	{"kind": "loop", "title": "LOOP TO CLAIM",
		"text": "Ride out of your land and loop back home. Everything inside your loop becomes yours."},
	{"kind": "cut", "title": "CUT THEIR TRAIL",
		"text": "Hit a rival's light wall before they get home and they're out. Keep yours safe!"},
	{"kind": "win", "title": "RULE THE GRID",
		"text": "Hold the most land when the clock hits zero. Win to unlock new lands and riders."},
]

var page := 0
var _t := 0.0
var _art: Control
var _title: Label
var _text: Label
var _dots: HBoxContainer
var _next: Button


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var shade := ColorRect.new()
	shade.set_anchors_preset(Control.PRESET_FULL_RECT)
	shade.color = Color(0.0, 0.02, 0.1, 0.55)
	add_child(shade)

	var col := VBoxContainer.new()
	col.set_anchors_preset(Control.PRESET_FULL_RECT)
	col.offset_left = 32
	col.offset_right = -32
	col.offset_top = 70
	col.offset_bottom = -50
	col.add_theme_constant_override("separation", 22)
	add_child(col)

	var top := HBoxContainer.new()
	col.add_child(top)
	top.add_child(UI.label("MISSION BRIEFING", 26, Color(CYAN, 0.8), true, HORIZONTAL_ALIGNMENT_LEFT))
	var gap := Control.new()
	gap.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	top.add_child(gap)
	var skip := UI.button("SKIP", Color(0.25, 0.3, 0.5), 26, 64)
	skip.custom_minimum_size.x = 140
	skip.pressed.connect(_done)
	top.add_child(skip)

	var frame := PanelContainer.new()
	var fs := UI.chamfer(Color(0.02, 0.05, 0.16, 0.85), CYAN, 30, 3, 14)
	fs.set_content_margin_all(14)
	frame.add_theme_stylebox_override("panel", fs)
	frame.size_flags_vertical = Control.SIZE_EXPAND_FILL
	col.add_child(frame)
	_art = Control.new()
	_art.custom_minimum_size = Vector2(0, 560)
	_art.clip_contents = true
	_art.draw.connect(_draw_art)
	frame.add_child(_art)

	_title = UI.glow_label("", 54, PINK)
	col.add_child(_title)
	_text = UI.label("", 30, Color(1, 1, 1, 0.92))
	_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_text.custom_minimum_size = Vector2(0, 130)
	col.add_child(_text)

	_dots = HBoxContainer.new()
	_dots.alignment = BoxContainer.ALIGNMENT_CENTER
	_dots.add_theme_constant_override("separation", 14)
	col.add_child(_dots)
	for k in PAGES.size():
		var d := Panel.new()
		d.custom_minimum_size = Vector2(36, 8)
		_dots.add_child(d)

	_next = UI.button("NEXT", UI.PINK, 44, 112)
	_next.pressed.connect(_advance)
	col.add_child(_next)
	_show(0)


func _show(p: int) -> void:
	page = p
	_t = 0.0
	var pg: Dictionary = PAGES[p]
	_title.text = pg.title
	_text.text = pg.text
	_next.text = "LET'S RIDE!" if p == PAGES.size() - 1 else "NEXT"
	for k in _dots.get_child_count():
		var on := k == p
		(_dots.get_child(k) as Panel).add_theme_stylebox_override("panel", UI.box(CYAN if on else Color(1, 1, 1, 0.25), CYAN, 4, 0, 6 if on else 0))
	_title.modulate.a = 0.0
	_text.modulate.a = 0.0
	var tw := create_tween().set_parallel(true)
	tw.tween_property(_title, "modulate:a", 1.0, 0.35)
	tw.tween_property(_text, "modulate:a", 1.0, 0.5).set_delay(0.15)


func _advance() -> void:
	if page < PAGES.size() - 1:
		_show(page + 1)
	else:
		_done()


func _done() -> void:
	finished.emit()


func _process(dt: float) -> void:
	_t += dt
	_art.queue_redraw()


# ---------------------------------------------------------------- pictures

var _o := Vector2.ZERO
var _cell := 40.0


## Grid cell (x, y) to a point in the picture, at the cell's centre.
func _p(x: float, y: float) -> Vector2:
	return _o + Vector2(x + 0.5, y + 0.5) * _cell


func _draw_art() -> void:
	var s := _art.size
	var side := minf(s.x, s.y) - 20.0
	_cell = side / GRID
	_o = (s - Vector2(side, side)) / 2.0
	var kind: String = PAGES[page].kind
	if kind == "city":
		_draw_city(s)
		return
	_draw_grid(side)
	match kind:
		"trail": _draw_trail()
		"loop": _draw_loop()
		"cut": _draw_cut()
		"win": _draw_win()


func _draw_grid(side: float) -> void:
	_art.draw_rect(Rect2(_o, Vector2(side, side)), Color(0.02, 0.12, 0.45, 0.9))
	for k in GRID + 1:
		var a := k * _cell
		var c := Color(0.4, 0.75, 1.0, 0.18 if k % 3 else 0.35)
		_art.draw_line(_o + Vector2(a, 0), _o + Vector2(a, side), c, 1.5)
		_art.draw_line(_o + Vector2(0, a), _o + Vector2(side, a), c, 1.5)


func _glow_line(pts: PackedVector2Array, c: Color) -> void:
	if pts.size() < 2:
		return
	_art.draw_polyline(pts, Color(c, 0.25), _cell * 0.55, true)
	_art.draw_polyline(pts, c, _cell * 0.2, true)
	_art.draw_polyline(pts, Color(1, 1, 1, 0.8), _cell * 0.06, true)


func _bike(at: Vector2, dir: Vector2, c: Color) -> void:
	_art.draw_circle(at, _cell * 0.75, Color(c, 0.25))
	var f := dir.normalized() * _cell * 0.55
	var r := Vector2(-f.y, f.x) * 0.6
	_art.draw_colored_polygon(PackedVector2Array([at + f, at - f * 0.6 + r, at - f * 0.3, at - f * 0.6 - r]), Color.WHITE.lerp(c, 0.3))
	_art.draw_circle(at + f * 0.2, _cell * 0.12, c)


## Where a rider is after travelling [param d] cells along [param path], and the
## trail it has drawn so far.
func _along(path: Array, d: float) -> Array:
	var pts := PackedVector2Array([_p(path[0].x, path[0].y)])
	var dir := Vector2.RIGHT
	for k in path.size() - 1:
		var a: Vector2 = path[k]
		var b: Vector2 = path[k + 1]
		var len := a.distance_to(b)
		dir = b - a
		if d <= len:
			var q := a.lerp(b, d / len)
			pts.append(_p(q.x, q.y))
			return [pts, _p(q.x, q.y), dir]
		d -= len
		pts.append(_p(b.x, b.y))
	return [pts, pts[pts.size() - 1], dir]


func _fill(x0: int, y0: int, x1: int, y1: int, c: Color) -> void:
	_art.draw_rect(Rect2(_o + Vector2(x0, y0) * _cell, Vector2(x1 - x0 + 1, y1 - y0 + 1) * _cell), c)


func _caption(text: String, at: Vector2, c: Color, size := 40) -> void:
	var f := UI.title_font
	var w := f.get_string_size(text, HORIZONTAL_ALIGNMENT_CENTER, -1, size).x
	_art.draw_string_outline(f, at - Vector2(w / 2, 0), text, HORIZONTAL_ALIGNMENT_LEFT, -1, size, 10, Color(0, 0, 0, 0.7))
	_art.draw_string(f, at - Vector2(w / 2, 0), text, HORIZONTAL_ALIGNMENT_LEFT, -1, size, c)


func _draw_city(s: Vector2) -> void:
	# A bright sky with the light gate, beams, and a skyline of towers.
	var horizon := s.y * 0.68
	_art.draw_rect(Rect2(Vector2.ZERO, Vector2(s.x, horizon)), Color(0.12, 0.32, 0.9))
	_art.draw_rect(Rect2(0, horizon * 0.6, s.x, horizon * 0.4), Color(0.6, 0.45, 0.75, 0.5))
	var gate := Vector2(s.x / 2, horizon * 0.62)
	for k in 18:
		var a := k * TAU / 18.0 + _t * 0.05
		var dash := fmod(_t * 0.6 + k * 0.37, 1.0)
		var d := Vector2(cos(a), sin(a))
		_art.draw_line(gate + d * (90 + dash * 200), gate + d * (150 + dash * 260), Color(0.7, 0.9, 1.0, 0.7), 6.0)
	_art.draw_circle(gate, 120, Color(1.0, 0.55, 0.35, 0.35))
	_art.draw_arc(gate, 92, 0, TAU, 64, Color(1.0, 0.45, 0.9), 8.0, true)
	_art.draw_circle(gate, 78, Color(1.0, 0.9, 0.7))
	var rng := RandomNumberGenerator.new()
	rng.seed = 7
	var x := -10.0
	while x < s.x:
		var w := rng.randf_range(34, 70)
		var h := rng.randf_range(60, 230)
		var r := Rect2(x, horizon - h, w - 6, h)
		_art.draw_rect(r, Color(0.08, 0.15, 0.42))
		_art.draw_rect(Rect2(r.position, Vector2(r.size.x, 4)), [CYAN, PINK, GOLD][rng.randi() % 3])
		for wy in range(int(r.position.y) + 12, int(horizon) - 8, 16):
			for wx in range(int(r.position.x) + 6, int(r.end.x) - 8, 12):
				if rng.randf() < 0.45:
					_art.draw_rect(Rect2(wx, wy, 6, 8), Color(0.85, 0.95, 1.0, 0.85))
		x += w
	# The glass floor, with a rider streaking toward the gate.
	_art.draw_rect(Rect2(0, horizon, s.x, s.y - horizon), Color(0.03, 0.2, 0.85))
	for k in 9:
		var fx := s.x * (k / 8.0)
		_art.draw_line(Vector2(s.x / 2 + (fx - s.x / 2) * 0.15, horizon), Vector2(fx, s.y), Color(0.6, 0.9, 1.0, 0.5), 2.0)
	var ride := fmod(_t * 0.35, 1.0)
	var bp := Vector2(s.x * 0.5 + sin(_t * 1.3) * 40, s.y - 40 - ride * 0.0)
	_art.draw_line(bp + Vector2(0, 60), bp, Color(CYAN, 0.5), 22.0)
	_art.draw_line(bp + Vector2(0, 60), bp, CYAN, 8.0)
	_bike(bp, Vector2.UP, CYAN)


func _draw_trail() -> void:
	var path := [Vector2(1, 10), Vector2(1, 3), Vector2(6, 3), Vector2(6, 8), Vector2(10, 8), Vector2(10, 1)]
	var total := 7.0 + 5 + 5 + 4 + 7
	var d := fmod(_t * 5.0, total + 3.0)
	var a := _along(path, minf(d, total))
	_glow_line(a[0], CYAN)
	_bike(a[1], a[2], CYAN)
	var p2 := [Vector2(11, 11), Vector2(3, 11), Vector2(3, 6)]
	var b := _along(p2, minf(d * 0.8, 13.0))
	_glow_line(b[0], PINK)
	_bike(b[1], b[2], PINK)


func _draw_loop() -> void:
	# Home: a block of cyan land. The rider leaves, loops, and comes home.
	var cycle := fmod(_t, 5.5)
	_fill(2, 6, 4, 9, Color(CYAN, 0.55))
	var path := [Vector2(4, 7), Vector2(9, 7), Vector2(9, 2), Vector2(3, 2), Vector2(3, 6)]
	var total := 5.0 + 5 + 6 + 4
	var d := minf(cycle * 6.0, total)
	var a := _along(path, d)
	if d >= total:
		var k := clampf((cycle - total / 6.0) * 2.5, 0.0, 1.0)
		_fill(3, 2, 9, 7, Color(CYAN, 0.55 * k))
		_caption("+18%", _p(6, 4.3), Color(GREEN, k), 54)
		_bike(_p(3, 6), Vector2.DOWN, CYAN)
	else:
		_glow_line(a[0], CYAN)
		_bike(a[1], a[2], CYAN)
	_caption("HOME", _p(3, 9.9), Color(1, 1, 1, 0.8), 26)


func _draw_cut() -> void:
	var cycle := fmod(_t, 4.5)
	_fill(0, 9, 2, 11, Color(CYAN, 0.5))
	_fill(9, 0, 11, 2, Color(PINK, 0.5))
	var rival := [Vector2(10, 4), Vector2(10, 6), Vector2(2, 6), Vector2(2, 4)]
	var me := [Vector2(1, 9), Vector2(1, 8), Vector2(6, 8), Vector2(6, 2)]
	var hit := 1.0 + 5.0 + 2.0  # when my bike reaches (6, 6), after the rival passed it
	var d := cycle * 4.0
	var r := _along(rival, minf(d, hit + 0.5))
	var m := _along(me, minf(d, 15.0))
	if d < hit:
		_glow_line(r[0], PINK)
		_bike(r[1], r[2], PINK)
	else:
		# The rival's trail breaks apart in a burst of sparks.
		var k := clampf((d - hit) / 4.0, 0.0, 1.0)
		for n in 14:
			var ang := n * TAU / 14.0
			_art.draw_circle(_p(6, 6) + Vector2(cos(ang), sin(ang)) * _cell * (0.5 + k * 3.0), _cell * 0.18 * (1.0 - k), PINK)
		_caption("KO!", _p(6, 4.6), Color(Color.WHITE.lerp(PINK, 0.35), 1.0 - k * 0.5), 72)
	_glow_line(m[0], CYAN)
	_bike(m[1], m[2], CYAN)


func _draw_win() -> void:
	var k := clampf(_t / 3.0, 0.0, 1.0)
	var grow := 1 + int(k * 4)
	_fill(0, 0, 2 + grow, 3 + grow, Color(CYAN, 0.6))
	_fill(9 - grow / 2, 0, 11, 2 + grow / 2, Color(PINK, 0.55))
	_fill(8, 9 - grow / 2, 11, 11, Color(GOLD, 0.55))
	_fill(0, 10, 3 + grow / 2, 11, Color(GREEN, 0.55))
	var secs := maxf(3.0 - _t, 0.0)
	_caption("0:%02d" % ceili(secs), _p(8.5, 6.3), Color(1, 1, 1, 0.9), 54)
	if k >= 1.0:
		var pulse := 1.0 + 0.08 * sin(_t * 6.0)
		_caption("YOU WIN!", _p(3.0, 9.0), Color(GOLD, 1.0), int(52 * pulse))
		var c := _p(2.5, 2.5)
		var w := _cell * 0.9
		_art.draw_colored_polygon(PackedVector2Array([c + Vector2(-w, w * 0.5), c + Vector2(-w, -w * 0.4), c + Vector2(-w * 0.5, 0), c + Vector2(0, -w * 0.7), c + Vector2(w * 0.5, 0), c + Vector2(w, -w * 0.4), c + Vector2(w, w * 0.5)]), GOLD)
