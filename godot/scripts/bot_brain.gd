class_name BotBrain
extends RefCounted
## Simple rules for a computer rider: leave home, draw a box-shaped loop, run home
## when someone gets close, grab nearby power-ups, and cut enemy trails in reach.
## Boldness above 1 means longer trips and more attacks.

const LOOKAHEAD := 5
const HALF_PI := PI / 2.0

var boldness: float
var _think_in := 0.0
var _leg_time := 0.0
var _legs_left := 0
var _turn_sign := 1.0
var _going_home := false
var _was_home := true
## The heading this bot wants, as an angle (0 = +x, PI/2 = +y).
var _target := 0.0


func _init(p_boldness: float) -> void:
	boldness = p_boldness


func reset() -> void:
	_think_in = 0.0
	_legs_left = 0
	_going_home = false
	_was_home = true


static func dir_of(angle: float) -> int:
	return posmod(roundi(angle / HALF_PI), 4)


func think(game: NeonGame, me: NeonGame.Player, dt: float) -> void:
	_leg_time -= dt
	_think_in -= dt
	if _think_in > 0.0:
		return
	_think_in = 0.1

	var at_home := me.trail.is_empty()
	if at_home:
		if not _was_home or _legs_left <= 0:
			_plan_trip(game, me)
		_was_home = true
		_going_home = false
	else:
		_was_home = false

	var desired := me.want * HALF_PI if not at_home else _target
	if not at_home:
		var home := game.nearest_own_cell(me)
		var home_dist := _dist(me, home.x, home.y) if home.x >= 0 else 0.0
		var scared := not me.shielded() and _danger(game, me) < home_dist + 4.0
		if home.x >= 0 and (_going_home or scared or me.trail.size() > 45 * boldness):
			_going_home = true
			desired = _angle_to(me, home.x, home.y)
		else:
			var aim = _attack_target(game, me)
			if aim == null:
				aim = _power_up_target(game, me)
			if aim != null:
				desired = aim
			elif _leg_time <= 0.0:
				_legs_left -= 1
				if _legs_left <= 0:
					_going_home = true
				else:
					desired = me.dir * HALF_PI + _turn_sign * HALF_PI
					_leg_time = _leg_length(game)
	me.want = _safest(game, me, desired)


func _plan_trip(game: NeonGame, me: NeonGame.Player) -> void:
	_legs_left = 3
	_turn_sign = 1.0 if game.rng.randf() < 0.5 else -1.0
	_leg_time = _leg_length(game)
	# Head roughly toward open space in the middle of the country.
	_target = _angle_to(me, game.center_x, game.center_y) + (game.rng.randf() - 0.5) * PI * 1.4


func _leg_length(game: NeonGame) -> float:
	return (0.7 + game.rng.randf() * 1.3) * boldness


## How close the nearest enemy is to any part of my trail.
func _danger(game: NeonGame, me: NeonGame.Player) -> float:
	var best := INF
	for other in game.players:
		if not other.alive or other.id == me.id:
			continue
		if _dist(me, floori(other.x), floori(other.y)) > me.trail.size() + 20:
			continue
		for k in range(0, me.trail.size(), 2):
			var i := me.trail[k]
			var d := Vector2(other.x - (i % game.size + 0.5), other.y - (i / game.size + 0.5)).length()
			if d < best:
				best = d
	return best


## Angle toward an enemy trail I can reach before its owner gets home, or null.
func _attack_target(game: NeonGame, me: NeonGame.Player):
	if boldness < 0.9:
		return null
	for other in game.players:
		if not other.alive or other.id == me.id or other.trail.is_empty() or other.shielded():
			continue
		for i in other.trail:
			var x := i % game.size
			var y := i / game.size
			var d := _dist(me, x, y)
			if d < 7.0 and d + 1.0 < _dist(other, x, y):
				return _angle_to(me, x, y)
	return null


func _power_up_target(game: NeonGame, me: NeonGame.Player):
	var best = null
	var best_d := 8.0
	for pu in game.power_ups:
		var d := _dist(me, pu.x, pu.y)
		if d < best_d:
			best_d = d
			best = pu
	return _angle_to(me, best.x, best.y) if best else null


## The grid direction nearest to [param desired] that doesn't run into my own
## trail or the wall soon. Bikes can't U-turn, so straight back is never an option.
func _safest(game: NeonGame, me: NeonGame.Player, desired: float) -> int:
	var want := dir_of(desired)
	var back := (me.dir + 2) % 4
	var order := [want, (want + 1) % 4, (want + 3) % 4, (want + 2) % 4]
	order.sort_custom(func(a, b): return _rank(desired, want, a) < _rank(desired, want, b))
	var best := me.dir
	var best_room := -1
	for d in order:
		if d == back:
			continue
		var room := _room_ahead(game, me, d)
		if room >= LOOKAHEAD:
			return d
		if room > best_room:
			best_room = room
			best = d
	return best


func _rank(desired: float, want: int, d: int) -> float:
	return 0.0 if d == want else absf(angle_difference(desired, d * HALF_PI)) + 1.0


## Open cells going straight in [param d] from where the next turn happens, up to
## LOOKAHEAD. Reaching home counts as safe; my own trail or the wall stops it.
func _room_ahead(game: NeonGame, me: NeonGame.Player, d: int) -> int:
	var x := me.cell_x
	var y := me.cell_y
	var along: float = (me.x - (x + 0.5)) * NeonGame.DX[me.dir] + (me.y - (y + 0.5)) * NeonGame.DY[me.dir]
	if along > 1e-4:
		# Already past this cell's centre, so the turn happens one cell on.
		x += NeonGame.DX[me.dir]
		y += NeonGame.DY[me.dir]
		if _cell_risk(game, me, x, y) < 0:
			return 0
	for k in range(1, LOOKAHEAD + 1):
		x += NeonGame.DX[d]
		y += NeonGame.DY[d]
		var risk := _cell_risk(game, me, x, y)
		if risk > 0:
			return LOOKAHEAD
		if risk < 0:
			return k - 1
	return LOOKAHEAD


## 1 = home (safe), -1 = my own trail or off the board, 0 = open ground.
func _cell_risk(game: NeonGame, me: NeonGame.Player, x: int, y: int) -> int:
	if not game.in_bounds(x, y):
		return -1
	var i := y * game.size + x
	if game.owner[i] == me.id:
		return 1
	if game.trail_owner[i] == me.id:
		return -1
	return 0


func _dist(p: NeonGame.Player, x: int, y: int) -> float:
	return Vector2(p.x - (x + 0.5), p.y - (y + 0.5)).length()


func _angle_to(p: NeonGame.Player, x: int, y: int) -> float:
	return atan2(y + 0.5 - p.y, x + 0.5 - p.x)
