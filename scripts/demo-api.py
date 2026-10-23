#!/usr/bin/env python3
"""Create a separate local demonstration deck and exercise the documented REST flow."""
import json
import urllib.parse
import urllib.request

BASE = 'http://localhost:8080/api'


def api(path, payload=None):
    request = urllib.request.Request(BASE + path, data=None if payload is None else json.dumps(payload).encode(),
                                     headers={'Content-Type': 'application/json', 'Accept': 'application/json'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def main():
    commander = api('/cards?' + urllib.parse.urlencode({'q': 'Meren of Clan Nel Toth', 'commanders': 'true'}))['cards'][0]
    deck = api('/decks', {'name': 'API walkthrough', 'commanderOracleId': commander['oracleId'], 'theme': 'graveyard'})
    prefix = '/decks/' + deck['id']
    preview = api(prefix + '/imports/preview', {'text': '18 Forest\n18 Swamp\n1 Sol Ring\n1 Cultivate', 'commanderOracleId': commander['oracleId']})
    if preview['errors']:
        raise SystemExit(preview['errors'])
    deck = api(prefix + '/revisions', {'expectedRevision': deck['revision'], 'commanderOracleId': commander['oracleId'],
                                      'theme': 'graveyard', 'note': 'Imported through the API', 'entries': preview['entries']})
    analysis = api(prefix + '/analysis')
    run = api(prefix + '/recommendations', {})
    if run['suggestions']:
        deck = api('/recommendations/' + run['id'] + '/apply', {'suggestionId': run['suggestions'][0]['id'], 'expectedRevision': deck['revision']})
    diff = api(prefix + '/revisions/diff?from=2&to=' + str(deck['revision']))
    print(json.dumps({'deck_url': 'http://localhost:8080/decks/' + deck['id'], 'revision': deck['revision'],
                      'starting_count': analysis['totalCards'], 'suggestions': len(run['suggestions']), 'changes': diff}, indent=2))


if __name__ == '__main__':
    main()
