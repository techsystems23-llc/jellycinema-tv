#!/usr/bin/env python3
"""Sets up a throwaway Jellyfin server for the Jelly Cinema privacy check.

  seed.py <server url> <media dir on this machine> [<the same dir as the server sees it>]

Makes tiny sample movies (1-second videos with .nfo ratings, genres and holiday tags, plain posters and
backdrops) in three libraries, an admin, and two restricted accounts:
  kid     - every library, but nothing above PG
  kidlib  - no rating limit, but only the "Movies" library
plus some watch history (the admin watches R-rated movies, so they rank in the Top 10). Writes the
accounts to <media dir>/accounts.json for the test (JC_PRIVACY_SERVER / JC_PRIVACY_ACCOUNTS).
Only uses the Python standard library and ffmpeg.
"""
import json, os, subprocess, sys, time, urllib.error, urllib.parse, urllib.request

BASE = sys.argv[1].rstrip('/')
MEDIA = os.path.abspath(sys.argv[2])
SERVER_MEDIA = sys.argv[3] if len(sys.argv) > 3 else MEDIA
AUTH = 'MediaBrowser Client="jc-privacy-seed", Device="ci", DeviceId="jc-privacy-seed", Version="1"'
token = None

# (library, title, year, rating, genres, tags, score)
MOVIES = [
    ("Movies", "Pumpkin Patch Pals", 2019, "G", ["Family", "Animation"], ["halloween"], 7.5),
    ("Movies", "Midnight Manor", 2018, "R", ["Horror"], ["halloween"], 6.9),
    ("Movies", "Snowy Hearts", 2020, "PG", ["Family", "Romance"], ["christmas"], 7.1),
    ("Movies", "Santa Heist", 2021, "R", ["Comedy", "Crime"], ["christmas"], 6.6),
    ("Movies", "Bunny Trail", 2019, "G", ["Family"], ["easter"], 7.0),
    ("Movies", "Dark Easter", 2020, "R", ["Horror"], ["easter"], 5.9),
    ("Movies", "Rocket Ranch", 2017, "PG", ["Family", "Adventure"], [], 7.2),
    ("Movies", "Tiny Robots", 2022, "G", ["Family", "Animation"], [], 8.0),
    ("Movies", "Garden Gang", 2016, "G", ["Family", "Animation"], [], 7.3),
    ("Movies", "Puppy Parade", 2018, "G", ["Family", "Adventure"], [], 6.8),
    ("Movies", "Ocean Song", 2021, "PG", ["Family", "Animation"], [], 7.6),
    ("Movies", "Little Lighthouse", 2015, "G", ["Family", "Adventure"], [], 7.4),
    ("Movies", "Night Run", 2015, "PG-13", ["Action", "Thriller"], [], 6.7),
    ("Movies", "Deep Space", 2014, "PG-13", ["Science Fiction", "Adventure"], [], 7.0),
    ("Movies", "Steel Fury", 2016, "R", ["Action"], [], 6.5),
    ("Movies", "Laser Lords", 2013, "R", ["Science Fiction", "Action"], [], 6.4),
    ("Movies", "Iron Alley", 2019, "R", ["Action", "Crime"], [], 6.6),
    ("Movies", "Blast Zone", 2020, "R", ["Action"], [], 6.1),
    ("Movies", "Rogue Signal", 2018, "R", ["Action", "Thriller"], [], 6.3),
    ("Movies", "Last Convoy", 2017, "R", ["Action"], [], 6.2),
] + [
    ("Holiday TV", f"Christmas in Maple Falls {i}", 2010 + i, "TV-G", ["Romance", "Family"], ["christmas"], 6.5) for i in range(1, 11)
] + [
    ("Holiday TV", "A Very Grown-up Christmas", 2019, "TV-MA", ["Comedy"], ["christmas"], 6.0),
    ("Holiday TV", "Christmas After Dark", 2021, "TV-MA", ["Thriller"], ["christmas"], 5.8),
    ("Grown-ups", "Midnight Shift", 2019, "R", ["Thriller", "Crime"], [], 6.9),
    ("Grown-ups", "Cold Harbor", 2020, "R", ["Drama", "Crime"], [], 7.1),
    ("Grown-ups", "Whiskey Road", 2018, "R", ["Drama"], [], 6.8),
    ("Grown-ups", "Blood Moon Christmas", 2017, "R", ["Horror"], ["christmas", "halloween"], 5.5),
]


def call(method, path, body=None, query=None, raw=False, timeout=60):
    url = BASE + path + ('?' + urllib.parse.urlencode(query, doseq=True) if query else '')
    data = json.dumps(body).encode() if body is not None else None
    h = {'Authorization': AUTH + (f', Token="{token}"' if token else ''), 'Content-Type': 'application/json'}
    for attempt in range(30):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=data, method=method, headers=h), timeout=timeout) as r:
                text = r.read().decode()
                return text if raw else (json.loads(text) if text.strip() else None)
        except urllib.error.HTTPError as e:
            if e.code in (502, 503) and attempt < 29: time.sleep(2); continue
            raise RuntimeError(f'{method} {path} -> {e.code} {e.read()[:300]!r}')
        except (urllib.error.URLError, ConnectionError, TimeoutError):
            if attempt < 29: time.sleep(2); continue
            raise


def ffmpeg(*args):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', *args], check=True)


def make_media():
    colors = ['red', 'blue', 'green', 'purple', 'orange', 'teal', 'navy', 'maroon']
    for i, (lib, title, year, rating, genres, tags, score) in enumerate(MOVIES):
        d = os.path.join(MEDIA, lib, f'{title} ({year})')
        os.makedirs(d, exist_ok=True)
        base = os.path.join(d, f'{title} ({year})')
        if not os.path.exists(base + '.mp4'):
            ffmpeg('-f', 'lavfi', '-i', 'color=c=black:s=320x180:d=1', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', base + '.mp4')
        c = colors[i % len(colors)]
        if not os.path.exists(os.path.join(d, 'poster.jpg')):
            ffmpeg('-f', 'lavfi', '-i', f'color=c={c}:s=200x300', '-frames:v', '1', os.path.join(d, 'poster.jpg'))
            ffmpeg('-f', 'lavfi', '-i', f'color=c={c}:s=640x360', '-frames:v', '1', os.path.join(d, 'fanart.jpg'))
        nfo = ['<?xml version="1.0" encoding="UTF-8"?>', '<movie>', f'  <title>{title}</title>', f'  <year>{year}</year>',
               f'  <mpaa>{rating}</mpaa>', f'  <rating>{score}</rating>', f'  <plot>A sample movie for the Jelly Cinema privacy check.</plot>']
        nfo += [f'  <genre>{g}</genre>' for g in genres] + [f'  <tag>{t}</tag>' for t in tags] + ['</movie>']
        open(base + '.nfo', 'w').write('\n'.join(nfo))


def wizard(admin_pw):
    info = call('GET', '/System/Info/Public')
    if info.get('StartupWizardCompleted'): return
    call('POST', '/Startup/Configuration', {'UICulture': 'en-US', 'MetadataCountryCode': 'US', 'PreferredMetadataLanguage': 'en'})
    call('GET', '/Startup/User')
    call('POST', '/Startup/User', {'Name': 'admin', 'Password': admin_pw})
    call('POST', '/Startup/RemoteAccess', {'EnableRemoteAccess': True, 'EnableAutomaticPortMapping': False})
    call('POST', '/Startup/Complete')


def login(name, pw):
    r = call('POST', '/Users/AuthenticateByName', {'Username': name, 'Pw': pw})
    return r['AccessToken'], r['User']['Id']


def main():
    global token
    os.makedirs(MEDIA, exist_ok=True)
    make_media()
    accounts = {'admin': 'admin-pw', 'kid': 'kid-pw', 'kidlib': 'kidlib-pw'}
    wizard(accounts['admin'])
    token, admin_id = login('admin', accounts['admin'])

    # libraries without internet look-ups (everything comes from the .nfo files)
    existing = {f['Name'] for f in call('GET', '/Library/VirtualFolders')}
    for lib in ['Movies', 'Holiday TV', 'Grown-ups']:
        if lib in existing: continue
        opts = {'LibraryOptions': {'EnableRealtimeMonitor': False, 'EnableInternetProviders': False,
                'PathInfos': [{'Path': os.path.join(SERVER_MEDIA, lib)}],
                'TypeOptions': [{'Type': 'Movie', 'MetadataFetchers': [], 'ImageFetchers': []}]}}
        call('POST', '/Library/VirtualFolders', opts, {'name': lib, 'collectionType': 'movies', 'refreshLibrary': 'false'})
    call('POST', '/Library/Refresh')
    for _ in range(120):
        n = call('GET', '/Items', query={'Recursive': 'true', 'IncludeItemTypes': 'Movie', 'Limit': 0})['TotalRecordCount']
        if n >= len(MOVIES): break
        time.sleep(2)
    else:
        raise SystemExit(f'library scan found only {n} of {len(MOVIES)} movies')
    time.sleep(5)
    items = call('GET', '/Items', query={'Recursive': 'true', 'IncludeItemTypes': 'Movie', 'Fields': 'ParentId,Path,OfficialRating'})['Items']
    by_name = {i['Name']: i for i in items}
    folders = {f['Name']: f['ItemId'] for f in call('GET', '/Library/VirtualFolders')}

    ratings = {r['Name']: r['Value'] for r in call('GET', '/Localization/ParentalRatings') if 'Value' in r}
    users = {u['Name']: u for u in call('GET', '/Users')}
    for name in ['kid', 'kidlib']:
        if name not in users:
            users[name] = call('POST', '/Users/New', {'Name': name, 'Password': accounts[name]})
        uid = users[name]['Id']
        policy = call('GET', f'/Users/{uid}')['Policy']
        if name == 'kid':
            policy['MaxParentalRating'] = ratings.get('PG')
        else:
            policy['EnableAllFolders'] = False
            policy['EnabledFolders'] = [folders['Movies']]
        call('POST', f'/Users/{uid}/Policy', policy)

    # collections: one grown-up, one for everyone
    have = {c['Name'] for c in call('GET', '/Items', query={'Recursive': 'true', 'IncludeItemTypes': 'BoxSet'})['Items']}
    for cname, titles in [('Horror Nights', ['Midnight Manor', 'Dark Easter', 'Blood Moon Christmas']),
                          ('Family Fun', ['Tiny Robots', 'Garden Gang', 'Puppy Parade', 'Little Lighthouse'])]:
        if cname not in have:
            call('POST', '/Collections', query={'name': cname, 'ids': ','.join(by_name[t]['Id'] for t in titles)})

    # watch history: the admin's R-rated movies rank in the Top 10; the kids watched family titles
    plays = {'admin': ['Steel Fury', 'Laser Lords', 'Midnight Manor', 'Iron Alley', 'Midnight Shift', 'Night Run', 'A Very Grown-up Christmas'],
             'kid': ['Tiny Robots', 'Rocket Ranch', 'Snowy Hearts'],
             'kidlib': ['Puppy Parade', 'Steel Fury']}
    for name, titles in plays.items():
        token, uid = login(name, accounts[name])
        for t in titles:
            call('POST', f'/UserPlayedItems/{by_name[t]["Id"]}', query={'userId': uid})
        token, _ = login('admin', accounts['admin'])

    out = {'server': BASE, 'accounts': accounts, 'restricted': ['kid', 'kidlib']}
    json.dump(out, open(os.path.join(MEDIA, 'accounts.json'), 'w'), indent=2)
    print(f'seeded {len(items)} movies in {len(folders)} libraries; accounts in {MEDIA}/accounts.json')


if __name__ == '__main__':
    main()
