import http from 'k6/http';

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

// 검증용 데이터 생성 스크립트가 모든 검증 회원에게 넣은 가짜 비밀번호다. 검증 환경에만 있는 회원이라 운영에서는 쓸 수 없다.
const PASSWORD = __ENV.LOADTEST_PASSWORD || 'LoadTest#2026';

export const ADMIN_EMAIL = 'admin@loadtest.invalid';

export function applicantEmail(index) {
    return `user${String(index + 1).padStart(6, '0')}@loadtest.invalid`;
}

// 서버는 CSRF 토큰을 쿠키와 헤더로 한 번씩 받아 두 값이 같은지만 본다. 토큰이 세션에 묶이지 않으므로 검증 회원 모두가 토큰 하나를 같이 써도 된다.
export function fetchCsrfToken() {
    const res = http.get(`${BASE_URL}/api/programs`, { jar: new http.CookieJar(), tags: { name: 'csrf' } });
    const cookies = res.cookies['XSRF-TOKEN'];
    if (res.status !== 200 || !cookies || cookies.length === 0) {
        throw new Error(`CSRF 토큰을 받지 못했습니다. status=${res.status}`);
    }
    return cookies[0].value;
}

// http.batch에 넘길 로그인 요청을 만든다. 요청마다 새 쿠키 저장소를 써서 서로 다른 회원의 세션 쿠키가 섞이지 않게 한다.
export function loginRequest(email, csrfToken) {
    return {
        method: 'POST',
        url: `${BASE_URL}/api/auth/login`,
        body: JSON.stringify({ email, password: PASSWORD }),
        params: {
            jar: new http.CookieJar(),
            headers: {
                'Content-Type': 'application/json',
                Cookie: `XSRF-TOKEN=${csrfToken}`,
                'X-XSRF-TOKEN': csrfToken,
            },
            tags: { name: 'login' },
        },
    };
}

export function sessionFrom(res, email) {
    const cookies = res.cookies.SESSION;
    if (res.status !== 200 || !cookies || cookies.length === 0) {
        throw new Error(`로그인에 실패했습니다. email=${email}, status=${res.status}`);
    }
    return cookies[0].value;
}

export function login(email, csrfToken) {
    const req = loginRequest(email, csrfToken);
    return sessionFrom(http.request(req.method, req.url, req.body, req.params), email);
}

// SESSION 쿠키에는 늘 Secure 속성이 붙는다. 그래서 k6가 http 주소로는 쿠키를 돌려보내지 않으므로, 스크립트가 Cookie 헤더를 직접 만든다.
export function authHeaders(session, csrfToken) {
    return {
        Cookie: `SESSION=${session}; XSRF-TOKEN=${csrfToken}`,
        'X-XSRF-TOKEN': csrfToken,
    };
}
