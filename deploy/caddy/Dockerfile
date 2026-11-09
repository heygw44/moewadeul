# 빌드 컨텍스트는 frontend/ 폴더다(docker build -f deploy/caddy/Dockerfile frontend).
# 기본 대상은 마지막 단계인 화면 파일 이미지다. Caddy 서버 이미지는 --target server로 따로 빌드한다.
# 서버 이미지의 태그는 아래 Caddy 버전과 모듈 커밋으로 정해진다(server-image.sh). 둘을 바꾸면 compose의 caddy 이미지 태그도 바꾼다.

# 날씨 요청을 IP별로 막는 rate_limit 지시어가 이 모듈에 있다. 태그가 2024년의 v0.1.0 하나뿐이라 커밋으로 고정한다.
FROM caddy:2.11.8-builder-alpine AS caddy-build
RUN xcaddy build --with github.com/mholt/caddy-ratelimit@5625512f24f6f59d6f64fb3aafe5eecff0b286db

FROM caddy:2.11.8-alpine AS server
COPY --from=caddy-build /usr/bin/caddy /usr/bin/caddy

FROM node:24-alpine AS web
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci
COPY . ./
# 카카오 JS 키는 Vite가 빌드할 때 번들에 넣는다. 브라우저에 공개되는 키라 빌드 인자로 받아도 되고,
# 카카오 콘솔의 도메인 제한이 이 키를 보호한다. 다른 비밀값은 빌드 인자로 넘기지 않는다.
ARG VITE_KAKAO_JS_KEY
ENV VITE_KAKAO_JS_KEY=${VITE_KAKAO_JS_KEY}
RUN npm run build

FROM scratch
COPY --from=web /web/dist /srv
