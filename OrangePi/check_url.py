#!/usr/bin/env python3
import json
with open('/var/log.hdd/babycam/events/events.jsonl') as f:
    lines = f.readlines()
    if lines:
        last = json.loads(lines[-1].strip())
        print("URL:", last.get('url', 'NO_URL'))
        print("Full:", last)
