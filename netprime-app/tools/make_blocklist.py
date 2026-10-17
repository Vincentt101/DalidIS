"""Turns a hosts-format ad list (e.g. StevenBlack/hosts) into assets/adhosts.txt."""
import sys
src, out = sys.argv[1], sys.argv[2]
skip = {'localhost', 'localhost.localdomain', 'local', '0.0.0.0', 'broadcasthost'}
hosts = set()
for line in open(src, encoding='utf-8', errors='ignore'):
    p = line.split('#')[0].split()
    if len(p) >= 2 and p[0] in ('0.0.0.0', '127.0.0.1'):
        h = p[1].lower().strip('.')
        if h not in skip and 'netprime' not in h:
            hosts.add(h)
open(out, 'w').write('\n'.join(sorted(hosts)) + '\n')
print(len(hosts), 'ad domains')
