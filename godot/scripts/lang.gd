extends Node
## Menu languages. Every English text in the game is its own key, so labels
## and buttons translate themselves; texts with numbers go through tr() first.
## Starts in the phone's language when we have it, and can be changed in the lobby.

const LANGUAGES := [["en", "English"], ["hi", "हिन्दी"], ["es", "Español"], ["pt", "Português"], ["id", "Indonesia"]]

## English -> [Hindi, Spanish, Portuguese, Indonesian].
const TEXT := {
	# Lobby
	"LIGHT BIKE LAND WARS": ["लाइट बाइक लैंड वॉर्स", "GUERRA DE TIERRAS EN MOTO DE LUZ", "GUERRA DE TERRAS EM MOTO DE LUZ", "PERANG LAHAN MOTOR CAHAYA"],
	"Loop the land. Cut their trails. Rule the Grid.": ["ज़मीन घेरो। उनकी लाइन काटो। ग्रिड पर राज करो।", "Rodea la tierra. Corta sus rastros. Domina la Red.", "Cerque a terra. Corte os rastros. Domine a Grade.", "Lingkari lahan. Potong jejak mereka. Kuasai Grid."],
	"STORY": ["कहानी", "HISTORIA", "HISTÓRIA", "CERITA"],
	"PLAY": ["खेलो", "JUGAR", "JOGAR", "MAIN"],
	"MISSIONS": ["मिशन", "MISIONES", "MISSÕES", "MISI"],
	"DAILY": ["रोज़ाना", "DIARIO", "DIÁRIO", "HARIAN"],
	"GARAGE": ["गैराज", "GARAJE", "GARAGEM", "GARASI"],
	"LEVEL %d / %d%s": ["लेवल %d / %d%s", "NIVEL %d / %d%s", "NÍVEL %d / %d%s", "LEVEL %d / %d%s"],
	"Claim %d%% of the land to win": ["जीतने के लिए %d%% ज़मीन लो", "Toma el %d%% de la tierra para ganar", "Conquiste %d%% da terra para vencer", "Kuasai %d%% lahan untuk menang"],
	"UNLOCK  ● %d": ["अनलॉक  ● %d", "DESBLOQUEAR  ● %d", "DESBLOQUEAR  ● %d", "BUKA  ● %d"],
	"Win rounds to earn coins": ["सिक्के कमाने के लिए राउंड जीतो", "Gana rondas para ganar monedas", "Vença rodadas para ganhar moedas", "Menangkan ronde untuk dapat koin"],
	"CLOSE": ["बंद करो", "CERRAR", "FECHAR", "TUTUP"],
	"New missions every day": ["हर दिन नए मिशन", "Misiones nuevas cada día", "Missões novas todo dia", "Misi baru setiap hari"],
	"CLAIM": ["लो", "RECLAMAR", "PEGAR", "AMBIL"],
	"Finish all 3 for a bonus of ● %d": ["तीनों पूरे करो, बोनस ● %d", "Completa las 3 y gana ● %d extra", "Complete as 3 e ganhe ● %d extra", "Selesaikan ketiganya untuk bonus ● %d"],
	"Come back every day for bigger rewards!": ["हर दिन आओ, इनाम बड़ा होता जाएगा!", "¡Vuelve cada día por premios mayores!", "Volte todo dia para prêmios maiores!", "Kembali tiap hari untuk hadiah lebih besar!"],
	"DAY %d": ["दिन %d", "DÍA %d", "DIA %d", "HARI %d"],
	"CLAIM  ● %d": ["लो  ● %d", "RECLAMAR  ● %d", "PEGAR  ● %d", "AMBIL  ● %d"],
	"▶ CLAIM x2  ● %d": ["▶ दोगुना लो  ● %d", "▶ RECLAMAR x2  ● %d", "▶ PEGAR x2  ● %d", "▶ AMBIL x2  ● %d"],
	"Claimed! Come back tomorrow.": ["मिल गया! कल फिर आना।", "¡Reclamado! Vuelve mañana.", "Pego! Volte amanhã.", "Sudah diambil! Kembali besok."],
	"DAILY REWARD": ["रोज़ का इनाम", "PREMIO DIARIO", "PRÊMIO DIÁRIO", "HADIAH HARIAN"],
	"Upgrades work for every rider": ["अपग्रेड हर राइडर पर लागू", "Las mejoras sirven para todos", "Melhorias valem para todos", "Upgrade berlaku untuk semua pembalap"],
	"ENGINE": ["इंजन", "MOTOR", "MOTOR", "MESIN"],
	"ARMOR": ["कवच", "BLINDAJE", "BLINDAGEM", "PELINDUNG"],
	"+3% top speed per level": ["हर लेवल पर +3% रफ़्तार", "+3% de velocidad por nivel", "+3% de velocidade por nível", "+3% kecepatan per level"],
	"+0.6 s spawn shield per level": ["हर लेवल पर +0.6 सेकंड ढाल", "+0,6 s de escudo por nivel", "+0,6 s de escudo por nível", "+0,6 dtk perisai per level"],
	"UPGRADE  ● %d": ["अपग्रेड  ● %d", "MEJORAR  ● %d", "MELHORAR  ● %d", "UPGRADE  ● %d"],
	"MAX": ["फुल", "MÁX", "MÁX", "MAKS"],
	# Missions
	"Play %d rounds": ["%d राउंड खेलो", "Juega %d rondas", "Jogue %d rodadas", "Main %d ronde"],
	"Knock out %d riders": ["%d राइडर्स को आउट करो", "Elimina a %d pilotos", "Derrube %d pilotos", "Kalahkan %d pembalap"],
	"Claim %d%% land in one round": ["एक राउंड में %d%% ज़मीन लो", "Toma el %d%% en una ronda", "Conquiste %d%% numa rodada", "Kuasai %d%% lahan dalam satu ronde"],
	"Win %d round": ["%d राउंड जीतो", "Gana %d ronda", "Vença %d rodada", "Menangkan %d ronde"],
	"Grab %d power-ups": ["%d पावर-अप लो", "Agarra %d potenciadores", "Pegue %d power-ups", "Ambil %d power-up"],
	"Collect %d coins on the land": ["ज़मीन पर %d सिक्के उठाओ", "Recoge %d monedas en la tierra", "Colete %d moedas na terra", "Kumpulkan %d koin di lahan"],
	"Close %d loops": ["%d लूप पूरे करो", "Cierra %d bucles", "Feche %d voltas", "Tutup %d putaran"],
	"Finish top 3 in %d rounds": ["%d राउंड में टॉप 3 में आओ", "Termina top 3 en %d rondas", "Fique no top 3 em %d rodadas", "Masuk 3 besar di %d ronde"],
	# Round
	"Tap left or right to turn": ["मुड़ने के लिए बाएँ या दाएँ टैप करो", "Toca izquierda o derecha para girar", "Toque à esquerda ou direita para virar", "Ketuk kiri atau kanan untuk belok"],
	"GO!": ["चलो!", "¡YA!", "VAI!", "JALAN!"],
	"▶ POWER DROP": ["▶ पावर ड्रॉप", "▶ SUPER PODER", "▶ SUPER PODER", "▶ POWER DROP"],
	"You knocked out %s": ["तुमने %s को आउट किया", "Eliminaste a %s", "Você derrubou %s", "Kamu mengalahkan %s"],
	"Knocked out by %s": ["%s ने आउट किया", "Te eliminó %s", "Derrubado por %s", "Dikalahkan oleh %s"],
	"Knocked out!": ["आउट!", "¡Eliminado!", "Derrubado!", "Kalah!"],
	"SPEED": ["रफ़्तार", "VELOCIDAD", "VELOCIDADE", "KECEPATAN"],
	"SHIELD": ["ढाल", "ESCUDO", "ESCUDO", "PERISAI"],
	"ENERGY BURST": ["ऊर्जा धमाका", "ESTALLIDO", "EXPLOSÃO", "LEDAKAN ENERGI"],
	"FREEZE": ["फ्रीज़", "CONGELAR", "CONGELAR", "BEKU"],
	"MAGNET": ["चुंबक", "IMÁN", "ÍMÃ", "MAGNET"],
	"DOUBLE KO!": ["डबल KO!", "¡DOBLE KO!", "KO DUPLO!", "KO GANDA!"],
	"TRIPLE KO!": ["ट्रिपल KO!", "¡TRIPLE KO!", "KO TRIPLO!", "KO TIGA!"],
	"UNSTOPPABLE!": ["रुकना नामुमकिन!", "¡IMPARABLE!", "IMPARÁVEL!", "TAK TERHENTIKAN!"],
	"PAUSED": ["रुका हुआ", "PAUSA", "PAUSADO", "JEDA"],
	"RESUME": ["जारी रखो", "SEGUIR", "CONTINUAR", "LANJUT"],
	"QUIT ROUND": ["राउंड छोड़ो", "SALIR", "SAIR", "KELUAR"],
	"KNOCKED OUT": ["आउट हो गए", "ELIMINADO", "DERRUBADO", "KALAH"],
	"▶ REVIVE": ["▶ फिर से ज़िंदा", "▶ REVIVIR", "▶ REVIVER", "▶ HIDUP LAGI"],
	"NO THANKS": ["नहीं, धन्यवाद", "NO, GRACIAS", "NÃO, OBRIGADO", "TIDAK"],
	# Results
	"VICTORY!": ["जीत!", "¡VICTORIA!", "VITÓRIA!", "MENANG!"],
	"TIME UP": ["समय खत्म", "SE ACABÓ", "TEMPO ESGOTADO", "WAKTU HABIS"],
	"%s CONQUERED!": ["%s जीत लिया!", "¡%s CONQUISTADA!", "%s CONQUISTADA!", "%s DIKUASAI!"],
	"#%d of %d riders": ["#%d (कुल %d राइडर्स)", "#%d de %d pilotos", "#%d de %d pilotos", "#%d dari %d pembalap"],
	"%d WIN STREAK!": ["लगातार %d जीत!", "¡%d VICTORIAS SEGUIDAS!", "%d VITÓRIAS SEGUIDAS!", "%d KALI MENANG BERUNTUN!"],
	"So close! Only %.1f%% behind %s": ["बस थोड़ा सा! सिर्फ़ %.1f%% पीछे: %s", "¡Casi! Solo %.1f%% detrás de %s", "Quase! Só %.1f%% atrás de %s", "Hampir! Cuma %.1f%% di belakang %s"],
	"Claim %d%% to conquer %s": ["%d%% लो और %s जीतो", "Toma el %d%% para conquistar %s", "Conquiste %d%% para dominar %s", "Kuasai %d%% untuk menaklukkan %s"],
	"Land": ["ज़मीन", "Tierra", "Terra", "Lahan"],
	"Knockouts": ["आउट किए", "Eliminaciones", "Derrubadas", "KO"],
	"Coins": ["सिक्के", "Monedas", "Moedas", "Koin"],
	"Streak bonus": ["लगातार जीत बोनस", "Bono de racha", "Bônus de sequência", "Bonus beruntun"],
	"Bonus Rush": ["बोनस रश", "Bonus Rush", "Bonus Rush", "Bonus Rush"],
	"XP": ["XP", "XP", "XP", "XP"],
	"Rider level %d": ["राइडर लेवल %d", "Nivel de piloto %d", "Nível de piloto %d", "Level pembalap %d"],
	"MISSION DONE: ": ["मिशन पूरा: ", "MISIÓN HECHA: ", "MISSÃO FEITA: ", "MISI SELESAI: "],
	"Mission: ": ["मिशन: ", "Misión: ", "Missão: ", "Misi: "],
	"NEXT: %s ▶": ["अगला: %s ▶", "SIGUIENTE: %s ▶", "PRÓXIMO: %s ▶", "BERIKUTNYA: %s ▶"],
	"ONE MORE ROUND!": ["एक और राउंड!", "¡UNA RONDA MÁS!", "MAIS UMA RODADA!", "SATU RONDE LAGI!"],
	"PLAY AGAIN": ["फिर से खेलो", "JUGAR DE NUEVO", "JOGAR DE NOVO", "MAIN LAGI"],
	"HOME": ["होम", "INICIO", "INÍCIO", "BERANDA"],
	"▶ x2 COINS +%d": ["▶ दोगुने सिक्के +%d", "▶ MONEDAS x2 +%d", "▶ MOEDAS x2 +%d", "▶ KOIN x2 +%d"],
	"COINS DOUBLED!": ["सिक्के दोगुने!", "¡MONEDAS DOBLES!", "MOEDAS EM DOBRO!", "KOIN BERLIPAT!"],
	# Bonus Rush
	"BONUS RUSH!": ["बोनस रश!", "¡BONUS RUSH!", "BONUS RUSH!", "BONUS RUSH!"],
	"Tap the coins, gems and boxes!": ["सिक्के, रत्न और बक्से टैप करो!", "¡Toca monedas, gemas y cajas!", "Toque nas moedas, joias e caixas!", "Ketuk koin, permata, dan kotak!"],
	"BOX!": ["बक्सा!", "¡CAJA!", "CAIXA!", "KOTAK!"],
	"%d mystery boxes opened!": ["%d रहस्य बक्से खुले!", "¡%d cajas sorpresa abiertas!", "%d caixas surpresa abertas!", "%d kotak misteri dibuka!"],
	"Mystery box opened!": ["रहस्य बक्सा खुला!", "¡Caja sorpresa abierta!", "Caixa surpresa aberta!", "Kotak misteri dibuka!"],
	"▶ GOLDEN BOX  +%d": ["▶ सुनहरा बक्सा  +%d", "▶ CAJA DORADA  +%d", "▶ CAIXA DOURADA  +%d", "▶ KOTAK EMAS  +%d"],
	"GOLDEN BOX OPENED!": ["सुनहरा बक्सा खुला!", "¡CAJA DORADA ABIERTA!", "CAIXA DOURADA ABERTA!", "KOTAK EMAS DIBUKA!"],
	"COLLECT": ["इकट्ठा करो", "RECOGER", "COLETAR", "KUMPULKAN"],
	# Story
	"MISSION BRIEFING": ["मिशन ब्रीफ़िंग", "INFORME DE MISIÓN", "BRIEFING DA MISSÃO", "BRIEFING MISI"],
	"SKIP": ["छोड़ो", "SALTAR", "PULAR", "LEWATI"],
	"NEXT": ["आगे", "SIGUIENTE", "PRÓXIMO", "LANJUT"],
	"LET'S RIDE!": ["चलो चलें!", "¡A RODAR!", "BORA!", "AYO JALAN!"],
	"NEO GRID, 2099": ["नियो ग्रिड, 2099", "NEO GRID, 2099", "NEO GRID, 2099", "NEO GRID, 2099"],
	"RIDE THE LIGHT": ["रोशनी पर सवारी", "CABALGA LA LUZ", "PILOTE A LUZ", "TUNGGANGI CAHAYA"],
	"LOOP TO CLAIM": ["घेरो और जीतो", "RODEA PARA GANAR", "CERQUE PARA GANHAR", "LINGKARI UNTUK MENGUASAI"],
	"CUT THEIR TRAIL": ["उनकी लाइन काटो", "CORTA SU RASTRO", "CORTE O RASTRO", "POTONG JEJAKNYA"],
	"RULE THE GRID": ["ग्रिड पर राज करो", "DOMINA LA RED", "DOMINE A GRADE", "KUASAI GRID"],
	"The floating lands of Neo Grid run on pure light. Whoever holds the most land rules the city.":
		["नियो ग्रिड की तैरती ज़मीनें रोशनी से चलती हैं। जिसके पास सबसे ज़्यादा ज़मीन, वही शहर का राजा।", "Las tierras flotantes de Neo Grid funcionan con luz pura. Quien tenga más tierra manda en la ciudad.", "As terras flutuantes de Neo Grid funcionam com luz pura. Quem tiver mais terra manda na cidade.", "Lahan terapung Neo Grid berjalan dengan cahaya murni. Siapa yang punya lahan terbanyak menguasai kota."],
	"Riders race light bikes across the lands. Every bike leaves a wall of light behind it.":
		["राइडर्स लाइट बाइक दौड़ाते हैं। हर बाइक अपने पीछे रोशनी की दीवार छोड़ती है।", "Los pilotos corren en motos de luz. Cada moto deja un muro de luz detrás.", "Pilotos correm em motos de luz. Cada moto deixa um muro de luz para trás.", "Para pembalap memacu motor cahaya. Setiap motor meninggalkan dinding cahaya."],
	"Ride out of your land and loop back home. Everything inside your loop becomes yours.":
		["अपनी ज़मीन से बाहर निकलो और घूमकर वापस आओ। घेरे के अंदर सब कुछ तुम्हारा।", "Sal de tu tierra y vuelve a casa. Todo lo que rodees será tuyo.", "Saia da sua terra e volte para casa. Tudo dentro da volta vira seu.", "Keluar dari lahanmu lalu kembali. Semua di dalam lingkaran jadi milikmu."],
	"Hit a rival's light wall before they get home and they're out. Keep yours safe!":
		["विरोधी के घर पहुँचने से पहले उसकी लाइन काटो, वो आउट। अपनी लाइन बचाकर रखो!", "Toca el muro de un rival antes de que llegue a casa y queda fuera. ¡Protege el tuyo!", "Acerte o muro de um rival antes que ele volte e ele está fora. Proteja o seu!", "Tabrak dinding lawan sebelum dia pulang, dia kalah. Jaga dindingmu!"],
	"Hold the most land when the clock hits zero. Win to unlock new lands and riders.":
		["समय खत्म होने पर सबसे ज़्यादा ज़मीन रखो। जीतकर नई ज़मीनें और राइडर्स पाओ।", "Ten más tierra cuando el reloj llegue a cero. Gana para desbloquear tierras y pilotos.", "Tenha mais terra quando o tempo acabar. Vença para liberar terras e pilotos.", "Kuasai lahan terbanyak saat waktu habis. Menang untuk membuka lahan dan pembalap baru."],
	# Riders
	"All-rounder": ["हर काम में माहिर", "Equilibrado", "Equilibrado", "Serba bisa"],
	"Starts every life with a longer shield": ["हर बार लंबी ढाल के साथ शुरू", "Empieza cada vida con más escudo", "Começa cada vida com escudo maior", "Mulai dengan perisai lebih lama"],
	"10% faster": ["10% तेज़", "10% más rápido", "10% mais rápido", "10% lebih cepat"],
	"Starts with more land": ["ज़्यादा ज़मीन से शुरू", "Empieza con más tierra", "Começa com mais terra", "Mulai dengan lahan lebih luas"],
	"Power-ups last 50% longer": ["पावर-अप 50% ज़्यादा चलते हैं", "Potenciadores duran 50% más", "Power-ups duram 50% mais", "Power-up 50% lebih lama"],
	"Energy bursts are bigger": ["ऊर्जा धमाके बड़े", "Estallidos más grandes", "Explosões maiores", "Ledakan energi lebih besar"],
	"Grinding along trails is extra fast": ["लाइन के साथ चलने पर ज़्यादा तेज़", "Rozar rastros es más rápido", "Raspar rastros é mais rápido", "Menyusur jejak ekstra cepat"],
	"Earns 50% more coins": ["50% ज़्यादा सिक्के", "Gana 50% más monedas", "Ganha 50% mais moedas", "Dapat koin 50% lebih banyak"],
}

func _ready() -> void:
	for k in range(1, LANGUAGES.size()):
		var t := Translation.new()
		t.locale = LANGUAGES[k][0]
		for en in TEXT:
			t.add_message(en, TEXT[en][k - 1])
		TranslationServer.add_translation(t)
	apply(Save.language)


## Switches to [param code], or to the phone's language when it's "".
func apply(code: String) -> void:
	if code == "":
		code = OS.get_locale_language()
	if not LANGUAGES.any(func(l): return l[0] == code):
		code = "en"
	TranslationServer.set_locale(code)


func current() -> String:
	return TranslationServer.get_locale().substr(0, 2)


## Moves to the next language in the list and remembers it.
func cycle() -> void:
	var k := 0
	for i in LANGUAGES.size():
		if LANGUAGES[i][0] == current():
			k = i
	Save.language = LANGUAGES[(k + 1) % LANGUAGES.size()][0]
	Save.save()
	apply(Save.language)
