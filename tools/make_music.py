# Composes the synthwave loops in res/raw (music_race.ogg, music_menu.ogg).
# Run: python3 make_music.py, then encode each WAV with: ffmpeg -i race.wav -c:a libvorbis -q:a 3 music_race.ogg
import numpy as np, wave, sys
SR=32000
rng=np.random.default_rng(7)
def mf(m): return 440*2**((m-69)/12)
def lp(x,c):
    # one-pole low-pass, c in 0..1 (higher = brighter), vectorised by chunks via lfilter-like loop
    y=np.empty_like(x); a=0.0
    for i in range(len(x)):
        a+= c*(x[i]-a); y[i]=a
    return y
def saw(f,n,ph=0.0):
    t=np.arange(n)/SR
    return 2*((t*f+ph)%1)-1
def env(n,a,d,s,r_start=None):
    e=np.ones(n)
    na=int(a*SR); nd=int(d*SR)
    if na>0: e[:na]=np.linspace(0,1,na)
    if nd>0: e[na:na+nd]=np.linspace(1,s,min(nd,max(0,n-na)))[:max(0,min(nd,n-na))]
    e[na+nd:]=s
    rn=int(0.03*SR); e[-rn:]*=np.linspace(1,0,rn)
    return e
def track(bpm,chords,bars,energy,seed):
    beat=60/bpm; bar=4*beat
    N=int(bars*bar*SR)
    out=np.zeros(N); bass=np.zeros(N); pad=np.zeros(N); lead=np.zeros(N); drums=np.zeros(N)
    side=np.ones(N)
    # drums
    kn=int(0.35*SR); t=np.arange(kn)/SR
    kick=np.sin(2*np.pi*np.cumsum(45+75*np.exp(-t*30))/SR)*np.exp(-t*9)
    sn=int(0.2*SR); t2=np.arange(sn)/SR
    snare=(rng.uniform(-1,1,sn)*0.7+0.4*np.sin(2*np.pi*190*t2))*np.exp(-t2*18)
    hn=int(0.05*SR); t3=np.arange(hn)/SR
    hn_noise=rng.uniform(-1,1,hn); hat=(hn_noise-lp(hn_noise,0.3))*np.exp(-t3*60)
    for b in range(int(bars*4)):
        s=int(b*beat*SR)
        drums[s:s+kn]+=kick[:N-s] if s+kn>N else kick
        # sidechain
        sl=min(int(0.25*SR),N-s); side[s:s+sl]=np.minimum(side[s:s+sl],0.35+0.65*np.linspace(0,1,sl)**0.7)
        if b%2==1:
            e=min(sn,N-s); drums[s:s+e]+=0.6*snare[:e]
        for h in range(2 if energy<1 else 4):
            hs=int((b*beat+h*beat/(2 if energy<1 else 4))*SR)+ (int(beat/4*SR) if energy<1 else 0)
            e=min(hn,N-hs)
            if e>0: drums[hs:hs+e]+=0.12*hat[:e]
    for bi in range(bars):
        root,tones=chords[bi%len(chords)]
        s0=int(bi*bar*SR); n_bar=int(bar*SR)
        # pad: detuned saws, slow attack
        for m in tones:
            for det in (-0.08,0.0,0.07):
                pad[s0:s0+n_bar]+=0.05*saw(mf(m+12)*2**(det/12),n_bar,rng.random())*env(n_bar,0.4,0.2,0.8)
        # bass: 8th or 16th notes
        steps=16 if energy>=1 else 8
        ln=int(bar/steps*SR)
        for k in range(steps):
            s=s0+k*ln
            m=root-12 + (12 if (k%4==2 and energy>=1) else 0)
            bass[s:s+ln]+=0.5*saw(mf(m),ln)*env(ln,0.003,0.08,0.4)
        # lead arpeggio (16ths) in octave 5
        arp=[tones[0],tones[1],tones[2],tones[1]+12,tones[2],tones[1],tones[0]+12,tones[2]]
        ln=int(bar/16*SR)
        if energy>=0.5:
            for k in range(16):
                s=s0+k*ln
                m=arp[k%8]+24
                sq=np.sign(np.sin(2*np.pi*mf(m)*np.arange(ln)/SR))*0.5+saw(mf(m),ln)*0.5
                lead[s:s+ln]+=0.12*sq*env(ln,0.002,0.06,0.3)
    bass=lp(bass,0.08)*side
    pad=lp(pad,0.05)*(0.6+0.4*side)
    lead=lp(lead,0.18)
    # echo on lead
    d=int(beat*0.75*SR); echo=np.zeros(N); echo[d:]=lead[:-d]*0.35
    lead=(lead+echo)*side**0.5
    mix=drums*0.9+bass+pad+lead*energy
    mix=lp(np.tanh(mix*1.4),0.75)
    mix/=np.max(np.abs(mix))*1.05
    # tiny fade to make the loop seamless-ish
    return mix
def save(name,x):
    with wave.open(name,'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x*32767).astype(np.int16).tobytes())
Am=(57,[57,60,64]); F=(53,[53,57,60]); C=(48,[48,52,55]); G=(55,[55,59,62]); Em=(52,[52,55,59]); Dm=(50,[50,53,57])
race=track(126,[Am,F,C,G,Am,F,Dm,Em],8,1.0,1)
menu=track(100,[Am,F,C,Em],4,0.6,2)
save('race.wav',race); save('menu.wav',menu)
print(len(race)/SR, len(menu)/SR)
