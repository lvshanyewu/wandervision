"""Refresh committed, bounded OSM vector data and three OSRM route snapshots.

No raster tiles are downloaded. Run only when intentionally refreshing data.
"""
import base64
import hashlib
import json
from pathlib import Path
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1] / 'web' / 'static'
HEADERS = {'User-Agent': 'wandervision-course-demo/1.0 (+https://github.com/lvshanyewu/wandervision)'}


def fetch(url, data=None):
    req = urllib.request.Request(url, data=data, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=100) as response:
        return response.read()


def write_json(name, data):
    (ROOT / 'data' / name).write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')


def main():
    vendor = ROOT / 'vendor' / 'leaflet'
    vendor.mkdir(parents=True, exist_ok=True)
    for name, expected in [('leaflet.js', '20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo='),
                           ('leaflet.css', 'p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=')]:
        content = fetch('https://unpkg.com/leaflet@1.9.4/dist/' + name)
        assert base64.b64encode(hashlib.sha256(content).digest()).decode() == expected
        (vendor / name).write_bytes(content)
    (vendor / 'LICENSE').write_bytes(fetch('https://raw.githubusercontent.com/Leaflet/Leaflet/v1.9.4/LICENSE'))
    query = '''[out:json][timeout:80];(
      way[highway~"^(motorway|trunk|primary|secondary|tertiary|residential|motorway_link|trunk_link|primary_link|secondary_link)$"](29.49,106.43,29.66,106.66);
      way[waterway=river](29.49,106.43,29.66,106.66);
      way[natural=water][water=river](29.49,106.43,29.66,106.66);
      relation[natural=water][water=river](29.49,106.43,29.66,106.66);
    );out geom;'''
    raw = None
    for endpoint in ['https://overpass-api.de/api/interpreter', 'https://overpass.kumi.systems/api/interpreter']:
        try:
            raw = json.loads(fetch(endpoint + '?' + urllib.parse.urlencode({'data': query})))
            break
        except Exception as exc:
            print('Overpass retry:', type(exc).__name__)
    if raw is None:
        raise RuntimeError('Could not refresh OSM data; existing snapshots are untouched.')
    features = []
    def coordinates(geom):
        return [[round(p['lon'], 6), round(p['lat'], 6)] for p in geom]
    for elem in raw['elements']:
        tags = elem.get('tags', {})
        props = {k: tags[k] for k in ['name', 'highway', 'waterway', 'water'] if k in tags}
        if elem['type'] == 'way' and elem.get('geometry'):
            coords = coordinates(elem['geometry'])
            polygon = tags.get('natural') == 'water' and coords[0] == coords[-1]
            features.append({'type': 'Feature', 'properties': props,
                             'geometry': {'type': 'Polygon' if polygon else 'LineString',
                                          'coordinates': [coords] if polygon else coords}})
        elif elem['type'] == 'relation':
            parts = [coordinates(m['geometry']) for m in elem.get('members', [])
                     if m.get('role') == 'outer' and m.get('geometry')]
            while parts:
                ring = parts.pop()
                while ring[0] != ring[-1]:
                    match = next((i for i, p in enumerate(parts) if ring[-1] in (p[0], p[-1])), None)
                    if match is None:
                        break
                    part = parts.pop(match)
                    ring += (part if part[0] == ring[-1] else part[::-1])[1:]
                if ring[0] == ring[-1] and len(ring) > 3:
                    features.append({'type': 'Feature', 'properties': {'water': 'river', **props},
                                     'geometry': {'type': 'Polygon', 'coordinates': [ring]}})
    write_json('chongqing.geojson', {'type': 'FeatureCollection', 'features': features})
    print('OSM features:', len(features))
    waypoints = [
        [[106.5493,29.5628],[106.5629,29.5586],[106.5750,29.5603],[106.5820,29.5630],[106.5788,29.5686]],
        [[106.5518,29.5821],[106.5648,29.5857],[106.5745,29.5884],[106.5899,29.5816],[106.5944,29.5780]],
        [[106.5611,29.5413],[106.5760,29.5464],[106.5857,29.5520],[106.5930,29.5576],[106.5990,29.5653]],
    ]
    routes = []
    for i, points in enumerate(waypoints):
        locations = ';'.join(','.join(map(str,p)) for p in points)
        result = json.loads(fetch('https://router.project-osrm.org/route/v1/driving/' + locations + '?overview=full&geometries=geojson'))
        assert result['code'] == 'Ok'
        route = result['routes'][0]
        routes.append({'id': f'route-{i+1}', 'name': ['渝中半岛线','江北滨江线','南滨路巡检线'][i],
                       'coordinates': route['geometry']['coordinates'], 'distance_m': route['distance'],
                       'source': 'OSRM / OpenStreetMap', 'playback': 'ping-pong'})
    write_json('routes.json', routes)
    print('OSRM route vertices:', [len(r['coordinates']) for r in routes])
    files = ['data/chongqing.geojson', 'data/routes.json', 'vendor/leaflet/leaflet.js',
             'vendor/leaflet/leaflet.css', 'vendor/leaflet/LICENSE']
    write_json('asset-checksums.json', {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in files})


if __name__ == '__main__':
    main()
