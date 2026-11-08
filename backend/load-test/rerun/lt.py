"""재현 측정이 함께 쓰는 도구. env.sh와 같은 위치 규칙을 쓴다.

측정 전 검사 세 가지를 여기서 한다. 어느 하나라도 어긋나면 예외로 멈춘다.
- 이미지의 git.sha 라벨 코드가 HEAD의 backend 코드와 같은가(check_image). 옛 코드와 번갈아 잴 때만 LT_OLD_SHA=<그 커밋>으로 그 이미지를 허용한다
- MySQL 디스크 읽기 제한이 요청한 대로 걸렸는가(ensure_stack)
- 앱이 관리 포트 8081에서 180초 안에 UP으로 답하는가(wait_ready)
"""
import json, os, pathlib, subprocess, time, urllib.error, urllib.request

TOOLS = pathlib.Path(__file__).resolve().parent
ROOT = TOOLS.parents[2]
MAIN = pathlib.Path(subprocess.run(["git", "-C", str(TOOLS), "rev-parse", "--path-format=absolute", "--git-common-dir"], capture_output=True, text=True, check=True).stdout.strip()).parent
LT_DIR = pathlib.Path(os.environ.get("LT_DIR", MAIN / "backend/load-test/results/2026-11-03-local-openai-s3-wait"))
PROJECT = os.environ.get("LT_PROJECT", "moewadeul-lt-weather")
NET = f"{PROJECT}_default"
APP, MYSQL, CADDY = (f"{PROJECT}-{n}-1" for n in ("app", "mysql", "caddy"))
BASE = "http://127.0.0.1:8080"
MGMT = "http://127.0.0.1:8081"
LOCK = pathlib.Path("/tmp/moewadeul-loadtest.lock")
PW = "LoadTest#2026"
ADMIN_EMAIL = "admin@loadtest.invalid"
CODE_PATHS = ["backend/src/main", "backend/build.gradle.kts", "backend/Dockerfile", "backend/scripts"]
K6_IMAGE = "grafana/k6"


def sh(*a, check=False, **k):
    return subprocess.run([str(x) for x in a], capture_output=True, text=True, check=check, **k)


def require_lock():
    if not LOCK.is_dir():
        raise SystemExit(f"측정 잠금 {LOCK}이 없다. withlock.sh로 돌린다")


def env_value(key):
    for line in (LT_DIR / "opt.env").read_text().splitlines():
        if line.startswith(key + "="):
            return line.split("=", 1)[1].strip().strip("'\"")
    raise KeyError(key)


def check_image(tag):
    sha = sh("docker", "inspect", "-f", '{{index .Config.Labels "git.sha"}}', f"moewadeul-lt-app:{tag}").stdout.strip()
    if not sha or sha == "<no value>":
        raise SystemExit(f"moewadeul-lt-app:{tag}에 git.sha 라벨이 없다. image.sh로 만든다")
    diff = sh("git", "-C", ROOT, "diff", "--name-only", sha, "HEAD", "--", *CODE_PATHS)
    if diff.returncode != 0:
        raise SystemExit(f"라벨 커밋 {sha}을 이 저장소에서 찾지 못한다: {diff.stderr.strip()}")
    if diff.stdout.strip() and not (os.environ.get("LT_OLD_SHA") and sha.startswith(os.environ["LT_OLD_SHA"])):
        raise SystemExit(f"이미지 {tag}({sha[:8]})의 코드가 HEAD와 다르다:\n{diff.stdout}")
    return sha


def compose_env(tag, disk, **extra):
    env = {**os.environ, "APP_TAG": tag, "LT_DISK": "1" if disk else "0"}
    env.update({k: str(v) for k, v in extra.items()})
    return env


def lt(tag, disk, *args, check=True, **extra):
    r = subprocess.run([str(TOOLS / "lt.sh"), *args], env=compose_env(tag, disk, **extra), capture_output=True, text=True)
    if check and r.returncode != 0:
        raise SystemExit(f"lt.sh {' '.join(args)} 실패: {r.stderr[-800:]}")
    return r


def disk_limited():
    out = sh("docker", "inspect", "-f", "{{json .HostConfig.BlkioDeviceReadIOps}}", MYSQL).stdout.strip()
    return bool(out) and out not in ("null", "[]")


def ensure_stack(tag, disk, **extra):
    """MySQL, Caddy, 스텁, SMTP를 띄우고 디스크 제한이 요청과 같은지 확인한다. 다르면 MySQL을 다시 만든다."""
    lt(tag, disk, "up", "-d", "mysql", "caddy", "extstub", "smtpsink", **extra)
    if disk_limited() != disk:
        lt(tag, disk, "up", "-d", "--force-recreate", "--no-deps", "mysql", **extra)
    if disk_limited() != disk:
        raise SystemExit(f"MySQL 디스크 제한이 {disk}이어야 하는데 {disk_limited()}이다")
    for _ in range(90):
        if sh("docker", "inspect", "-f", "{{.State.Health.Status}}", MYSQL).stdout.strip() == "healthy":
            return
        time.sleep(2)
    raise SystemExit("MySQL이 180초 안에 healthy가 되지 않았다")


def wait_ready(timeout=None):
    # 볼륨이 앞선 Flyway 버전이면 첫 기동이 마이그레이션으로 길어진다. 그 한 번만 LT_READY_TIMEOUT으로 늘린다.
    timeout = timeout or int(os.environ.get("LT_READY_TIMEOUT", "180"))
    end = time.time() + timeout
    last = None
    while time.time() < end:
        try:
            body = urllib.request.urlopen(MGMT + "/actuator/health", timeout=2).read().decode()
            if json.loads(body).get("status") == "UP":
                return
            last = body[:200]
        except Exception as e:
            last = repr(e)
        time.sleep(2)
    raise SystemExit(f"앱이 {timeout}초 안에 {MGMT}/actuator/health UP으로 답하지 않았다: {last}")


def restart_app(tag, disk, **extra):
    """검사를 마친 이미지로 앱만 새로 띄운다. 돌아온 값은 측정 기록(meta)에 남길 조건이다."""
    sha = check_image(tag)
    ensure_stack(tag, disk, **extra)
    lt(tag, disk, "up", "-d", "--force-recreate", "--no-deps", "app", **extra)
    wait_ready()
    return {"tag": tag, "image_sha": sha, "disk_limit": disk_limited(), "head": sh("git", "-C", ROOT, "rev-parse", "HEAD").stdout.strip(),
            "env": {k: str(v) for k, v in extra.items()}, "load_before": os.getloadavg(), "started": time.time()}


def reuse_app(tag, disk, **extra):
    """앱을 다시 띄우지 않고 이어서 잴 때 쓴다. 떠 있는 앱의 이미지와 환경 변수가 요청과 같은지만 확인한다."""
    sha = check_image(tag)
    if disk_limited() != disk:
        raise SystemExit(f"MySQL 디스크 제한이 {disk}이어야 하는데 {disk_limited()}이다")
    image = sh("docker", "inspect", "-f", "{{.Config.Image}}", APP).stdout.strip()
    if image != f"moewadeul-lt-app:{tag}":
        raise SystemExit(f"떠 있는 앱 이미지가 {image}이다. moewadeul-lt-app:{tag}이어야 한다")
    env = dict(e.split("=", 1) for e in json.loads(sh("docker", "inspect", "-f", "{{json .Config.Env}}", APP).stdout))
    names = {"LT_POOL": "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", "LT_TOMCAT": "SERVER_TOMCAT_THREADS_MAX"}
    for k, v in extra.items():
        if k in names and env.get(names[k]) != str(v):
            raise SystemExit(f"떠 있는 앱의 {names[k]}={env.get(names[k])}인데 {v}로 재려 했다")
    wait_ready()
    return {"tag": tag, "image_sha": sha, "disk_limit": disk_limited(), "head": sh("git", "-C", ROOT, "rev-parse", "HEAD").stdout.strip(),
            "env": {k: str(v) for k, v in extra.items()}, "load_before": os.getloadavg(), "started": None, "reused_app": True}


def mysql_args(extra=()):
    return ["docker", "exec", "-i", "-e", f"MYSQL_PWD={env_value('DB_ROOT_PASSWORD')}", MYSQL, "mysql", "-uroot", "--default-character-set=utf8mb4", *extra, env_value("DB_NAME")]


def sql(stmt, timeout=1800):
    r = subprocess.run(mysql_args(["-N", "-B"]), input=stmt, capture_output=True, text=True, timeout=timeout)
    if r.returncode != 0:
        raise RuntimeError(f"SQL 실패: {r.stderr.strip()[-400:]}\n{stmt[:300]}")
    return r.stdout.strip()


METRICS = {"Innodb_deadlocks": "lock_deadlocks", "Innodb_lock_timeouts": "lock_timeouts"}


def status(*names):
    """전역 상태 변수. MySQL 8.4에는 Innodb_deadlocks 상태 변수가 없어서 데드락과 잠금 대기 초과는 INNODB_METRICS에서 읽는다."""
    out = sql("SELECT VARIABLE_NAME, VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME IN (%s)" % ",".join(f"'{n}'" for n in names))
    v = {k: int(x) for k, x in (l.split("\t") for l in out.splitlines())}
    wanted = [n for n in names if n in METRICS]
    if wanted:
        m = dict(l.split("\t") for l in sql("SELECT NAME, COUNT FROM information_schema.INNODB_METRICS WHERE NAME IN (%s)" % ",".join(f"'{METRICS[n]}'" for n in wanted)).splitlines())
        v.update({n: int(m[METRICS[n]]) for n in wanted})
    missing = set(names) - set(v)
    if missing:
        raise KeyError(f"상태 값이 없다: {missing}")
    return v


def request(method, path, body=None, session=None, csrf=None, headers=None, timeout=120):
    """앱 8080에 바로 보낸다. (상태 코드, 응답 헤더, 본문, 걸린 초)를 돌려준다."""
    h = {"Content-Type": "application/json", **(headers or {})}
    cookies = [f"SESSION={session}"] if session else []
    if csrf:
        cookies.append(f"XSRF-TOKEN={csrf}")
        h["X-XSRF-TOKEN"] = csrf
    if cookies:
        h["Cookie"] = "; ".join(cookies)
    data = json.dumps(body).encode() if body is not None else (b"" if method == "POST" else None)
    t = time.time()
    try:
        r = urllib.request.urlopen(urllib.request.Request(BASE + path, data=data, method=method, headers=h), timeout=timeout)
    except urllib.error.HTTPError as e:
        r = e
    raw = r.read().decode()
    return r.status, r.headers, raw, time.time() - t


def error_code(raw):
    try:
        return json.loads(raw).get("code") if raw else None
    except (ValueError, AttributeError):
        return None


def cookie(headers, name):
    for v in headers.get_all("Set-Cookie") or []:
        if v.startswith(name + "="):
            return v.split(";")[0].split("=", 1)[1]


def csrf_token():
    return cookie(request("GET", "/api/programs")[1], "XSRF-TOKEN")


def login(csrf, email):
    st, h, raw, _ = request("POST", "/api/auth/login", {"email": email, "password": PW}, csrf=csrf)
    if st != 200:
        raise RuntimeError(f"로그인 실패 {email} {st} {raw[:200]}")
    return cookie(h, "SESSION")


def prom(timeout=3):
    try:
        return urllib.request.urlopen(MGMT + "/actuator/prometheus", timeout=timeout).read().decode()
    except Exception:
        return ""


def prom_values(text, *names):
    v = {}
    for line in text.splitlines():
        for n in names:
            if line.startswith(n + "{") or line.startswith(n + " "):
                v[n] = v.get(n, 0.0) + float(line.rsplit(" ", 1)[1])
    return v


def k6(out, script, env=(), mounts=(), extra=(), name=None, detach=False):
    """k6를 앱과 다른 CPU(4-7)에서 돌린다. /scripts는 backend/load-test, /r은 이 폴더다."""
    cmd = ["docker", "run", "--rm", "--network", NET, "--cpuset-cpus", "4-7", "--ulimit", "nofile=65536:65536",
           "-v", f"{ROOT}/backend/load-test:/scripts:ro", "-v", f"{TOOLS}:/r:ro", "-v", f"{out}:/out", "-e", "K6_CSV_TIME_FORMAT=unix_milli"]
    if name:
        cmd += ["--name", name]
    for m in mounts:
        cmd += ["-v", m]
    cmd += [K6_IMAGE, "run", "-q", "--no-thresholds", "-e", "BASE_URL=http://caddy:80", *extra]
    for k, v in dict(env).items():
        cmd += ["-e", f"{k}={v}"]
    cmd.append(script)
    return cmd


def run_dir(group, name):
    d = MAIN / "backend/load-test/results" / group / "runs" / name
    d.mkdir(parents=True, exist_ok=True)
    d.chmod(0o777)
    return d
