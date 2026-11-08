# 측정용 SMTP 받기 서버(#841). 연결을 받을 때마다 /sink/mode를 읽어 동작을 정한다.
#   ok   받은 메일마다 `<epoch>\t<받는 사람>`을 /sink/received.log에 남긴다.
#   hang 연결만 받고 220 인사말을 보내지 않은 채 클라이언트가 끊을 때까지 붙든다. 연결을 연 시각을 /sink/connections.log에 남긴다.
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import D, S, ROOT, LEDGER  # noqa: E402,F401
import asyncio, time

RECEIVED = open('/sink/received.log', 'a', buffering=1)
CONNECTIONS = open('/sink/connections.log', 'a', buffering=1)


def mode():
    try:
        return open('/sink/mode').read().strip() or 'ok'
    except OSError:
        return 'ok'


async def hang(r, w):
    CONNECTIONS.write(f'{time.time():.3f}\n')
    try:
        while await r.read(4096):
            pass
    finally:
        w.close()


async def serve_ok(r, w):
    def say(s): w.write((s + '\r\n').encode())
    rcpt = []
    say('220 sink'); await w.drain()
    while True:
        line = await r.readline()
        if not line: break
        raw = line.decode(errors='replace').strip()
        cmd = raw.upper()
        if cmd.startswith(('EHLO', 'HELO')): say('250-sink'); say('250 8BITMIME')
        elif cmd.startswith('RCPT TO:'):
            rcpt.append(raw[8:].strip().strip('<>')); say('250 ok')
        elif cmd.startswith('DATA'):
            say('354 go'); await w.drain()
            while (await r.readline()) not in (b'.\r\n', b'.\n', b''): pass
            for to in rcpt: RECEIVED.write(f'{time.time():.3f}\t{to}\n')
            rcpt = []
            say('250 ok')
        elif cmd.startswith('QUIT'): say('221 bye'); await w.drain(); break
        else: say('250 ok')
        await w.drain()
    w.close()


async def handle(r, w):
    try:
        await (hang(r, w) if mode() == 'hang' else serve_ok(r, w))
    except (ConnectionError, asyncio.IncompleteReadError):
        w.close()


async def main():
    s = await asyncio.start_server(handle, '0.0.0.0', 2525)
    async with s: await s.serve_forever()

asyncio.run(main())
