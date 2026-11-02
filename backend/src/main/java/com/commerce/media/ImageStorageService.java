package com.commerce.media;

import com.commerce.common.exception.ValidationFailedException;
import com.commerce.config.AppProperties;
import com.commerce.shop.SettingsService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Service;

/**
 * Validates uploads (size, magic bytes, decodability, dimensions), crops to the configured aspect ratio and produces
 * random-keyed JPEG derivatives, returned as base64 text for storage in MySQL (table {@code product_image_data}).
 * Nothing is written to disk, so a rolled-back transaction cannot leave orphaned files (SRS 6.2 / 7.1 / CAT-07 / CAT-08).
 */
@Service
public class ImageStorageService {
    static final int LARGE_LONG_SIDE = 1200;
    static final int THUMB_LONG_SIDE = 400;
    static final int MIN_DIMENSION = 200;

    public record Stored(String key, int width, int height, String imageBase64, String thumbBase64) {}

    private final AppProperties props;
    private final SettingsService settings;

    public ImageStorageService(AppProperties props, SettingsService settings) {
        this.props = props;
        this.settings = settings;
    }

    /** True when the bytes start with a JPEG or PNG signature. */
    static boolean hasAllowedSignature(byte[] b) {
        if (b.length < 12) return false;
        boolean jpeg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        return jpeg || png;
    }

    /** Validates and stores an image; returns its random key and the stored (cropped) dimensions. */
    public Stored store(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new ValidationFailedException("file", "Choose an image file.");
        if (bytes.length > props.getMedia().getMaxFileBytes()) throw new ValidationFailedException("file", "Image is larger than " + props.getMedia().getMaxFileBytes() / 1_048_576 + " MB.");
        if (!hasAllowedSignature(bytes)) throw new ValidationFailedException("file", "Only JPEG and PNG images are accepted.");
        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException e) {
            throw new ValidationFailedException("file", "The image could not be read; it may be corrupt.");
        }
        if (src == null) throw new ValidationFailedException("file", "The image could not be read; it may be corrupt.");
        int max = props.getMedia().getMaxDimension();
        if (src.getWidth() > max || src.getHeight() > max) throw new ValidationFailedException("file", "Image dimensions exceed " + max + " px.");
        if (src.getWidth() < MIN_DIMENSION || src.getHeight() < MIN_DIMENSION) throw new ValidationFailedException("file", "Image must be at least " + MIN_DIMENSION + " px on each side.");

        int[] ratio = settings.aspectRatio();
        BufferedImage cropped = cropToRatio(src, ratio[0], ratio[1]);
        BufferedImage large = scaleToLongSide(cropped, LARGE_LONG_SIDE);
        BufferedImage thumb = scaleToLongSide(cropped, THUMB_LONG_SIDE);

        String key = UUID.randomUUID().toString().replace("-", "");
        try {
            String imageBase64 = Base64.getEncoder().encodeToString(toJpeg(large));
            String thumbBase64 = Base64.getEncoder().encodeToString(toJpeg(thumb));
            return new Stored(key, large.getWidth(), large.getHeight(), imageBase64, thumbBase64);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode image", e);
        }
    }

    static BufferedImage cropToRatio(BufferedImage src, int rw, int rh) {
        int w = src.getWidth();
        int h = src.getHeight();
        double target = (double) rw / rh;
        int cw = w;
        int ch = h;
        if ((double) w / h > target) cw = (int) Math.round(h * target);
        else ch = (int) Math.round(w / target);
        int x = (w - cw) / 2;
        int y = (h - ch) / 2;
        return src.getSubimage(x, y, Math.max(cw, 1), Math.max(ch, 1));
    }

    static BufferedImage scaleToLongSide(BufferedImage src, int longSide) {
        double scale = Math.min(1.0, (double) longSide / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);   // flattens alpha onto white
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] toJpeg(BufferedImage img) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.84f);
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
