package com.gpstl.joyn.company.service;

import com.gpstl.joyn.company.exception.CompanyLogoException;
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
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests unitaires du traitement des logos (R84) : pas de Spring, pas de base. */
class CompanyLogoImageProcessorTest {

    private final CompanyLogoImageProcessor processor = new CompanyLogoImageProcessor();

    @Test
    @DisplayName("Un logo large garde ses proportions : 800x400 devient 512x256, en WebP")
    void keepsAspectRatioAndConvertsToWebp() throws IOException {
        byte[] webp = processor.processLogo(png("logo.png", 800, 400, BufferedImage.TYPE_INT_RGB));

        assertThat(new String(webp, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(webp, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(webp));
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(256);
    }

    @Test
    @DisplayName("Un logo haut garde aussi ses proportions : 300x900 devient 171x512")
    void keepsAspectRatioForTallLogo() throws IOException {
        byte[] webp = processor.processLogo(png("tall.png", 300, 900, BufferedImage.TYPE_INT_RGB));

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(webp));
        assertThat(decoded.getWidth()).isEqualTo(171);
        assertThat(decoded.getHeight()).isEqualTo(512);
    }

    @Test
    @DisplayName("Un petit logo n'est jamais agrandi")
    void neverUpscalesSmallLogo() throws IOException {
        byte[] webp = processor.processLogo(png("small.png", 120, 60, BufferedImage.TYPE_INT_ARGB));

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(webp));
        assertThat(decoded.getWidth()).isEqualTo(120);
        assertThat(decoded.getHeight()).isEqualTo(60);
    }

    @Test
    @DisplayName("Un SVG est refusé (il peut contenir du JavaScript)")
    void rejectsSvg() {
        String svg = """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
                  <script>alert('x')</script><rect width="100" height="100"/>
                </svg>""";
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png",
                svg.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> processor.processLogo(file))
                .isInstanceOf(CompanyLogoException.class)
                .hasMessageContaining("SVG")
                .satisfies(e -> assertThat(((CompanyLogoException) e).getStatus()).isEqualTo(400));
    }

    @Test
    @DisplayName("Un fichier qui n'est pas une image est refusé, même nommé .png")
    void rejectsNonImageEvenWithPngName() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png",
                "ceci n'est pas une image".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> processor.processLogo(file))
                .isInstanceOf(CompanyLogoException.class)
                .hasMessageContaining("Format d'image non supporté");
    }

    @Test
    @DisplayName("Un logo de moins de 32 px est refusé")
    void rejectsTooSmallLogo() throws IOException {
        MockMultipartFile file = png("tiny.png", 20, 20, BufferedImage.TYPE_INT_RGB);

        assertThatThrownBy(() -> processor.processLogo(file))
                .isInstanceOf(CompanyLogoException.class)
                .hasMessageContaining("trop petit");
    }

    @Test
    @DisplayName("Un fichier vide est refusé")
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> processor.processLogo(file))
                .isInstanceOf(CompanyLogoException.class)
                .hasMessageContaining("requis");
    }

    // ------------------------------------------------------------------ outils

    private static MockMultipartFile png(String name, int width, int height, int type) throws IOException {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, width / 2, height / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", name, "image/png", out.toByteArray());
    }
}
