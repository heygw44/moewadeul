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
