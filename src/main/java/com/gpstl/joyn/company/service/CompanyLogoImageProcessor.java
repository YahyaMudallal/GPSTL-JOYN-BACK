package com.gpstl.joyn.company.service;

import com.gpstl.joyn.company.exception.CompanyLogoException;
import com.luciad.imageio.webp.WebPWriteParam;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Prépare un logo d'entreprise avant de le stocker (R84).
 *
 * Même contrôles que pour les photos de profil (U86), avec une différence importante :
 * un logo n'est PAS recadré en carré (on couperait le nom de l'entreprise sur un logo large).
 * On garde ses proportions, et le plus grand côté est réduit à 512 px au maximum.
 *
 * Étapes :
 * <ol>
 *   <li>vrai type du fichier vérifié (pas seulement l'extension) : JPEG, PNG, WebP, GIF ou BMP ;</li>
 *   <li>SVG refusé : un SVG peut contenir du code (JavaScript), ce qui est un risque de sécurité ;</li>
 *   <li>5 Mo maximum, dimensions entre 32 et 8192 px ;</li>
 *   <li>réduction à 512 px maximum (jamais agrandi), transparence conservée ;</li>
 *   <li>conversion en WebP 80 % (fichier léger).</li>
 * </ol>
 */
@Component
public class CompanyLogoImageProcessor {

    public static final int MAX_SIDE = 512;
    public static final float WEBP_QUALITY = 0.80f;
    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5 Mo
    public static final int MIN_DIMENSION = 32;
    public static final int MAX_DIMENSION = 8192;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp", "image/x-ms-bmp");

    private final Tika fileTypeDetector = new Tika();

    /** Valide le fichier et renvoie le logo prêt à stocker, en WebP. */
    public byte[] processLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CompanyLogoException(400, "Un fichier image est requis.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new CompanyLogoException(400, "Le logo dépasse la taille maximale autorisée de 5 Mo.");
        }

        byte[] inputBytes;
        try {
            inputBytes = file.getBytes();
        } catch (IOException e) {
            throw new CompanyLogoException(400, "Impossible de lire le fichier envoyé.", e);
        }

        // 1. Vrai type du fichier, d'après son contenu (et pas son nom)
        String detectedMimeType;
        try (var input = new ByteArrayInputStream(inputBytes)) {
            detectedMimeType = fileTypeDetector.detect(input);
        } catch (IOException e) {
            throw new CompanyLogoException(400, "Erreur lors de l'analyse du format du fichier.", e);
        }
        String mime = detectedMimeType == null ? "" : detectedMimeType.toLowerCase(Locale.ROOT);
        if (mime.contains("svg")) {
            throw new CompanyLogoException(400,
                    "Les logos SVG ne sont pas acceptés pour des raisons de sécurité. Formats acceptés : PNG, JPEG, WebP, GIF, BMP.");
        }
        if (!ALLOWED_MIME_TYPES.contains(mime)) {
            throw new CompanyLogoException(400,
                    "Format d'image non supporté (" + detectedMimeType + "). Formats acceptés : PNG, JPEG, WebP, GIF, BMP.");
        }

        BufferedImage original;
        try (var input = new ByteArrayInputStream(inputBytes)) {
            original = ImageIO.read(input);
        } catch (IOException e) {
            throw new CompanyLogoException(400, "Impossible de décoder l'image.", e);
        }
        if (original == null) {
            throw new CompanyLogoException(400, "Le fichier n'est pas une image valide ou est corrompu.");
        }

        // 2. Dimensions
        int width = original.getWidth();
        int height = original.getHeight();
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
            throw new CompanyLogoException(400, "Logo trop petit (" + width + "x" + height
                    + " px). Minimum : " + MIN_DIMENSION + "x" + MIN_DIMENSION + " px.");
        }
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new CompanyLogoException(400, "Logo trop grand (" + width + "x" + height
                    + " px). Maximum : " + MAX_DIMENSION + "x" + MAX_DIMENSION + " px.");
        }

        // 3. Réduction en gardant les proportions (jamais d'agrandissement)
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        boolean hasAlpha = original.getColorModel().hasAlpha();
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight,
                hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            if (!hasAlpha) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        // 4. WebP 80 %
        return encodeToWebP(resized);
    }

    /** Même encodage que ProfilePictureImageProcessor (U86). */
    private static byte[] encodeToWebP(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            throw new IllegalStateException("Aucun encodeur WebP disponible dans le classpath.");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            WebPWriteParam param = new WebPWriteParam(writer.getLocale());
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType(param.getCompressionTypes()[WebPWriteParam.LOSSY_COMPRESSION]);
            param.setCompressionQuality(WEBP_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Erreur lors de l'encodage du logo en WebP.", e);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }
}
