package com.minecraftclone.engine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/** Captures the current framebuffer to a PNG file (F2 in-game, or used by automated smoke tests). */
public final class Screenshot {

    private Screenshot() {
    }

    public static void capture(int width, int height, String path) {
        ByteBuffer buffer = memAlloc(width * height * 3);
        // Read the back buffer: this must be called after rendering the frame
        // but before swapBuffers(), since front-buffer reads are unreliable
        // under some (e.g. headless/software) GL drivers.
        glReadBuffer(GL_BACK);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, buffer);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (x + (height - y - 1) * width) * 3;
                int r = buffer.get(i) & 0xFF;
                int g = buffer.get(i + 1) & 0xFF;
                int b = buffer.get(i + 2) & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        memFree(buffer);

        try {
            File file = new File(path);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            ImageIO.write(image, "png", file);
            System.out.println("Saved screenshot to " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save screenshot: " + e.getMessage());
        }
    }
}
