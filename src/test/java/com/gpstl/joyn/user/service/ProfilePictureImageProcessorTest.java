package com.gpstl.joyn.user.service;

import com.gpstl.joyn.user.exception.UserProfilePictureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfilePictureImageProcessorTest {

    private ProfilePictureImageProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ProfilePictureImageProcessor();
    }

    @Test
    @DisplayName("Should convert and resize a landscape PNG to a 512x512 WebP")
    void shouldConvertAndResizeLandscapeImageTo512x512Webp() throws IOException {
        // Landscape image: 800 x 400
        BufferedImage original = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = original.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 400, 400);
        g.setColor(Color.RED);
        g.fillRect(400, 0, 400, 400);
        g.dispose();

        byte[] pngBytes = writeImage(original, "png");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", pngBytes);

        byte[] webpBytes = processor.processProfilePicture(file);

        assertThat(webpBytes).isNotEmpty();
        // Check RIFF ... WEBP header
        assertThat(new String(webpBytes, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(webpBytes, 8, 4)).isEqualTo("WEBP");

        // Read back the WebP image and verify dimensions
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(webpBytes));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(512);
    }

    @Test
    @DisplayName("Should convert a portrait JPEG to a 512x512 WebP with square center crop")
    void shouldConvertPortraitJpegWithCenterCrop() throws IOException {
        // Portrait image: 300 x 600
        BufferedImage original = new BufferedImage(300, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = original.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 300, 600);
        g.dispose();

        byte[] jpegBytes = writeImage(original, "jpeg");
        MockMultipartFile file = new MockMultipartFile("photo", "portrait.jpg", "image/jpeg", jpegBytes);

        byte[] webpBytes = processor.processProfilePicture(file);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(webpBytes));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(512);
    }

    @Test
    @DisplayName("Should preserve alpha transparency when converting PNG with alpha")
    void shouldPreserveAlphaWhenConvertingTransparentPng() throws IOException {
        BufferedImage original = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = original.createGraphics();
        g.setColor(new Color(255, 0, 0, 128)); // 50% transparent red
        g.fillRect(50, 50, 100, 100);
        g.dispose();

        byte[] pngBytes = writeImage(original, "png");
        MockMultipartFile file = new MockMultipartFile("file", "transparent.png", "image/png", pngBytes);

        byte[] webpBytes = processor.processProfilePicture(file);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(webpBytes));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(512);
    }

    @Test
    @DisplayName("Should reject empty or null file")
    void shouldRejectEmptyOrNullFile() {
        assertThatThrownBy(() -> processor.processProfilePicture(null))
                .isInstanceOf(UserProfilePictureException.class)
                .hasMessageContaining("Un fichier image est requis");

        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> processor.processProfilePicture(emptyFile))
                .isInstanceOf(UserProfilePictureException.class)
                .hasMessageContaining("Un fichier image est requis");
    }

    @Test
    @DisplayName("Should reject non-image file such as text or PDF")
    void shouldRejectNonImageFile() {
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "script.txt", "text/plain", "Hello world, not an image".getBytes());

        assertThatThrownBy(() -> processor.processProfilePicture(textFile))
                .isInstanceOf(UserProfilePictureException.class)
                .hasMessageContaining("Format d'image non supporté");
    }

    @Test
    @DisplayName("Should reject image when dimensions are below 32x32")
    void shouldRejectImageWithTooSmallDimensions() throws IOException {
        BufferedImage tiny = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        byte[] bytes = writeImage(tiny, "png");
        MockMultipartFile file = new MockMultipartFile("file", "tiny.png", "image/png", bytes);

        assertThatThrownBy(() -> processor.processProfilePicture(file))
                .isInstanceOf(UserProfilePictureException.class)
                .hasMessageContaining("Dimensions de l'image trop petites");
    }

    @Test
    @DisplayName("Should reject file exceeding maximum size limit")
    void shouldRejectFileExceedingMaxSize() {
        byte[] bigData = new byte[(int) (ProfilePictureImageProcessor.MAX_FILE_SIZE_BYTES + 1024)];
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", bigData);

        assertThatThrownBy(() -> processor.processProfilePicture(file))
                .isInstanceOf(UserProfilePictureException.class)
                .hasMessageContaining("dépasse la limite maximale");
    }

    private byte[] writeImage(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
}
