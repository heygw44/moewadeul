-- 자바 패키지 이름을 com.pitchmap에서 com.moewadeul로 바꾸면서, 이미 저장된 로그인 세션을 모두 지운다.
-- Spring Session JDBC는 로그인한 회원 객체(LoginMember)를 JDK 직렬화로 세션 속성 테이블에 저장하고, 직렬화 데이터에는 클래스의 전체 이름이 들어간다.
-- 그래서 새 코드는 예전 패키지 이름으로 저장된 세션을 읽지 못한다(ClassNotFoundException). 그 세션 쿠키로 들어오는 요청은 모두 500이 된다.
-- 세션을 지우면 회원은 한 번 다시 로그인하면 된다. 세션 속성 행은 외래 키의 ON DELETE CASCADE로 함께 지워진다.
DELETE FROM SPRING_SESSION;
