-- 지도 마커 조회는 영역 안 장소의 id, type, name, 위도·경도, park_warning을 읽는다.
-- V32 인덱스에는 name이 없어서, MySQL은 인덱스로 고른 행마다 기본 키로 장소 본 행을 다시 읽었다.
-- 장소 100만 건이면 본 행이 버퍼 풀에 다 들어가지 않아 이 읽기가 디스크로 가고, 디스크 읽기 한도(초당 3,000회)에 닿으면 마커 조회가 초 단위로 밀린다.
-- name을 끝에 더하면 마커 조회가 인덱스만 읽고 끝난다. 앞 다섯 열은 그대로라 영역 조건과 필터의 쓰임은 바뀌지 않는다.
ALTER TABLE spot
    DROP INDEX idx_spot_status_latitude_longitude,
    ADD INDEX idx_spot_status_latitude_longitude (status, latitude, longitude, type, park_warning, name);
