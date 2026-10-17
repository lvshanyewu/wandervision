# Map data and routes

Snapshot date: 2026-10-17. These are demonstration map resources, not live traffic.

- `chongqing.geojson`: derived from © OpenStreetMap contributors, fetched via https://overpass.private.coffee/api/interpreter (fallbacks: overpass-api.de and overpass.kumi.systems). Bounding box and reproducible query in `tools/prepare_web_maps.py`. Contains selected road ways, campus footways, nearby buildings, university boundaries, parks, river centerlines and outer river polygons; relation inner rings are omitted for lightweight rendering. WGS84 coordinates rounded to six decimals. Map labels are manually placed public place names. This derived database is provided under the Open Database License (ODbL) 1.0: https://opendatacommons.org/licenses/odbl/1-0/ . Original data and editing: https://www.openstreetmap.org .
- `routes.json`: three driving route geometry snapshots from https://router.project-osrm.org/route/v1/driving/ using OpenStreetMap data, under ODbL. Waypoints and refresh procedure in `tools/prepare_web_maps.py`. Runtime never calls OSRM. Playback reverses at endpoints rather than teleporting.
- Online optional tiles use https://tile.openstreetmap.org/{z}/{x}/{y}.png subject to https://operations.osmfoundation.org/policies/tiles/ . Raster tiles are **not** bundled, prefetched or downloaded by the preparation tool.
- `asset-checksums.json`: SHA-256 of the exact committed map data and Leaflet runtime assets. Regenerate intentionally after refreshing resources.

Map attribution remains visible even with the default offline vectors. Leaflet 1.9.4 is distributed in `../vendor/leaflet/` with its BSD-2-Clause license. Leaflet JS and CSS SHA-256 match https://leafletjs.com/download.html . Project code is licensed separately under the repository AGPL-3.0; the OSM derived database retains ODbL.

V2 focus: Chongqing Jiaotong University Nan'an campus, WGS84 center (29.4903657,106.567229), from OSM university way 412677871. Campus address cross-check: https://zs.cqjtu.edu.cn/contact . Context bbox 29.465,106.535,29.535,106.615; near-campus buildings bbox 29.481,106.557,29.504,106.579. Three OSRM snapshots cover Xuefu Avenue north/south and Huilong Road.
