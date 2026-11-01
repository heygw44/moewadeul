-- AI 검색 사용량을 회원·한국 날짜별로 한 행씩 센다. 회원의 하루 횟수 한도와 서비스 전체의 하루 비용 한도에 쓴다.
-- 회원이 입력한 문장은 이 테이블에도, 다른 어디에도 저장하지 않는다. 횟수와 토큰 수만 남긴다.
-- usage_date는 한국 날짜(DATE)다. 앱이 행을 잠그고(FOR UPDATE) 횟수를 판정하므로 같은 회원의 동시 요청이 한 줄로 선다.
--
-- 날짜 인덱스는 두 곳에서 쓴다. 하루 전체 토큰 합계를 구할 때 그 날짜의 행만 읽게 하고,
-- 보관 기간이 지난 행을 정리 작업이 지울 때 오래된 날짜부터 찾게 한다.
CREATE TABLE ai_search_usage (
    member_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    request_count INT NOT NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (member_id, usage_date),
    CONSTRAINT fk_ai_search_usage_member FOREIGN KEY (member_id) REFERENCES member (id),
    INDEX idx_ai_search_usage_usage_date (usage_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
