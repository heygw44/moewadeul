-- 장소 상세의 평균 평점은 회원마다 한 표라서, 서버가 그 장소의 후기를 member_id로 묶어 회원별 평균을 낸 뒤 다시 평균 낸다.
-- (spot_id, rating) 인덱스에는 member_id가 없어 이 계산에 쓰지 못하고, 서버가 유니크 인덱스를 훑으며 후기마다 기본 키로 행을 다시 읽었다.
-- member_id 순서로 묶고 평점까지 인덱스에서 읽도록 (spot_id, member_id, rating)으로 바꾼다.
-- 지우는 인덱스를 쓰는 다른 조회는 없다. 장소 외래 키는 유니크 (spot_id, member_id, visited_date) 인덱스가 받친다.
CREATE INDEX idx_spot_review_spot_member_rating ON spot_review (spot_id, member_id, rating);

DROP INDEX idx_spot_review_spot_rating ON spot_review;
