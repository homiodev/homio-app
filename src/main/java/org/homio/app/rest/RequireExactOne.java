package org.homio.app.rest;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {RequireExactOne.Validator.class})
public @interface RequireExactOne {

    String message() default "Field value should be from list of ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String propName() default "";

    class Validator implements ConstraintValidator<RequireExactOne, String> {

        private String propName;

        @Override
        public void initialize(RequireExactOne requiredIfChecked) {
            this.propName = requiredIfChecked.propName();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            /*Boolean valid = this.allowable.contains(value);

            if (!Boolean.TRUE.equals(valid)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(message.concat(this.allowable.toString()))
                       .addPropertyNode(this.propName).addConstraintViolation();
            }
            return valid;*/
            return true;
        }
    }
}
