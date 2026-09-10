package com.pml.wallet.controller;

import com.pml.wallet.exception.ForbiddenException;

final class Auth {
    private Auth() {
    }

    static String user(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ForbiddenException("bearer token required");
        }
        String user = authorization.substring(7).trim();
        if (user.isBlank()) {
            throw new ForbiddenException("bearer token required");
        }
        return user;
    }
}
