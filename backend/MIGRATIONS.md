# 마이그레이션 호환 규칙

새 마이그레이션은 직전 릴리스 앱이 그대로 돌 수 있어야 합니다. 배포가 실패하면 `deploy.sh`가 직전 태그로 되돌리는데, Flyway는 되돌리지 않으므로 옛 앱이 새 스키마 위에서 돕니다.

실제로 일어난 일입니다. V50은 `applied_at`을 `NOT NULL`로 바꿨고, 이 스키마에서 옛 앱의 합류 신청은 100건 모두 500으로 끝났습니다(#840 측정).

## 규칙

옛 앱을 깨는 변경은 두 릴리스로 나눕니다.

| 하려는 일 | 첫 릴리스(expand) | 다음 릴리스(contract) |
|---|---|---|
| 필수 컬럼 추가 | `DEFAULT`를 두거나 `NULL`로 추가하고 앱이 값을 채움 | 채운 뒤 `NOT NULL`로 변경 |
| 컬럼 삭제 | 앱이 읽기와 쓰기를 멈춤 | 컬럼 삭제 |
| 컬럼이나 표 이름 변경 | 새 이름을 추가하고 앱이 양쪽에 씀 | 옛 이름 삭제 |
| 표 삭제 | 앱이 그 표를 쓰지 않음 | 표 삭제 |

contract 단계 마이그레이션에는 파일 안에 이유를 적습니다. 직전 릴리스가 이 스키마에서 돈다는 것을 확인한 뒤에 적습니다.

```sql
-- contract: v1.5.0부터 앱이 category를 읽고 쓰지 않는다.
ALTER TABLE community_post DROP COLUMN category;
```

## 검사

`MigrationCompatibilityRuleTest`가 V67부터 다음 변경을 찾습니다. 파일에 `-- contract:` 표시가 없으면 `./gradlew test`가 실패합니다.

- `DROP TABLE`, `RENAME TABLE`
- 컬럼 삭제, 이름 변경, `CHANGE`
- `MODIFY ... NOT NULL`
- 기본값이나 계산식이 없는 `NOT NULL` 컬럼 추가

SQL 문장의 모양만 봅니다. 값의 뜻을 바꾸거나 앱 코드가 새 값을 요구하는 변경은 잡지 못하니 리뷰에서 봅니다.
