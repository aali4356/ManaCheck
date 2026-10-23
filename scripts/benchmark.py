#!/usr/bin/env python3
"""Measure the local rules workflow and inspect its actual PostgreSQL ranking query."""
import argparse
import json
import os
from pathlib import Path
import platform
import re
import statistics
import subprocess
import time
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='http://localhost:8080')
    parser.add_argument('--deck-id')
    parser.add_argument('--iterations', type=int, default=20)
    parser.add_argument('--max-p95-ms', type=float, help='Optional local warm-response budget; retains evidence before returning failure.')
    args = parser.parse_args()
    parsed = urllib.parse.urlsplit(args.base)
    if parsed.hostname not in ('localhost', '127.0.0.1') or parsed.scheme != 'http':
        parser.error('This benchmark is limited to the local application.')
    if not 1 <= args.iterations <= 100:
        parser.error('Choose 1–100 iterations.')

    def api(path, post=False):
        request = urllib.request.Request(args.base + '/api' + path, data=b'' if post else None,
                                         method='POST' if post else 'GET', headers={'Accept': 'application/json'})
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.load(response)

    decks = api('/decks')
    if not decks:
        raise SystemExit('Create a deck before measuring.')
    chosen = next((d for d in decks if d['id'] == args.deck_id), None) if args.deck_id else next((d for d in decks if d['commanderName'] == 'Meren of Clan Nel Toth'), decks[0])
    if chosen is None:
        raise SystemExit('Deck ID not found.')
    deck = api('/decks/' + chosen['id'])
    catalog = api('/catalog/imports')
    times = []
    for _ in range(args.iterations + 1):
        start = time.perf_counter()
        run = api('/decks/' + chosen['id'] + '/recommendations', post=True)
        times.append((time.perf_counter() - start) * 1000)
    env = dict(os.environ, PGPASSWORD='local-demo-only')
    command = ['psql', '-X', '-h', '127.0.0.1', '-p', '55432', '-U', 'manacheck', '-d', 'manacheck', '-A', '-t', '-v', 'ON_ERROR_STOP=1']

    def sql(statement, parameters):
        arguments = command + [item for key, value in parameters.items() for item in ('-v', key + '=' + str(value))]
        return subprocess.run(arguments, input=statement, capture_output=True, text=True, env=env, check=True).stdout.strip()

    revision = sql("SELECT id FROM deck_revision WHERE deck_id=:'deck_id' AND revision_number=:'revision';\n",
                   {'deck_id': deck['id'], 'revision': deck['revision']})
    ranking = (ROOT / 'src/main/resources/sql/recommendations.sql').read_text()
    ranking = re.sub(r'(?<!:):([a-z_]+)', r":'\1'", ranking)
    params = {'revision_id': revision, 'commander_id': deck['commander']['oracleId'],
              'identity': '{' + ','.join(deck['commander']['colorIdentity']) + '}', 'theme': deck['theme'],
              'land_target': 36, 'ramp_target': 10, 'draw_target': 10, 'interaction_target': 10}
    plan = json.loads(sql('EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)\n' + ranking, params))
    folder = ROOT / 'docs/evidence'
    folder.mkdir(parents=True, exist_ok=True)
    (folder / 'recommendation-plan.json').write_text(json.dumps(plan, indent=2) + '\n')
    warm = sorted(times[1:])
    report = {'measured_at_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
              'platform': platform.system() + ' ' + platform.machine(), 'java': '21', 'postgresql_major': 17,
              'catalog_cards': catalog['activeCards'], 'catalog_source_updated_at': catalog['sourceUpdatedAt'],
              'deck_id': deck['id'], 'deck_name': deck['name'],
              'deck_revision': deck['revision'], 'suggestions': len(run['suggestions']),
              'first_measured_http_ms': round(times[0], 3),
              'warm_http_ms': {'iterations': args.iterations, 'p50': round(statistics.median(warm), 3),
                               'p95': round(warm[max(0, int(len(warm) * .95 + .999) - 1)], 3),
                               'min': round(min(warm), 3), 'max': round(max(warm), 3)},
              'sql_planning_ms': plan[0]['Planning Time'], 'sql_execution_ms': plan[0]['Execution Time'],
              'limits': 'Local sample on one deck; JVM already running. No cold-start, concurrent-load, hosted, or AI latency claim.'}
    (folder / 'performance.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    if args.max_p95_ms is not None and report['warm_http_ms']['p95'] > args.max_p95_ms:
        raise SystemExit('Local p95 response budget exceeded; inspect the retained evidence before changing code.')


if __name__ == '__main__':
    main()
