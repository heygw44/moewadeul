-- 지도 영역 조회가 쓰는 위도·경도 생성 컬럼과 인덱스.
-- 지도 영역 조회는 화면 사각형 안의 ACTIVE 장소를 고르고, 장소가 많으면 위도·경도로 칸 번호를 계산해 묶는다.
-- 이 일을 location에서 하면 MySQL이 행마다 ST_Latitude, ST_Longitude를 여러 번 계산해야 한다.
-- 장소 100만 건, 전국 영역에서 측정해 보니 묶음 조회 18.8초 가운데 대부분이 이 계산이었다.
-- 그래서 위도·경도를 행을 쓸 때 한 번만 계산해 STORED 생성 컬럼으로 저장한다. 앱은 이 컬럼에 값을 넣지 않고, MySQL이 location에서 채운다.
-- (status, latitude, longitude, type, park_warning) 인덱스는 영역 조건과 유형·경고 필터, 묶음 집계에 필요한 값을 모두 담는다.
-- 그래서 필터가 상세 테이블을 쓰지 않는 조회는 MySQL이 spot 행을 읽지 않고 인덱스만 읽고 끝낸다.
-- 공간 인덱스 spx_spot_location은 반경 검색과 공원 경계 판정이 계속 쓰므로 그대로 둔다.
ALTER TABLE spot
    ADD COLUMN latitude DOUBLE AS (ST_Latitude(location)) STORED NOT NULL,
    ADD COLUMN longitude DOUBLE AS (ST_Longitude(location)) STORED NOT NULL,
    ADD INDEX idx_spot_status_latitude_longitude (status, latitude, longitude, type, park_warning);
