package com.athaydes.jgrab.daemon;

import com.athaydes.jgrab.JGrabHome;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

final class Authorizer {
    private static final String tokenFile = "token";

    public static String generateRandomToken() throws IOException {
        var token = generateToken();
        Files.writeString( new File( JGrabHome.getDir(), tokenFile ).toPath(),
                token, StandardCharsets.US_ASCII );
        return token;
    }

    private static String generateToken() {
        return UUID.randomUUID().toString();
    }
}
