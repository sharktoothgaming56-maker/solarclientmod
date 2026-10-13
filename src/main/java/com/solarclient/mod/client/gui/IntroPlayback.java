package com.solarclient.mod.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SolarClient loading intro.
 *
 * Frames decode on their own thread into a short queue; the render thread
 * only uploads the frame that is due into a single dynamic GPU texture.
 *
 * Playback follows the wall clock. On a slow PC that can't decode 15
 * frames a second while the game is also loading its resources, frames
 * that are already late are skipped (not even decoded) so the animation
 * keeps moving at its real speed instead of freezing. The last frame, the
 * end card, is never skipped.
 *
 * The decoder deliberately does NOT use Minecraft's worker pool: during
 * startup that pool is saturated by resource loading, and the intro would
 * sit in line behind it on a static background.
 */
public final class IntroPlayback {
    public static final int FRAME_COUNT = 102;
    public static final float FPS = 15f;
    public static final Identifier MENU_BG = Identifier.of("solarclient", "textures/intro/menu_bg.png");
    public static final Identifier LOGO = Identifier.of("solarclient", "textures/intro/logo.png");
    public static final Identifier VIDEO_ID = Identifier.of("solarclient", "intro_video_dynamic");

    public static final int LOGO_W = 942, LOGO_H = 258;
    public static final int FRAME_W = 1280, FRAME_H = 720;

    /** End-card lettering box inside FRAME_W×FRAME_H (meta.txt). */
    public static final int ENDCARD_X0 = 315, ENDCARD_Y0 = 271;
    public static final int ENDCARD_X1 = 968, ENDCARD_Y1 = 447;

    private static final int PREFETCH = 6;
    // Don't hold the first frame back forever waiting for a full buffer.
    private static final long PREFETCH_MAX_WAIT_NS = 1_500_000_000L;
    private static final long FRAME_DURATION_NS = (long) (1_000_000_000L / FPS);

    private record Frame(int index, NativeImage image) {}

    private static final BlockingQueue<Frame> QUEUE = new ArrayBlockingQueue<>(20);
    private static final AtomicBoolean producerStarted = new AtomicBoolean(false);
    private static volatile boolean producerDone;
    private static volatile boolean producerFailed;

    private static NativeImageBackedTexture videoTex;
    private static boolean videoFinished;
    private static boolean sessionIntroHandled;
    /** True after the first-boot SplashOverlay has used our intro once.
     *  Later resource-pack reloads keep Mojang's vanilla splash. */
    private static boolean startupSplashIntroDone;
    // Read by the decoder thread to know which frames are already late.
    private static volatile long videoStartNs = -1L;
    private static long prefetchStartNs = -1L;
    private static int displayedFrame = -1;

    private IntroPlayback() {}

    public static boolean hasPlayedThisSession() {
        return sessionIntroHandled;
    }

    public static void markSessionHandled() {
        sessionIntroHandled = true;
    }

    /** Whether the next SplashOverlay should play our intro (first boot only). */
    public static boolean shouldReplaceSplashWithIntro() {
        return !startupSplashIntroDone && !com.solarclient.mod.client.Preload.SESSION;
    }

    public static void markStartupSplashIntroDone() {
        startupSplashIntroDone = true;
    }

    public static boolean isVideoFinished() {
        return videoFinished;
    }

    public static void begin(MinecraftClient client) {
        // Never restart the decode pipeline for mid-session resource reloads.
        if (startupSplashIntroDone) return;
        resetPlaybackState();
        if (client != null) {
            client.getTextureManager().getTexture(MENU_BG);
            client.getTextureManager().getTexture(LOGO);
            ensureVideoTexture(client);
        }
        startProducer();
    }

    /**
     * Faster launch: plays the intro again as if the game had just started,
     * then lands on the title screen with its logo animation.
     */
    public static void replay(MinecraftClient client) {
        resetPlaybackState();
        sessionIntroHandled = false;
        client.getTextureManager().getTexture(MENU_BG);
        client.getTextureManager().getTexture(LOGO);
        ensureVideoTexture(client);
        startProducer();
        client.setOverlay(new IntroOverlay(client));
    }

    private static void resetPlaybackState() {
        videoStartNs = -1L;
        prefetchStartNs = -1L;
        videoFinished = false;
        displayedFrame = -1;
        releaseRemaining();
        producerDone = false;
        producerFailed = false;
        producerStarted.set(false);
    }

    /** Index of the frame the clock says should be on screen now, or -1 before playback starts. */
    private static long dueFrame(long now) {
        long start = videoStartNs;
        return start < 0 ? -1L : (now - start) / FRAME_DURATION_NS;
    }

    private static void startProducer() {
        if (!producerStarted.compareAndSet(false, true)) return;
        Thread decoder = new Thread(() -> {
            try {
                ClassLoader cl = IntroPlayback.class.getClassLoader();
                for (int i = 0; i < FRAME_COUNT; i++) {
                    if (i < FRAME_COUNT - 1 && i < dueFrame(System.nanoTime())) continue;
                    String path = String.format("assets/solarclient/textures/intro/frame_%03d.png", i);
                    try (InputStream in = cl.getResourceAsStream(path)) {
                        if (in == null) throw new IllegalStateException("missing " + path);
                        QUEUE.put(new Frame(i, NativeImage.read(in)));
                    }
                }
                producerDone = true;
            } catch (Exception e) {
                producerFailed = true;
                producerDone = true;
                e.printStackTrace();
            }
        }, "SolarClient intro decoder");
        decoder.setDaemon(true);
        decoder.start();
    }

    private static void ensureVideoTexture(MinecraftClient client) {
        if (videoTex != null) return;
        NativeImage placeholder = new NativeImage(FRAME_W, FRAME_H, false);
        videoTex = new NativeImageBackedTexture(() -> "solarclient/intro_video", placeholder);
        client.getTextureManager().registerTexture(VIDEO_ID, videoTex);
    }

    private static int pumpFrame(MinecraftClient client) {
        ensureVideoTexture(client);
        long now = System.nanoTime();

        if (displayedFrame < 0) {
            // Wait for a small buffer so the first seconds don't stutter.
            if (prefetchStartNs < 0) prefetchStartNs = now;
            boolean ready = QUEUE.size() >= PREFETCH || producerDone || now - prefetchStartNs > PREFETCH_MAX_WAIT_NS;
            if (!ready) return -1;
            Frame first = QUEUE.poll();
            if (first == null) return -1;
            show(first);
            videoStartNs = now - (long) first.index() * FRAME_DURATION_NS;
            return displayedFrame;
        }

        if (displayedFrame >= FRAME_COUNT - 1) {
            videoFinished = true;
            return displayedFrame;
        }

        // Show the newest frame that is due; anything older is already late.
        long due = Math.min(FRAME_COUNT - 1, dueFrame(now));
        Frame pick = null;
        Frame next;
        while ((next = QUEUE.peek()) != null && next.index() <= due) {
            QUEUE.poll();
            if (pick != null) pick.image().close();
            pick = next;
        }
        if (pick != null) show(pick);

        if (displayedFrame >= FRAME_COUNT - 1 || (producerDone && QUEUE.isEmpty() && due >= FRAME_COUNT - 1)) {
            videoFinished = true;
            releaseRemaining();
        }
        return displayedFrame;
    }

    private static void show(Frame frame) {
        NativeImage src = frame.image();
        NativeImage dst = videoTex.getImage();
        if (dst != null && dst.getWidth() == src.getWidth() && dst.getHeight() == src.getHeight()) {
            // Hands the decoded image to the texture (the old one is closed), no copy.
            videoTex.setImage(src);
            videoTex.upload();
        } else {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) client.getTextureManager().destroyTexture(VIDEO_ID);
            videoTex = new NativeImageBackedTexture(() -> "solarclient/intro_video", src);
            if (client != null) client.getTextureManager().registerTexture(VIDEO_ID, videoTex);
        }
        displayedFrame = frame.index();
    }

    private static void releaseRemaining() {
        Frame frame;
        while ((frame = QUEUE.poll()) != null) {
            frame.image().close();
        }
    }

    public static void renderVideoFrame(DrawContext ctx, int screenW, int screenH) {
        MinecraftClient client = MinecraftClient.getInstance();
        int frame = pumpFrame(client);
        if (frame < 0) {
            // Prefetching — show the real space BG so it never feels frozen black.
            drawCover(ctx, MENU_BG, FRAME_W, FRAME_H, screenW, screenH);
            return;
        }
        drawCover(ctx, VIDEO_ID, FRAME_W, FRAME_H, screenW, screenH);
        if (producerFailed && displayedFrame >= 0) {
            videoFinished = producerDone;
        }
    }

    public static void renderMenuBackground(DrawContext ctx, int screenW, int screenH) {
        drawCover(ctx, MENU_BG, FRAME_W, FRAME_H, screenW, screenH);
    }

    public static void drawLogo(DrawContext ctx, int cx, int top, int targetWidth) {
        int w = Math.max(2, targetWidth & ~1);
        SolarLogos.drawCentered(ctx, LOGO, LOGO_W, LOGO_H, cx, top, w);
    }

    public static CoverMapping coverMapping(int screenW, int screenH) {
        float scale = Math.max(screenW / (float) FRAME_W, screenH / (float) FRAME_H);
        int dw = Math.max(1, Math.round(FRAME_W * scale));
        int dh = Math.max(1, Math.round(FRAME_H * scale));
        int x = (screenW - dw) / 2;
        int y = (screenH - dh) / 2;
        return new CoverMapping(scale, x, y, dw, dh);
    }

    public static EndcardLogoPlacement endcardLogoOnScreen(int screenW, int screenH) {
        CoverMapping m = coverMapping(screenW, screenH);
        int boxW = Math.max(1, Math.round((ENDCARD_X1 - ENDCARD_X0) * m.scale));
        int boxH = Math.max(1, Math.round((ENDCARD_Y1 - ENDCARD_Y0) * m.scale));
        int boxY = m.y + Math.round(ENDCARD_Y0 * m.scale);
        float logoAspect = LOGO_W / (float) LOGO_H;
        float boxAspect = boxW / (float) boxH;
        int logoW;
        int logoH;
        if (logoAspect >= boxAspect) {
            logoW = boxW;
            logoH = Math.max(1, Math.round(boxW / logoAspect));
        } else {
            logoH = boxH;
            logoW = Math.max(1, Math.round(boxH * logoAspect));
        }
        logoW = Math.max(2, logoW & ~1);
        logoH = Math.max(1, (int) ((long) logoW * LOGO_H / LOGO_W));
        int logoTop = boxY + (boxH - logoH) / 2;
        return new EndcardLogoPlacement(screenW / 2, logoTop, logoW, logoH);
    }

    public record CoverMapping(float scale, int x, int y, int dw, int dh) {}

    public record EndcardLogoPlacement(int cx, int top, int width, int height) {}

    public static void drawCover(DrawContext ctx, Identifier tex, int srcW, int srcH, int screenW, int screenH) {
        CoverMapping m = coverMapping(screenW, screenH);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex,
                m.x, m.y, 0f, 0f,
                m.dw, m.dh,
                srcW, srcH,
                srcW, srcH,
                0xFFFFFFFF);
    }

    public static float easeInOut(float t) {
        t = clamp01(t);
        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    public static float easeOut(float t) {
        t = clamp01(t);
        float u = 1f - t;
        return 1f - u * u * u * u * u;
    }

    public static float clamp01(float t) {
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
