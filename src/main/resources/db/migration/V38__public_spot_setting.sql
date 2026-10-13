-- 고캠핑 야영장의 입지(산, 숲, 계곡 등). 원천 필드 lctCl은 "산,숲,계곡"처럼 쉼표로 여러 값을 준다.
-- 한 야영장에 입지가 여러 개 붙을 수 있어서 public_spot_detail의 열이 아니라 별도 테이블에 한 행씩 둔다.
-- setting은 앱의 PublicSpotSetting enum 이름이고 앱이 값을 검사한다. 입지가 없는 야영장은 행이 없다.
-- 입지로 거르는 조회는 spot_id로 EXISTS를 걸어 확인한다. 이 조회는 기본 키 (spot_id, setting)의 앞부분 spot_id를 쓰므로 별도 인덱스는 두지 않는다.
CREATE TABLE public_spot_setting (
    spot_id BIGINT NOT NULL,
    setting VARCHAR(20) NOT NULL,
    PRIMARY KEY (spot_id, setting),
    CONSTRAINT fk_public_spot_setting_detail FOREIGN KEY (spot_id) REFERENCES public_spot_detail (spot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
