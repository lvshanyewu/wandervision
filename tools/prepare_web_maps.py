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
      way[highway~"^(motorway|trunk|primary|secondary|tertiary|residential|service|unclassified|living_street|footway|path|pedestrian|motorway_link|trunk_link|primary_link|secondary_link)$"](29.465,106.535,29.535,106.615);
      way[waterway=river](29.465,106.535,29.535,106.615);
      way[natural=water][water=river](29.465,106.535,29.535,106.615);
      relation[natural=water][water=river](29.465,106.535,29.535,106.615);
      way[building](29.481,106.557,29.504,106.579);
      way[amenity=university](29.481,106.557,29.504,106.579);
      way[leisure=park](29.465,106.535,29.535,106.615);
    );out geom;'''
    raw = None
    for endpoint in ['https://overpass.private.coffee/api/interpreter','https://overpass-api.de/api/interpreter', 'https://overpass.kumi.systems/api/interpreter']:
        try:
            raw = json.loads(fetch(endpoint + '?' + urllib.parse.urlencode({'data': query})))
            break
        except Exception as exc:
            print('Overpass retry:', type(exc).__name__, getattr(exc,'code',''))
    if raw is None:
        raise RuntimeError('Could not refresh OSM data; existing snapshots are untouched.')
    features = []
    def coordinates(geom):
        return [[round(p['lon'], 6), round(p['lat'], 6)] for p in geom]
    for elem in raw['elements']:
        tags = elem.get('tags', {})
        props = {k: tags[k] for k in ['name', 'highway', 'waterway', 'water','building','amenity','leisure'] if k in tags}
        if elem['type'] == 'way' and elem.get('geometry'):
            coords = coordinates(elem['geometry'])
            polygon = any(k in tags for k in ['natural','building','amenity','leisure']) and coords[0] == coords[-1]
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
        [[106.5691,29.4914],[106.5688,29.4960],[106.5702,29.5010],[106.5708,29.5041]],
        [[106.5691,29.4914],[106.5688,29.4860],[106.5680,29.4810],[106.5659,29.4777]],
        [[106.5690,29.4850],[106.5740,29.4840],[106.5790,29.4820],[106.5820,29.4865]],
    ]
    routes = []
    for i, points in enumerate(waypoints):
        locations = ';'.join(','.join(map(str,p)) for p in points)
        result = json.loads(fetch('https://router.project-osrm.org/route/v1/driving/' + locations + '?overview=full&geometries=geojson'))
        assert result['code'] == 'Ok'
        route = result['routes'][0]
        routes.append({'id': f'route-{i+1}', 'name': ['学府大道北线','交大南侧巡检线','汇龙路巡检线'][i],
                       'coordinates': route['geometry']['coordinates'], 'distance_m': route['distance'],
                       'source': 'OSRM / OpenStreetMap', 'playback': 'ping-pong'})
    write_json('routes.json', routes)
    print('OSRM route vertices:', [len(r['coordinates']) for r in routes])
    files = ['data/chongqing.geojson', 'data/routes.json', 'vendor/leaflet/leaflet.js',
             'vendor/leaflet/leaflet.css', 'vendor/leaflet/LICENSE']
    write_json('asset-checksums.json', {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in files})


if __name__ == '__main__':
    main()
