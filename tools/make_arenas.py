# Builds the country arena masks used by game/Arenas.kt.
# Input: Natural Earth ne_10m_admin_0_countries_ind.geojson (public domain) in the working directory.
# Output: masks.json with (code, name, size, playable cells, base64 bits) per level.
import json, math, base64, sys
from collections import deque
d=json.load(open('ne_10m_admin_0_countries_ind.geojson'))
by={}
for f in d['features']:
    p=f['properties']; by[p['ADM0_A3']]=f
# (code, display, original map size)
maps=[('IND','India',64),('EGY','Egypt',70),('FRA','France',76),('AUS','Australia',80),('BRA','Brazil',84),
      ('ZAF','South Africa',88),('SAU','Saudi Arabia',90),('DEU','Germany',94),('CHN','China',98),('USA','United States',104)]
def polys(f):
    g=f['geometry']
    return [g['coordinates']] if g['type']=='Polygon' else g['coordinates']
def area(r):
    return abs(sum(r[i][0]*r[i-1][1]-r[i-1][0]*r[i][1] for i in range(len(r))))/2
out=[]
RATIO={'IND':0.25,'EGY':0.61,'FRA':0.48,'AUS':0.4,'BRA':0.4,'ZAF':0.43,'SAU':0.38,'DEU':0.44,'CHN':0.27,'USA':0.28}
for code,name,size in maps:
    ps=polys(by[code])
    # biggest landmass only (mainland)
    main=max(ps,key=lambda p:area(p[0]))
    lat0=sum(pt[1] for pt in main[0])/len(main[0])
    k=math.cos(math.radians(lat0))
    rings=[[(x*k,-y) for x,y in r] for r in main]
    xs=[x for x,_ in rings[0]]; ys=[y for _,y in rings[0]]
    w=max(xs)-min(xs); h=max(ys)-min(ys)
    N=size; m=2
    N=int(round(size*math.sqrt(0.55/RATIO.get(code,0.5))))
    sc=(N-2*m)/max(w,h)
    ox=m+((N-2*m)-w*sc)/2-min(xs)*sc; oy=m+((N-2*m)-h*sc)/2-min(ys)*sc
    R=[[(x*sc+ox,y*sc+oy) for x,y in r] for r in rings]
    grid=[[False]*N for _ in range(N)]
    for j in range(N):
        cy=j+0.5; xsx=[]
        for r in R:
            for i in range(len(r)):
                (x1,y1),(x2,y2)=r[i-1],r[i]
                if (y1<=cy)!=(y2<=cy):
                    xsx.append(x1+(cy-y1)*(x2-x1)/(y2-y1))
        xsx.sort()
        for a,b in zip(xsx[::2],xsx[1::2]):
            for i in range(N):
                if a<=i+0.5<b: grid[j][i]=True
    # opening: erode then dilate (3x3 cross) to drop thin spurs
    def er(g):
        return [[g[j][i] and all(0<=j+dy<N and 0<=i+dx<N and g[j+dy][i+dx] for dx,dy in((1,0),(-1,0),(0,1),(0,-1))) for i in range(N)] for j in range(N)]
    def di(g):
        return [[g[j][i] or any(0<=j+dy<N and 0<=i+dx<N and g[j+dy][i+dx] for dx,dy in((1,0),(-1,0),(0,1),(0,-1))) for i in range(N)] for j in range(N)]
    g=di(di(er(er(grid))))
    g=[[g[j][i] and grid[j][i] for i in range(N)] for j in range(N)]
    # largest 4-connected component
    seen=[[False]*N for _ in range(N)]; best=[]
    for j in range(N):
        for i in range(N):
            if g[j][i] and not seen[j][i]:
                comp=[];q=deque([(i,j)]);seen[j][i]=True
                while q:
                    x,y=q.popleft();comp.append((x,y))
                    for dx,dy in((1,0),(-1,0),(0,1),(0,-1)):
                        a,b=x+dx,y+dy
                        if 0<=a<N and 0<=b<N and g[b][a] and not seen[b][a]:
                            seen[b][a]=True;q.append((a,b))
                if len(comp)>len(best): best=comp
    final=[[False]*N for _ in range(N)]
    for x,y in best: final[y][x]=True
    bits=bytearray((N*N+7)//8)
    for y in range(N):
        for x in range(N):
            if final[y][x]: bits[(y*N+x)//8]|=1<<((y*N+x)%8)
    out.append((code,name,N,len(best),base64.b64encode(bytes(bits)).decode()))
    print(code,N,len(best),round(len(best)/N/N,2),file=sys.stderr)
    if code in('IND',):
        for y in range(0,N,2): print(''.join('#' if final[y][x] else '.' for x in range(0,N,1)),file=sys.stderr)
json.dump(out,open('masks.json','w'))
