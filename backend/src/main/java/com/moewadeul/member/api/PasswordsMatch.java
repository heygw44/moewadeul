package com.moewadeul.member.api;

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
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordsMatch.Validator.class)
public @interface PasswordsMatch {

    String message() default "비밀번호 확인이 비밀번호와 달라요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<PasswordsMatch, ConfirmedPassword> {

        @Override
        public boolean isValid(ConfirmedPassword value, ConstraintValidatorContext context) {
            if (value == null || value.passwordToConfirm() == null || value.passwordConfirmation() == null) {
                return true;
            }
            if (value.passwordToConfirm().equals(value.passwordConfirmation())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(value.confirmationFieldName())
                    .addConstraintViolation();
            return false;
        }
    }
}
