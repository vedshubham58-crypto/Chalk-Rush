class_name NeonGame
extends RefCounted
## The whole game: a grid of cells shaped like a country, each owned by at most
## one rider, plus everyone's light trails. No drawing here, so it can be tested
## on its own (see tests/sim.gd).
##
## Only other riders can knock you out, by touching your trail while you're away
## from home. Crossing your own trail is fine, and the country's edge is a wall.

const NONE := -1
const SPEED := 8.5
const DX := [1, 0, -1, 0]
const DY := [0, 1, 0, -1]
const START_RADIUS := 2
const RESPAWN_SECONDS := 3.0
const SPAWN_SHIELD := 2.0
const STREAK_SECONDS := 8.0
const DEATH_PAUSE := 1.5
const POWER_UP_EVERY := 4.0
const MAX_POWER_UPS := 8
const POWER_DROP_SECONDS := 8.0
const FREEZE_RANGE := 14
const MAGNET_RANGE := 5
const COIN_EVERY := 0.6
const MAX_FIELD_COINS := 30

enum Power { SPEED, SHIELD, SPLASH, FREEZE, MAGNET }
const POWER_NAMES := ["SPEED", "SHIELD", "ENERGY BURST", "FREEZE", "MAGNET"]


class Player:
	var id: int
	var rider: Dictionary
	var is_bot: bool
	var nickname := ""
	var color: Color
	## Position in cell units. Cell (i, j) covers [i, i+1) x [j, j+1).
	var x := 0.0
	var y := 0.0
	## Heading: 0 right (+x), 1 down (+y), 2 left, 3 up. Bikes only move along the grid.
	var dir := 0
	## The way this rider wants to go; taken at the next cell centre (no U-turns).
	var want := 0
	var grinding := false
	var alive := false
	var respawn_in := 0.0
	var cell_x := 0
	var cell_y := 0
	## The trail drawn since leaving home, as cell indices in order.
	var trail := PackedInt32Array()
	var land := 0
	var best_land := 0
	## A box around every cell this rider has owned since they last spawned, so a
	## capture only has to look inside it. It never shrinks, which is still correct.
	var box := Rect2i()
	var kills := 0
	var knocked_out_by := ""
	var boost := 0.0
	var shield := 0.0
	var frozen := 0.0
	var magnet := 0.0
	var coins := 0
	var brain: BotBrain

	func _init(p_id: int, p_rider: Dictionary, p_bot: bool) -> void:
		id = p_id
		rider = p_rider
		is_bot = p_bot
		color = p_rider.color

	func display_name() -> String:
		if nickname != "":
			return nickname
		return rider.name if is_bot else "You"

	func shielded() -> bool:
		return shield > 0.0


var size: int
var cells: int
var round_seconds := 45.0
var win_percent := 101.0
var rng := RandomNumberGenerator.new()
var playable: PackedByteArray
var playable_cells := PackedInt32Array()
var area := 0
var center_x := 0
var center_y := 0
var owner := PackedInt32Array()
var trail_owner := PackedInt32Array()
var players: Array[Player] = []
var me: Player
var power_ups: Array[Dictionary] = []  # {x, y, type}
var field_coins := PackedInt32Array()
var countdown := 3.0
var elapsed := 0.0
var dead_for := 0.0
var revived := false
var power_drop_used := false
var streak := 0
var best_streak := 0
var feed: Array[Dictionary] = []  # {text, at}
## Things that just happened, for effects and sounds. The screen empties it every frame.
var events: Array[Dictionary] = []
## Cells whose owner changed since the screen last looked.
var changed := PackedInt32Array()

var _power_ups_enabled := true
var _bot_speed := 1.0
var _my_speed_bonus := 0.0
var _my_shield_bonus := 0.0
var _power_up_in := 2.0
var _coin_in := 0.5
var _last_kill_at := -100.0
var _fill_queue := PackedInt32Array()
var _fill_mark := PackedInt32Array()
var _fill_stamp := 0


## [param opts]: size, mask, bots, seed, rider (my rider id), countdown, power_ups,
## min_bold, max_bold, bot_speed, win, round_seconds, speed_bonus, shield_bonus, nickname.
func _init(opts: Dictionary = {}) -> void:
	size = opts.get("size", 60)
	cells = size * size
	rng.seed = opts.get("seed", Time.get_ticks_usec())
	countdown = opts.get("countdown", 3.0)
	round_seconds = opts.get("round_seconds", 45.0)
	win_percent = opts.get("win", 101.0)
	_power_ups_enabled = opts.get("power_ups", true)
	_bot_speed = opts.get("bot_speed", 1.0)
	_my_speed_bonus = opts.get("speed_bonus", 0.0)
	_my_shield_bonus = opts.get("shield_bonus", 0.0)
	if opts.has("mask"):
		playable = opts.mask
	else:
		playable = PackedByteArray()
		playable.resize(cells)
		playable.fill(1)
	var sx := 0
	var sy := 0
	for i in cells:
		if playable[i]:
			playable_cells.append(i)
			sx += i % size
			sy += i / size
	area = playable_cells.size()
	center_x = sx / maxi(area, 1)
	center_y = sy / maxi(area, 1)
	owner.resize(cells)
	owner.fill(NONE)
	trail_owner.resize(cells)
	trail_owner.fill(NONE)
	_fill_queue.resize(cells)
	_fill_mark.resize(cells)

	var mine := GameData.rider(opts.get("rider", 0))
	me = Player.new(0, mine, false)
	me.nickname = opts.get("nickname", "")
	players.append(me)
	var others := GameData.RIDERS.filter(func(r): return r.id != mine.id)
	for i in range(others.size() - 1, 0, -1):
		var j := rng.randi_range(0, i)
		var t = others[i]
		others[i] = others[j]
		others[j] = t
	var min_bold: float = opts.get("min_bold", 0.7)
	var max_bold: float = opts.get("max_bold", 1.4)
	for i in range(1, int(opts.get("bots", 7)) + 1):
		var p := Player.new(i, others[(i - 1) % others.size()], true)
		p.brain = BotBrain.new(min_bold + rng.randf() * (max_bold - min_bold))
		players.append(p)
	for p in players:
		_spawn(p)


func percent(p: Player) -> float:
	return p.land * 100.0 / area


## Riders by land, biggest first.
func ranking() -> Array[Player]:
	var r := players.duplicate()
	r.sort_custom(func(a, b): return a.land > b.land)
	return r


## Your place in the round, 1 = most land.
func my_rank() -> int:
	var n := 1
	for p in players:
		if p.id != me.id and p.land > me.land:
			n += 1
	return n


func time_left() -> float:
	return maxf(round_seconds - elapsed, 0.0)


func won_early() -> bool:
	return me.alive and percent(me) >= win_percent


## True once time runs out, you claim enough land, or shortly after you're knocked out.
func is_over() -> bool:
	return elapsed >= round_seconds or (not me.alive and dead_for >= DEATH_PAUSE) or won_early()


## You won: still standing, and either hit the target or had the most land at the end.
func won() -> bool:
	return me.alive and (won_early() or (elapsed >= round_seconds and my_rank() == 1))


func steer(d: int) -> void:
	me.want = d


func update(dt_raw: float) -> void:
	var dt := clampf(dt_raw, 0.0, 0.05)
	if countdown > 0.0:
		countdown -= dt
		if countdown <= 0.0:
			events.append({"type": "go"})
		return
	if is_over():
		return
	elapsed += dt
	if not me.alive:
		dead_for += dt
	if _power_ups_enabled:
		_spawn_power_ups(dt)
		_spawn_coins(dt)
	for p in players:
		if not p.alive:
			if p.is_bot:
				p.respawn_in -= dt
				if p.respawn_in <= 0.0:
					_spawn(p)
			continue
		p.boost = maxf(p.boost - dt, 0.0)
		p.shield = maxf(p.shield - dt, 0.0)
		p.frozen = maxf(p.frozen - dt, 0.0)
		p.magnet = maxf(p.magnet - dt, 0.0)
		if p.brain:
			p.brain.think(self, p, dt)
		_move(p, dt)
		p.grinding = _is_grinding(p)
	if elapsed - _last_kill_at > STREAK_SECONDS:
		streak = 0
	feed = feed.filter(func(f): return elapsed - f.at <= 4.0)


## Brings you back after a knockout (once per round, for watching a video),
## on your own land if you still have some, with a shield.
func revive() -> void:
	if me.alive or revived:
		return
	revived = true
	dead_for = 0.0
	var home := -1
	for i in cells:
		if owner[i] == me.id:
			home = i
			break
	if home < 0:
		_spawn(me)
	else:
		me.cell_x = home % size
		me.cell_y = home / size
		me.x = me.cell_x + 0.5
		me.y = me.cell_y + 0.5
		me.alive = true
		me.knocked_out_by = ""
	me.shield = 3.0


## The mid-round Power Drop is on offer: about halfway through, while you're riding.
func power_drop_offered() -> bool:
	return not power_drop_used and me.alive and countdown <= 0.0 and not is_over() \
		and elapsed >= round_seconds * 0.4 and elapsed <= round_seconds * 0.4 + POWER_DROP_SECONDS


## The reward for the Power Drop video: a shield and a speed boost.
func power_drop() -> void:
	if power_drop_used or not me.alive:
		return
	power_drop_used = true
	me.shield = maxf(me.shield, 5.0)
	me.boost = maxf(me.boost, 3.0)
	events.append({"type": "picked", "player": me.id, "power": Power.SHIELD, "x": me.x, "y": me.y})


func skip_power_drop() -> void:
	power_drop_used = true


func speed_of(p: Player) -> float:
	var s := SPEED
	if p.is_bot:
		s *= _bot_speed
	if p.rider.perk == GameData.Perk.FASTER:
		s *= 1.1
	if p.id == 0:
		s *= 1.0 + _my_speed_bonus
	if p.boost > 0.0:
		s *= 1.6
	if p.grinding:
		s *= 1.6 if p.rider.perk == GameData.Perk.GRIND_MASTER else 1.35
	if p.frozen > 0.0:
		s *= 0.5
	return s


func in_bounds(x: int, y: int) -> bool:
	return x >= 0 and y >= 0 and x < size and y < size and playable[y * size + x] == 1


func _is_grinding(p: Player) -> bool:
	if p.trail.is_empty():
		return false
	var side := (p.dir + 1) % 4
	for sign in [1, -1]:
		var x: int = p.cell_x + DX[side] * sign
		var y: int = p.cell_y + DY[side] * sign
		if not in_bounds(x, y):
			continue
		var t := trail_owner[y * size + x]
		if t != NONE and t != p.id:
			return true
	return false


## Light-cycle movement: straight along the grid, turning 90 degrees only at cell
## centres. The country's edge turns you aside instead of stopping you.
func _move(p: Player, dt: float) -> void:
	var remaining := speed_of(p) * dt
	var guard := 0
	while remaining > 1e-5 and p.alive and guard < 16:
		guard += 1
		var cx := p.cell_x + 0.5
		var cy := p.cell_y + 0.5
		var along: float = (p.x - cx) * DX[p.dir] + (p.y - cy) * DY[p.dir]
		if absf(along) < 1e-4:
			p.x = cx
			p.y = cy
			if p.want != p.dir and p.want != (p.dir + 2) % 4:
				p.dir = p.want
			if not in_bounds(p.cell_x + DX[p.dir], p.cell_y + DY[p.dir]):
				p.dir = _wall_turn(p)
				p.want = p.dir
		var to_center: float = -along if along < -1e-4 else 1.0 - maxf(along, 0.0)
		var step := minf(remaining, to_center)
		p.x += DX[p.dir] * step
		p.y += DY[p.dir] * step
		remaining -= step
		var nx := floori(p.x)
		var ny := floori(p.y)
		if (nx != p.cell_x or ny != p.cell_y) and in_bounds(nx, ny):
			if not _enter(p, nx, ny):
				return
		if step == to_center:
			p.x = p.cell_x + 0.5
			p.y = p.cell_y + 0.5


## Which way to turn at the wall: towards the side with more room.
func _wall_turn(p: Player) -> int:
	var a := (p.dir + 1) % 4
	var b := (p.dir + 3) % 4
	var room_a := _room_to(p, a)
	var room_b := _room_to(p, b)
	if room_a == 0 and room_b == 0:
		return (p.dir + 2) % 4
	return a if room_a >= room_b else b


func _room_to(p: Player, d: int) -> int:
	var n := 0
	var x: int = p.cell_x + DX[d]
	var y: int = p.cell_y + DY[d]
	while in_bounds(x, y) and n < size:
		n += 1
		x += DX[d]
		y += DY[d]
	return n


## Steps [param p] into cell (cx, cy). Returns false if they got knocked out.
func _enter(p: Player, cx: int, cy: int) -> bool:
	p.cell_x = cx
	p.cell_y = cy
	var i := cy * size + cx
	var line_owner := trail_owner[i]
	if line_owner != NONE and line_owner != p.id:
		var victim := players[line_owner]
		# Your cut always lands: a bot's shield only stops other bots.
		if victim.shielded() and not (p.id == me.id and victim.is_bot):
			events.append({"type": "blocked", "player": victim.id, "x": cx + 0.5, "y": cy + 0.5})
		else:
			_knock_out(victim, p)
	if owner[i] == p.id:
		if not p.trail.is_empty():
			_capture(p)
	elif trail_owner[i] == NONE:
		trail_owner[i] = p.id
		p.trail.append(i)
	if not power_ups.is_empty():
		_pick_up(p)
	if not field_coins.is_empty():
		_pick_coins(p)
	return p.alive


func _set_owner(i: int, id: int) -> void:
	var old := owner[i]
	if old == id:
		return
	if old != NONE:
		players[old].land -= 1
	owner[i] = id
	if id != NONE:
		var p := players[id]
		p.land += 1
		var c := Vector2i(i % size, i / size)
		p.box = Rect2i(c, Vector2i.ZERO) if p.land == 1 else p.box.expand(c)
	changed.append(i)


## [param p] got back home: their trail and everything it encloses becomes theirs.
func _capture(p: Player) -> void:
	var before := p.land
	for i in p.trail:
		trail_owner[i] = NONE
		_set_owner(i, p.id)
	p.trail.clear()

	# Flood in from outside the box around this rider's land. Whatever the flood
	# can't reach is enclosed. Cells outside the box can't be enclosed anyway.
	var x0 := maxi(p.box.position.x - 1, 0)
	var y0 := maxi(p.box.position.y - 1, 0)
	var x1 := mini(p.box.end.x + 1, size - 1)
	var y1 := mini(p.box.end.y + 1, size - 1)
	_fill_stamp += 1
	var stamp := _fill_stamp
	var head := 0
	var tail := 0
	var pid := p.id
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			var i := y * size + x
			var edge := x == x0 or x == x1 or y == y0 or y == y1
			if (edge or playable[i] == 0) and owner[i] != pid:
				_fill_mark[i] = stamp
				_fill_queue[tail] = i
				tail += 1
	while head < tail:
		var i := _fill_queue[head]
		head += 1
		var x := i % size
		var y := i / size
		if x > x0:
			tail = _flood(i - 1, stamp, pid, tail)
		if x < x1:
			tail = _flood(i + 1, stamp, pid, tail)
		if y > y0:
			tail = _flood(i - size, stamp, pid, tail)
		if y < y1:
			tail = _flood(i + size, stamp, pid, tail)
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			var i := y * size + x
			if _fill_mark[i] != stamp and owner[i] != pid and playable[i] == 1:
				_set_owner(i, pid)
	_after_land_grab(p, before)


func _flood(n: int, stamp: int, pid: int, tail: int) -> int:
	if _fill_mark[n] != stamp and owner[n] != pid:
		_fill_mark[n] = stamp
		_fill_queue[tail] = n
		return tail + 1
	return tail


func _after_land_grab(p: Player, before: int) -> void:
	p.best_land = maxi(p.best_land, p.land)
	events.append({"type": "captured", "player": p.id, "gained": p.land - before, "x": p.x, "y": p.y})
	for other in players:
		if other.alive and other.id != p.id and other.land == 0:
			_knock_out(other, p)


func _knock_out(victim: Player, by: Player) -> void:
	if not victim.alive:
		return
	victim.alive = false
	victim.knocked_out_by = by.display_name() if by else ""
	for i in victim.trail:
		trail_owner[i] = NONE
	victim.trail.clear()
	# The knocker takes all the loser's land. Your own land stays so a revive can bring you back on it.
	if victim.id != me.id:
		var taker: Player = by if by and by.alive and by.id != victim.id else null
		var before := taker.land if taker else 0
		var b := victim.box
		for y in range(b.position.y, b.end.y + 1):
			for x in range(b.position.x, b.end.x + 1):
				var i := y * size + x
				if owner[i] == victim.id:
					_set_owner(i, taker.id if taker else NONE)
		if taker and taker.land > before:
			taker.best_land = maxi(taker.best_land, taker.land)
			events.append({"type": "captured", "player": taker.id, "gained": taker.land - before, "x": victim.x, "y": victim.y})
	victim.respawn_in = RESPAWN_SECONDS
	victim.boost = 0.0
	victim.shield = 0.0
	victim.frozen = 0.0
	victim.magnet = 0.0
	events.append({"type": "ko", "victim": victim.id, "killer": by.id if by else -1, "x": victim.x, "y": victim.y})
	if by:
		by.kills += 1
		feed.append({"text": "%s knocked out %s" % [by.display_name(), victim.display_name()], "at": elapsed})
		if by.id == me.id:
			streak = streak + 1 if elapsed - _last_kill_at <= STREAK_SECONDS else 1
			_last_kill_at = elapsed
			best_streak = maxi(best_streak, streak)
			if streak >= 2:
				events.append({"type": "streak", "count": streak})


func _spawn_power_ups(dt: float) -> void:
	_power_up_in -= dt
	if _power_up_in > 0.0:
		return
	_power_up_in = POWER_UP_EVERY
	if power_ups.size() >= MAX_POWER_UPS:
		return
	for k in 40:
		var i := playable_cells[rng.randi_range(0, area - 1)]
		if owner[i] == NONE and trail_owner[i] == NONE:
			power_ups.append({"x": i % size, "y": i / size, "type": rng.randi_range(0, 4)})
			return


func _pick_up(p: Player) -> void:
	for k in power_ups.size():
		var pu: Dictionary = power_ups[k]
		if absi(pu.x - p.cell_x) > 1 or absi(pu.y - p.cell_y) > 1:
			continue
		power_ups.remove_at(k)
		var longer := 1.5 if p.rider.perk == GameData.Perk.LONG_POWERUPS else 1.0
		events.append({"type": "picked", "player": p.id, "power": pu.type, "x": pu.x + 0.5, "y": pu.y + 0.5})
		match pu.type:
			Power.SPEED:
				p.boost = 3.0 * longer
			Power.SHIELD:
				p.shield = 5.0 * longer
			Power.SPLASH:
				_splash(p)
			Power.FREEZE:
				for o in players:
					if o.alive and o.id != p.id and absi(o.cell_x - p.cell_x) + absi(o.cell_y - p.cell_y) < FREEZE_RANGE:
						o.frozen = 3.0 * longer
			Power.MAGNET:
				p.magnet = 6.0 * longer
		return


func _spawn_coins(dt: float) -> void:
	_coin_in -= dt
	if _coin_in > 0.0:
		return
	_coin_in = COIN_EVERY
	if field_coins.size() >= MAX_FIELD_COINS:
		return
	for k in 20:
		var i := playable_cells[rng.randi_range(0, area - 1)]
		if owner[i] == NONE and trail_owner[i] == NONE and not field_coins.has(i):
			field_coins.append(i)
			return


## Only you collect coins; bots ride over them. A magnet pulls them in from further away.
func _pick_coins(p: Player) -> void:
	if p.id != me.id:
		return
	var reach := MAGNET_RANGE if p.magnet > 0.0 else 1
	var k := field_coins.size() - 1
	while k >= 0:
		var i := field_coins[k]
		var x := i % size
		var y := i / size
		if absi(x - p.cell_x) <= reach and absi(y - p.cell_y) <= reach:
			field_coins.remove_at(k)
			p.coins += 1
			events.append({"type": "coin", "x": x + 0.5, "y": y + 0.5})
		k -= 1


## Paints a blob of land around [param p] in one go.
func _splash(p: Player) -> void:
	var r := 6 if p.rider.perk == GameData.Perk.BIG_SPLASH else 4
	var before := p.land
	for y in range(p.cell_y - r, p.cell_y + r + 1):
		for x in range(p.cell_x - r, p.cell_x + r + 1):
			if not in_bounds(x, y):
				continue
			var dx := x - p.cell_x
			var dy := y - p.cell_y
			if dx * dx + dy * dy > r * r:
				continue
			var i := y * size + x
			if trail_owner[i] == p.id:
				continue
			_set_owner(i, p.id)
	# Now standing on own land: the trail drawn so far closes a loop too.
	if not p.trail.is_empty():
		_capture(p)
	else:
		_after_land_grab(p, before)


## Puts [param p] on the board with a small square of starting land.
func _spawn(p: Player) -> void:
	var radius := START_RADIUS + 1 if p.rider.perk == GameData.Perk.BIG_START else START_RADIUS
	var cx := 0
	var cy := 0
	var found := false
	for attempt in 400:
		var c := playable_cells[rng.randi_range(0, area - 1)]
		cx = c % size
		cy = c / size
		if _area_is_free(cx, cy, radius + 2):
			found = true
			break
	if not found:
		# Crowded: settle for any spot whose own square fits inside the arena.
		for attempt in 400:
			var c := playable_cells[rng.randi_range(0, area - 1)]
			cx = c % size
			cy = c / size
			if _fits_inside(cx, cy, radius):
				break
	for y in range(cy - radius, cy + radius + 1):
		for x in range(cx - radius, cx + radius + 1):
			if not in_bounds(x, y):
				continue
			var i := y * size + x
			var line := trail_owner[i]
			if line != NONE and line != p.id:
				_knock_out(players[line], p)
			_set_owner(i, p.id)
	p.x = cx + 0.5
	p.y = cy + 0.5
	p.cell_x = cx
	p.cell_y = cy
	p.dir = rng.randi_range(0, 3)
	p.want = p.dir
	p.grinding = false
	p.alive = true
	p.knocked_out_by = ""
	p.boost = 0.0
	p.shield = (5.0 if p.rider.perk == GameData.Perk.SHIELD_START else SPAWN_SHIELD) + (_my_shield_bonus if p.id == 0 else 0.0)
	p.best_land = maxi(p.best_land, p.land)
	if p.brain:
		p.brain.reset()
	events.append({"type": "spawn", "player": p.id, "x": p.x, "y": p.y})


func _fits_inside(cx: int, cy: int, r: int) -> bool:
	for y in range(cy - r, cy + r + 1):
		for x in range(cx - r, cx + r + 1):
			if not in_bounds(x, y):
				return false
	return true


func _area_is_free(cx: int, cy: int, r: int) -> bool:
	for y in range(cy - r, cy + r + 1):
		for x in range(cx - r, cx + r + 1):
			if not in_bounds(x, y):
				return false
			var i := y * size + x
			if owner[i] != NONE or trail_owner[i] != NONE:
				return false
	for o in players:
		if o.alive and absi(o.cell_x - cx) <= r and absi(o.cell_y - cy) <= r:
			return false
	return true


## The nearest cell [param p] owns, searching outward in square rings, or Vector2i(-1, -1).
func nearest_own_cell(p: Player) -> Vector2i:
	for r in size:
		var x0 := p.cell_x - r
		var x1 := p.cell_x + r
		var y0 := p.cell_y - r
		var y1 := p.cell_y + r
		for x in range(x0, x1 + 1):
			for y in [y0, y1]:
				if in_bounds(x, y) and owner[y * size + x] == p.id:
					return Vector2i(x, y)
		for y in range(y0 + 1, y1):
			for x in [x0, x1]:
				if in_bounds(x, y) and owner[y * size + x] == p.id:
					return Vector2i(x, y)
		if x0 < 0 and y0 < 0 and x1 >= size and y1 >= size:
			break
	return Vector2i(-1, -1)
