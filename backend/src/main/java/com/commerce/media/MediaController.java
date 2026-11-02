package com.commerce.media;

import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves product images stored as base64 in MySQL at the same public URLs as before:
 * {@code /media/{key}.jpg} (full size) and {@code /media/{key}_t.jpg} (thumbnail). Keys are random and never reused, so
 * responses are cached for a year.
 */
@RestController
public class MediaController {
    private static final Pattern NAME = Pattern.compile("^([a-f0-9]{32})(_t)?\\.jpg$");

    private final ProductImageDataRepository data;

    public MediaController(ProductImageDataRepository data) {
        this.data = data;
    }

    @GetMapping("/media/{name}")
    public ResponseEntity<byte[]> image(@PathVariable("name") String name) {
        Matcher m = NAME.matcher(name);
        if (!m.matches()) return ResponseEntity.notFound().build();
        String key = m.group(1);
        Optional<String> encoded = m.group(2) != null ? data.findThumbBase64(key) : data.findLargeBase64(key);
        if (encoded.isEmpty()) return ResponseEntity.notFound().build();
        byte[] bytes = Base64.getDecoder().decode(encoded.get());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .contentLength(bytes.length)
                .body(bytes);
    }
}
