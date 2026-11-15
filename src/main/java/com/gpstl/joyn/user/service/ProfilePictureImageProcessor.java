package com.gpstl.joyn.user.service;

import com.gpstl.joyn.user.exception.UserProfilePictureException;
import com.luciad.imageio.webp.WebPWriteParam;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Set;

/**
 * Service de traitement des images de profil utilisateur.
 * <p>
 * Étapes appliquées :
 * <ol>
 *   <li>Vérification que le fichier est réellement une image (types MIME raster acceptés, décodage valide).</li>
 *   <li>Validation du poids maximal (5 Mo) et des dimensions (minimum 32x32, maximum 8192x8192).</li>
 *   <li>Recadrage centré au ratio carré (1:1) et redimensionnement haute qualité en 512 × 512 px.</li>
 *   <li>Conversion au format WebP avec 80 % de qualité pour un poids optimisé (cible 30 à 100 Ko).</li>
 * </ol>
 */
@Component
public class ProfilePictureImageProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProfilePictureImageProcessor.class);

    public static final int TARGET_WIDTH = 512;
    public static final int TARGET_HEIGHT = 512;
    public static final float WEBP_QUALITY = 0.80f;

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5 Mo
    public static final int MIN_DIMENSION = 32;
    public static final int MAX_DIMENSION = 8192;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/bmp",
            "image/x-ms-bmp"
    );

    private final Tika fileTypeDetector = new Tika();

    /**
     * Traite un fichier image uploadé : validation, recadrage centré 1:1,
     * redimensionnement en 512x512 et conversion en WebP 80%.
     *
     * @param file fichier uploadé par le client
     * @return tableau d'octets de l'image optimisée au format WebP
     * @throws UserProfilePictureException si le fichier n'est pas une image valide ou dépasse les limites
     */
    public byte[] processProfilePicture(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UserProfilePictureException(400, "Un fichier image est requis.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new UserProfilePictureException(400,
                    "La taille de l'image dépasse la limite maximale autorisée de 5 Mo.");
        }

        byte[] inputBytes;
        try {
            inputBytes = file.getBytes();
        } catch (IOException e) {
            throw new UserProfilePictureException(400, "Impossible de lire le fichier uploadé.", e);
        }

        // 1. Vérification du type MIME réel via inspection des magic bytes
        String detectedMimeType;
        try (var mimeInput = new ByteArrayInputStream(inputBytes)) {
            detectedMimeType = fileTypeDetector.detect(mimeInput);
        } catch (IOException e) {
            throw new UserProfilePictureException(400, "Erreur lors de l'analyse du format du fichier.", e);
        }

        if (detectedMimeType == null || !ALLOWED_MIME_TYPES.contains(detectedMimeType.toLowerCase())) {
            throw new UserProfilePictureException(400,
                    "Format d'image non supporté (" + detectedMimeType + "). Formats acceptés : JPEG, PNG, WebP, GIF, BMP.");
        }

        // Décodage de l'image en mémoire
        BufferedImage originalImage;
        try (var imageInput = new ByteArrayInputStream(inputBytes)) {
            originalImage = ImageIO.read(imageInput);
        } catch (IOException e) {
            throw new UserProfilePictureException(400, "Impossible de décoder le flux d'image.", e);
        }

        if (originalImage == null) {
            throw new UserProfilePictureException(400,
                    "Le fichier n'est pas une image valide ou est corrompu.");
        }

        // 2. Vérification des dimensions
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        if (originalWidth < MIN_DIMENSION || originalHeight < MIN_DIMENSION) {
            throw new UserProfilePictureException(400,
                    "Dimensions de l'image trop petites (" + originalWidth + "x" + originalHeight
                            + "px). Minimum requis : " + MIN_DIMENSION + "x" + MIN_DIMENSION + "px.");
        }

        if (originalWidth > MAX_DIMENSION || originalHeight > MAX_DIMENSION) {
            throw new UserProfilePictureException(400,
                    "Dimensions de l'image trop grandes (" + originalWidth + "x" + originalHeight
                            + "px). Maximum autorisé : " + MAX_DIMENSION + "x" + MAX_DIMENSION + "px.");
        }

        // 3. Recadrage centré pour garantir un ratio carré parfait 1:1
        int squareSize = Math.min(originalWidth, originalHeight);
        int cropX = (originalWidth - squareSize) / 2;
        int cropY = (originalHeight - squareSize) / 2;
        BufferedImage croppedSquare = originalImage.getSubimage(cropX, cropY, squareSize, squareSize);

        // 4. Redimensionnement en 512 × 512 avec interpolation bicubique haute qualité
        boolean hasAlpha = croppedSquare.getColorModel().hasAlpha();
        int targetImageType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resizedImage = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, targetImageType);

        Graphics2D graphics = resizedImage.createGraphics();
        try {
            if (!hasAlpha) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, TARGET_WIDTH, TARGET_HEIGHT);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(croppedSquare, 0, 0, TARGET_WIDTH, TARGET_HEIGHT, null);
        } finally {
            graphics.dispose();
        }

        // 5. Encodage WebP avec 80 % de qualité
        byte[] webpBytes = encodeToWebP(resizedImage, WEBP_QUALITY);
        log.debug("Image de profil traitée avec succès : taille finale {} octets ({:.1f} Ko)",
                webpBytes.length, webpBytes.length / 1024.0);
        return webpBytes;
    }

    private byte[] encodeToWebP(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            throw new IllegalStateException("Aucun encodeur WebP disponible dans le classpath.");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (var imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            WebPWriteParam writeParam = new WebPWriteParam(writer.getLocale());
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionType(writeParam.getCompressionTypes()[WebPWriteParam.LOSSY_COMPRESSION]);
            writeParam.setCompressionQuality(quality);

            writer.write(null, new IIOImage(image, null, null), writeParam);
        } catch (IOException e) {
            throw new IllegalStateException("Erreur lors de l'encodage de l'image en WebP.", e);
        } finally {
            writer.dispose();
        }

        return outputStream.toByteArray();
    }
}
