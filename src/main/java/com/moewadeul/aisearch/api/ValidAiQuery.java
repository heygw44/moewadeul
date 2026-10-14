package com.moewadeul.aisearch.api;

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
 * 앞뒤 공백을 뺀 문장이 2자 이상 100자 이하인지 검사한다. null과 공백뿐인 문장도 거부한다.
 * {@code @Size}를 쓰지 않는 이유: 그것은 앞뒤 공백을 세고 UTF-16 문자 수로 세서, 공백이 낀 문장이나 이모지의 길이를 규칙과 다르게 센다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidAiQuery.Validator.class)
@interface ValidAiQuery {

    int MIN_LENGTH = 2;
    int MAX_LENGTH = 100;

    String message() default "문장은 공백을 빼고 2자 이상 100자 이하로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidAiQuery, String> {

        @Override
        public boolean isValid(String query, ConstraintValidatorContext context) {
            if (query == null) {
                return false;
            }
            String stripped = query.strip();
            int length = stripped.codePointCount(0, stripped.length());
            return length >= MIN_LENGTH && length <= MAX_LENGTH;
        }
    }
}
