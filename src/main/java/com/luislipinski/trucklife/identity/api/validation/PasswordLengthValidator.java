package com.luislipinski.trucklife.identity.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordLengthValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MINIMUM_LENGTH = 12;
    private static final int MAXIMUM_LENGTH = 128;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = value.codePointCount(0, value.length());
        return length >= MINIMUM_LENGTH && length <= MAXIMUM_LENGTH;
    }
}
