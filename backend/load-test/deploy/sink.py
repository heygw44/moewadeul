# 측정용 SMTP 받기 서버. 본문은 저장하지 않고, 받은 시각과 받는 사람 주소만 한 줄씩 남긴다.
# DELAY_MS만큼 DATA 응답을 늦춰 느린 외부 메일 서버를 흉내 낸다. 지연 값은 /sink/delay_ms 파일이 있으면 그 값을 먼저 쓴다.
import asyncio, os, time
LOG = open('/sink/received.log', 'a', buffering=1)

def delay():
    try:
        return int(open('/sink/delay_ms').read().strip()) / 1000
    except (OSError, ValueError):
        return int(os.environ.get('DELAY_MS', '0')) / 1000

async def handle(r, w):
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
            d = delay()
            if d: await asyncio.sleep(d)
            for to in rcpt: LOG.write(f'{time.time():.3f}\t{to}\n')
            rcpt = []
            say('250 ok')
        elif cmd.startswith('QUIT'): say('221 bye'); await w.drain(); break
        else: say('250 ok')
        await w.drain()
    w.close()

async def main():
    s = await asyncio.start_server(handle, '0.0.0.0', 2525)
    async with s: await s.serve_forever()

asyncio.run(main())
