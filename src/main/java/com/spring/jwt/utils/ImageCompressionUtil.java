package com.spring.jwt.utils;

import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import java.util.concurrent.*;

public class ImageCompressionUtil
{

    private static final int TARGET_WIDTH = 1000;
    private static final int TARGET_HEIGHT = 1000;
    private static final long TARGET_SIZE_KB = 100;

    private static final float MIN_QUALITY = 0.1f;
    private static final float MAX_QUALITY = 0.95f;

    // Dedicated executor (avoid common pool for heavy tasks)
    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private ImageCompressionUtil() {}

    public static byte[] compressImage(byte[] originalBytes) throws IOException
    {
        if (originalBytes == null || originalBytes.length == 0) return null;

        if (originalBytes.length / 1024 <= TARGET_SIZE_KB)
        {
            return originalBytes;
        }

        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (originalImage == null) {
            throw new IllegalArgumentException("Invalid image data");
        }

        // Resize with aspect ratio preserved
        BufferedImage resizedImage = resizeImage(originalImage);

        // Detect format (default to jpg)
        String format = detectFormat(originalBytes);
        if (!format.equalsIgnoreCase("jpg") && !format.equalsIgnoreCase("jpeg"))
        {
            format = "jpg"; // normalize for compression
        }

        return compressWithBinarySearch(resizedImage, format, TARGET_SIZE_KB);
    }

    private static BufferedImage resizeImage(BufferedImage originalImage) throws IOException
    {
        return Thumbnails.of(originalImage)
                .size(TARGET_WIDTH, TARGET_HEIGHT)
                .keepAspectRatio(true)
                .asBufferedImage();
    }

    private static byte[] compressWithBinarySearch(BufferedImage image, String format, long targetKB)
            throws IOException
    {

        float low = MIN_QUALITY;
        float high = MAX_QUALITY;
        byte[] bestResult = null;

        for (int i = 0; i < 7; i++)
        {
            float mid = (low + high) / 2;

            byte[] compressed = compress(image, format, mid);
            double sizeKB = compressed.length / 1024.0;

            if (sizeKB <= targetKB)
            {
                bestResult = compressed;
                low = mid; // try higher quality
            } else
            {
                high = mid; // reduce quality
            }
        }

        return bestResult != null ? bestResult : compress(image, format, MIN_QUALITY);
    }

    private static byte[] compress(BufferedImage image, String format, float quality)
            throws IOException
    {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext())
        {
            throw new IllegalStateException("No writers found for format: " + format);
        }

        ImageWriter writer = writers.next();

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos))
        {
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();

            if (param.canWriteCompressed())
            {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }

            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }

    private static String detectFormat(byte[] imageBytes) throws IOException
    {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes)))
        {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext())
            {
                return readers.next().getFormatName();
            }
        }
        return "jpg"; // fallback
    }

    public static CompletableFuture<byte[]> compressImageAsync(byte[] originalBytes)
    {
        return CompletableFuture.supplyAsync(() -> {
            try
            {
                return compressImage(originalBytes);
            } catch (IOException e)
            {
                throw new RuntimeException("Image compression failed", e);
            }
        }, EXECUTOR);
    }

    public static void shutdown()
    {
        EXECUTOR.shutdown();
    }
}
