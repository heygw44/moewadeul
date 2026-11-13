-- 같은 CI 해시의 본인확인을 INSERT 전에 한 줄로 세우는 잠금 행. CI 해시 앞 16비트를 칸 번호로 쓴다.
-- identity_verification의 유니크 검사는 지운 행과 이웃 키에 간격 잠금을 걸어, 동시 요청끼리 서로를 기다리는 교착 상태를 낸다.
-- 지워지지 않는 행을 먼저 배타 잠금하면 같은 칸의 요청은 INSERT에 닿기 전에 기다리고, 간격 잠금으로 돌아가지 않는다.
-- 칸 번호는 해시 일부라 개인을 가리키지 않으므로 보관 기한 정리 대상이 아니다. 행을 지우지 않는다.
CREATE TABLE identity_ci_lock (
    bucket SMALLINT UNSIGNED NOT NULL,
    PRIMARY KEY (bucket)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO identity_ci_lock (bucket)
WITH hex_digit (n) AS (
    SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
    UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11
    UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
)
SELECT a.n * 4096 + b.n * 256 + c.n * 16 + d.n
FROM hex_digit a CROSS JOIN hex_digit b CROSS JOIN hex_digit c CROSS JOIN hex_digit d;
