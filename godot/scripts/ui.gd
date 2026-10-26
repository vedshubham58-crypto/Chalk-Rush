class_name UI
## Small helpers for the neon look of menus and the HUD.

const CYAN := Color("00e5ff")
const PINK := Color("ff2bd6")
const GOLD := Color("ffc93c")
const GREEN := Color("39ff88")
const INK := Color("070a1c")
const CARD := Color(0.05, 0.07, 0.17, 0.82)

static var title_font: FontVariation
static var body_font: FontVariation


static func fonts() -> void:
	if title_font:
		return
	title_font = FontVariation.new()
	title_font.base_font = load("res://fonts/Orbitron.ttf")
	title_font.variation_opentype = {"wght": 900}
	body_font = FontVariation.new()
	body_font.base_font = load("res://fonts/Exo2.ttf")
	body_font.variation_opentype = {"wght": 700}
	# Letters the neon fonts don't have (accents, Hindi) come from these.
	var hindi: Font = load("res://fonts/NotoSansDevanagari.ttf")
	title_font.fallbacks = [load("res://fonts/Exo2.ttf"), hindi]
	body_font.fallbacks = [hindi]


static func label(text: String, size := 28, color := Color.WHITE, title := false, align := HORIZONTAL_ALIGNMENT_CENTER) -> Label:
	fonts()
	var l := Label.new()
	l.text = text
	l.horizontal_alignment = align
	l.add_theme_font_override("font", title_font if title else body_font)
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	l.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.75))
	l.add_theme_constant_override("outline_size", maxi(4, size / 7))
	return l


## A label with a soft coloured glow behind it, for big titles.
static func glow_label(text: String, size: int, color: Color) -> Label:
	var l := label(text, size, Color.WHITE.lerp(color, 0.25), true)
	l.add_theme_color_override("font_shadow_color", Color(color, 0.85))
	l.add_theme_constant_override("shadow_outline_size", size / 3)
	l.add_theme_constant_override("shadow_offset_x", 0)
	l.add_theme_constant_override("shadow_offset_y", 0)
	l.add_theme_color_override("font_outline_color", Color(color, 0.9))
	l.add_theme_constant_override("outline_size", size / 10)
	return l


static func box(bg: Color, border := Color.TRANSPARENT, radius := 22, border_w := 0, glow := 0) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = bg
	s.set_corner_radius_all(radius)
	if border_w > 0:
		s.border_color = border
		s.set_border_width_all(border_w)
	if glow > 0:
		s.shadow_color = Color(border, 0.55)
		s.shadow_size = glow
	s.content_margin_left = 18
	s.content_margin_right = 18
	s.content_margin_top = 10
	s.content_margin_bottom = 10
	return s


## A box with its corners cut off at an angle, for a sharper sci-fi look.
static func chamfer(bg: Color, border := Color.TRANSPARENT, cut := 22, border_w := 0, glow := 0) -> StyleBoxFlat:
	var s := box(bg, border, cut, border_w, glow)
	s.corner_detail = 1
	return s


static func button(text: String, color := PINK, size := 34, min_h := 96) -> Button:
	fonts()
	var b := Button.new()
	b.text = text
	b.custom_minimum_size = Vector2(0, min_h)
	b.add_theme_font_override("font", title_font)
	b.add_theme_font_size_override("font_size", size)
	b.add_theme_color_override("font_color", Color.WHITE)
	b.add_theme_color_override("font_pressed_color", Color.WHITE)
	b.add_theme_color_override("font_hover_color", Color.WHITE)
	b.add_theme_color_override("font_disabled_color", Color(1, 1, 1, 0.35))
	b.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.5))
	b.add_theme_constant_override("outline_size", 6)
	b.add_theme_stylebox_override("normal", chamfer(color.darkened(0.25), color.lightened(0.35), 26, 3, 14))
	b.add_theme_stylebox_override("hover", chamfer(color.darkened(0.15), color.lightened(0.5), 26, 3, 18))
	b.add_theme_stylebox_override("pressed", chamfer(color.darkened(0.45), color.lightened(0.2), 26, 3, 6))
	b.add_theme_stylebox_override("disabled", chamfer(Color(0.2, 0.22, 0.3, 0.7), Color(0.4, 0.42, 0.5), 26, 2, 0))
	b.add_theme_stylebox_override("focus", StyleBoxEmpty.new())
	b.pressed.connect(func(): Audio.play("click"))
	return b


## A small round icon button such as ◀ ▶ or pause.
static func icon_button(text: String, color := CYAN, size := 80) -> Button:
	var b := button(text, color, size / 2, size)
	b.custom_minimum_size = Vector2(size, size)
	for s in ["normal", "hover", "pressed", "disabled"]:
		var st: StyleBoxFlat = b.get_theme_stylebox(s).duplicate()
		st.set_corner_radius_all(size / 2)
		st.corner_detail = 8
		st.bg_color = Color(color.darkened(0.7), 0.85) if s != "disabled" else Color(0.1, 0.1, 0.15, 0.5)
		st.content_margin_left = 0
		st.content_margin_right = 0
		b.add_theme_stylebox_override(s, st)
	return b


static func card(color := CYAN) -> PanelContainer:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", chamfer(CARD, Color(color, 0.7), 26, 2, 10))
	return p


static func pill(text: String, color: Color, size := 26) -> PanelContainer:
	var p := PanelContainer.new()
	var st := box(Color(0.03, 0.04, 0.12, 0.8), Color(color, 0.8), 40, 2, 6)
	st.content_margin_top = 4
	st.content_margin_bottom = 4
	p.add_theme_stylebox_override("panel", st)
	var l := label(text, size, color)
	l.name = "text"
	p.add_child(l)
	return p


static func pop(c: Control, from := 0.4, time := 0.35) -> void:
	c.pivot_offset = c.size / 2
	c.scale = Vector2.ONE * from
	c.create_tween().tween_property(c, "scale", Vector2.ONE, time).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)


## Gently pulses a control's scale forever, to draw the eye.
static func breathe(c: Control, amount := 0.05, period := 1.1) -> void:
	var tw := c.create_tween().set_loops()
	tw.tween_property(c, "scale", Vector2.ONE * (1.0 + amount), period / 2).set_trans(Tween.TRANS_SINE)
	tw.tween_property(c, "scale", Vector2.ONE, period / 2).set_trans(Tween.TRANS_SINE)
	c.resized.connect(func(): c.pivot_offset = c.size / 2)
