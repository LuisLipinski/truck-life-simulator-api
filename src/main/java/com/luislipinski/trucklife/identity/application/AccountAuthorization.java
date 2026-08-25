package com.luislipinski.trucklife.identity.application;

import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AccountAuthorization {

    public void requireAnyRole(AuthenticatedAccount account, UserRole... allowedRoles) {
        boolean allowed = account != null
                && Arrays.stream(allowedRoles).anyMatch(role -> role == account.role());
        if (!allowed) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "Access forbidden",
                    "The authenticated account does not have permission for this operation"
            );
        }
    }
}
