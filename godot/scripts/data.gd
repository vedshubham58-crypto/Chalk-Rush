class_name GameData
## Riders, levels and country arenas. Everything here is fixed game content.

enum Perk { NONE, SHIELD_START, FASTER, BIG_START, LONG_POWERUPS, BIG_SPLASH, GRIND_MASTER, MORE_COINS }

## id, name, tagline, perk, perk text, cost, neon colour.
const RIDERS := [
	{"id": 0, "name": "Sparky", "tagline": "Born on the grid", "perk": Perk.NONE, "perk_text": "All-rounder", "cost": 0, "color": Color("00e5ff")},
	{"id": 1, "name": "Pip", "tagline": "Small but brave", "perk": Perk.SHIELD_START, "perk_text": "Starts every life with a longer shield", "cost": 150, "color": Color("ff4fd8")},
	{"id": 2, "name": "Zippy", "tagline": "Never walks anywhere", "perk": Perk.FASTER, "perk_text": "10% faster", "cost": 300, "color": Color("fff04f")},
	{"id": 3, "name": "Rocky", "tagline": "Likes a big garage", "perk": Perk.BIG_START, "perk_text": "Starts with more land", "cost": 450, "color": Color("4fff7a")},
	{"id": 4, "name": "Blaze", "tagline": "Hot-headed and quick", "perk": Perk.LONG_POWERUPS, "perk_text": "Power-ups last 50% longer", "cost": 600, "color": Color("ff8a2b")},
	{"id": 5, "name": "Luna", "tagline": "Rides by moonlight", "perk": Perk.BIG_SPLASH, "perk_text": "Energy bursts are bigger", "cost": 800, "color": Color("b57cff")},
	{"id": 6, "name": "Frost", "tagline": "Cool on the corners", "perk": Perk.GRIND_MASTER, "perk_text": "Grinding along trails is extra fast", "cost": 1000, "color": Color("9bf6ff")},
	{"id": 7, "name": "King Kilowatt", "tagline": "Rules the city", "perk": Perk.MORE_COINS, "perk_text": "Earns 50% more coins", "cost": 1500, "color": Color("ff4f5e")},
]

## One level per country. Early ones are small with a few timid, slower bots.
## code, bots, min/max boldness, bot speed, win percent, accent colour.
const LEVELS := [
	{"code": "IND", "bots": 3, "min_bold": 0.55, "max_bold": 0.75, "bot_speed": 0.85, "win": 20.0, "accent": Color("00e5ff")},
	{"code": "EGY", "bots": 4, "min_bold": 0.6, "max_bold": 0.8, "bot_speed": 0.88, "win": 20.0, "accent": Color("ff2bd6")},
	{"code": "FRA", "bots": 4, "min_bold": 0.65, "max_bold": 0.9, "bot_speed": 0.9, "win": 22.0, "accent": Color("ff8a00")},
	{"code": "AUS", "bots": 5, "min_bold": 0.7, "max_bold": 0.95, "bot_speed": 0.92, "win": 22.0, "accent": Color("39ff88")},
	{"code": "BRA", "bots": 5, "min_bold": 0.75, "max_bold": 1.0, "bot_speed": 0.94, "win": 25.0, "accent": Color("ff3b5c")},
	{"code": "ZAF", "bots": 6, "min_bold": 0.8, "max_bold": 1.1, "bot_speed": 0.96, "win": 25.0, "accent": Color("ffc400")},
	{"code": "SAU", "bots": 6, "min_bold": 0.85, "max_bold": 1.2, "bot_speed": 0.98, "win": 27.0, "accent": Color("9be7ff")},
	{"code": "DEU", "bots": 7, "min_bold": 0.9, "max_bold": 1.3, "bot_speed": 1.0, "win": 27.0, "accent": Color("ffb000")},
	{"code": "CHN", "bots": 7, "min_bold": 0.95, "max_bold": 1.4, "bot_speed": 1.0, "win": 30.0, "accent": Color("b388ff")},
	{"code": "USA", "bots": 7, "min_bold": 1.0, "max_bold": 1.5, "bot_speed": 1.0, "win": 30.0, "accent": Color("ff5f1f")},
]

static var _arenas := {}


static func rider(id: int) -> Dictionary:
	for r in RIDERS:
		if r.id == id:
			return r
	return RIDERS[0]


static func level(i: int) -> Dictionary:
	var l: Dictionary = LEVELS[clampi(i, 0, LEVELS.size() - 1)].duplicate()
	var a := arena(l.code)
	l["index"] = clampi(i, 0, LEVELS.size() - 1)
	l["name"] = a.name
	l["size"] = a.size
	return l


## A country outline (Natural Earth, public domain) as {name, size, mask}.
## mask has one byte per cell, row by row: 1 = inside the country.
static func arena(code: String) -> Dictionary:
	if _arenas.is_empty():
		var f := FileAccess.open("res://data/arenas.json", FileAccess.READ)
		for a in JSON.parse_string(f.get_as_text()):
			_arenas[a.code] = a
	var a: Dictionary = _arenas[code]
	if not a.has("mask"):
		var size := int(a.size)
		var bytes := Marshalls.base64_to_raw(a.bits)
		var mask := PackedByteArray()
		mask.resize(size * size)
		for i in size * size:
			mask[i] = (bytes[i >> 3] >> (i & 7)) & 1
		a["mask"] = mask
		a["size"] = size
	return a
