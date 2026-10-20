package de.Z7534.ztone.stonelog.util;

import java.util.UUID;

public class AvatarUtil {

    public static String headUrl(String baseUrl, UUID uuid) {
        if (baseUrl == null || uuid == null) return null;
        return baseUrl.replace("{uuid}", uuid.toString());
    }
}
