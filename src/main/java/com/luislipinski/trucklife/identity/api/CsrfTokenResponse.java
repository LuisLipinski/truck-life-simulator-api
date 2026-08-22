package com.luislipinski.trucklife.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record CsrfTokenResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY)
        String token,

        @Schema(example = "X-CSRF-TOKEN")
        String headerName
) {

    @Override
    public String toString() {
        return "CsrfTokenResponse[token=redacted, headerName=" + headerName + ']';
    }
}
