# 프론트엔드 빌드 결과(/srv)만 담는 운반용 이미지다. 실행하지 않는다. 앱과 프론트엔드를 같은 커밋 SHA 태그로 함께 배포하려고 이미지로 묶는다.
# deploy.sh가 이 이미지에서 /srv를 서버의 /opt/moewadeul/web/releases/<태그>로 꺼내 쓴다. 실행되는 Caddy는 업스트림 이미지다(docker-compose.prod.yml).
# 빌드 컨텍스트는 저장소 루트다(docker build -f deploy/caddy/Dockerfile .).

FROM node:24-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
# 카카오 JS 키는 Vite가 빌드할 때 번들에 넣는다. 브라우저에 공개되는 키라 빌드 인자로 받아도 되고,
# 카카오 콘솔의 도메인 제한이 이 키를 보호한다. 다른 비밀값은 빌드 인자로 넘기지 않는다.
ARG VITE_KAKAO_JS_KEY
ENV VITE_KAKAO_JS_KEY=${VITE_KAKAO_JS_KEY}
RUN npm run build

FROM scratch
COPY --from=web /web/dist /srv
