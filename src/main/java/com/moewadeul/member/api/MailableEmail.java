package com.moewadeul.member.api;

import com.moewadeul.member.domain.Email;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이메일이 인증 메일을 받고 한도를 정확히 셀 수 있는 모양인지({@link Email#isMailableDomain}, {@link Email#isAsciiAddress}) 검사한다.
 * 점이 없는 도메인(localhost), IP 주소 도메인, ASCII가 아닌 글자(전각 문자, 보이지 않는 문자)가 든 주소를 거른다.
 *
 * <p>이메일 형식 자체는 {@code @Email}이 검사한다. 형식이 틀린 값에 이 제약까지 오류를 내면 같은 필드에 오류가 두 개 나오므로,
 * '@' 앞이나 뒤가 비어서 도메인을 가릴 수 없는 값과 null은 도메인 검사에서 통과시키고 {@code @Email}과 {@code @NotBlank}에 맡긴다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MailableEmail.Validator.class)
public @interface MailableEmail {

    String message() default "이메일에는 영문, 숫자, 기호만 쓸 수 있고 도메인에 점이 있어야 해요. IP 주소는 쓸 수 없어요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MailableEmail, String> {

        @Override
        public boolean isValid(String email, ConstraintValidatorContext context) {
            return email == null
                    || (Email.isAsciiAddress(email)
                            && Email.domainOf(email)
                                    .map(Email::isMailableDomain)
                                    .orElse(true));
        }
    }
}
