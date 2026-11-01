-- 장소 상세는 열 때마다 그 장소의 후기 평점(COUNT, AVG)과 최근 후기 3개를 구한다. 후기 목록도 최신순으로 쪽을 나눠 읽는다.
-- 후기 인덱스가 유니크 (spot_id, member_id, visited_date)와 회원 외래 키뿐이면, 두 조회가 그 장소의 후기를 모두 읽고
-- 후기마다 기본 키로 행을 다시 찾는다. 최근 3개는 모두 읽은 뒤 created_at DESC, id DESC로 정렬해 3개를 자른다.
-- 후기 2만 건인 장소에서 평점이 20,006행, 최근 3개가 20,012행을 읽었고, 그 장소의 상세를 300명이 함께 보면 p95가 3.7초였다.

-- 최근 후기 3개와 후기 목록의 정렬용이다. MySQL이 인덱스를 거꾸로 읽어 필요한 개수만 읽는다(2만 행 읽기 → 3행).
CREATE INDEX idx_spot_review_spot_created ON spot_review (spot_id, created_at, id);

-- 평균 평점과 후기 수용이다. 평점이 인덱스에 들어 있어서 기본 키로 행을 다시 찾지 않고 인덱스만 읽는다(커버링).
CREATE INDEX idx_spot_review_spot_rating ON spot_review (spot_id, rating);
