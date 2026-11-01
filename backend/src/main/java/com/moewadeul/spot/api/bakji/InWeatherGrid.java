package com.moewadeul.spot.api.bakji;

import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.WeatherGrid;
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
@Constraint(validatedBy = InWeatherGrid.Validator.class)
@interface InWeatherGrid {

    String message() default "서비스 지역 밖 좌표예요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<InWeatherGrid, BakjiCreateRequest> {

        private static final double MAX_ABS_LATITUDE = 90;
        private static final double MAX_ABS_LONGITUDE = 180;

        @Override
        public boolean isValid(BakjiCreateRequest request, ConstraintValidatorContext context) {
            if (request == null || request.lat() == null || request.lng() == null) {
                return true;
            }
            if (!Double.isFinite(request.lat())
                    || !Double.isFinite(request.lng())
                    || Math.abs(request.lat()) > MAX_ABS_LATITUDE
                    || Math.abs(request.lng()) > MAX_ABS_LONGITUDE) {
                return true;
            }
            if (WeatherGrid.covers(new GeoPoint(request.lat(), request.lng()))) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("lat")
                    .addConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("lng")
                    .addConstraintViolation();
            return false;
        }
    }
}
