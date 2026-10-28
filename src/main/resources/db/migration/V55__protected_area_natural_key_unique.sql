-- 자연 키 (source, area_type, name)마다 가장 작은 id의 행만 남긴다. 장소가 지울 행을 가리키면 남길 행으로 옮긴 뒤 지운다.
-- 중복이 없으면 두 문장 모두 아무 행도 바꾸지 않는다.
UPDATE spot s
JOIN protected_area pa ON pa.id = s.protected_area_id
JOIN (
    SELECT source, area_type, name, MIN(id) AS keep_id
    FROM protected_area
    GROUP BY source, area_type, name
) k ON k.source = pa.source AND k.area_type = pa.area_type AND k.name = pa.name
SET s.protected_area_id = k.keep_id
WHERE s.protected_area_id <> k.keep_id;

DELETE pa
FROM protected_area pa
JOIN (
    SELECT source, area_type, name, MIN(id) AS keep_id
    FROM protected_area
    GROUP BY source, area_type, name
) k ON k.source = pa.source AND k.area_type = pa.area_type AND k.name = pa.name
WHERE pa.id <> k.keep_id;

ALTER TABLE protected_area
    ADD CONSTRAINT uk_protected_area_source_area_type_name UNIQUE (source, area_type, name);
