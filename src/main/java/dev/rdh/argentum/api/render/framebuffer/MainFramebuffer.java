package dev.rdh.argentum.api.render.framebuffer;

import net.minecraft.client.Minecraft;

/**
 * With direct rendering on, Argentum draws most frames straight to the window instead of Minecraft's main render
 * target, so the main render target doesn't hold that frame's image. Code that reads the main render target,
 * such as screenshot tools, should use these methods to remain compatible. All methods must be called
 * on the render thread only.
 */
public final class MainFramebuffer {
    private MainFramebuffer() {
    }

    /**
     * @return whether the frame being rendered skips the main render target
     */
    public static boolean isRenderingDirect() {
        return Minecraft.getInstance().argentum$isRenderingDirect();
    }

    /**
     * Makes only the next frame render into the main render target.
     */
    public static void request() {
        Minecraft.getInstance().argentum$requestFramebuffer();
    }

    /**
     * Runs a task once the next frame that renders into the main render target has finished rendering, before it
     * is shown. Queuing a task also makes that next frame render into the main render target. The task runs with
     * the default framebuffer bound and the finished frame in the main render target.
     */
    public static void runAfterFramebufferFrame(Runnable task) {
        Minecraft.getInstance().argentum$runAfterFramebufferFrame(task);
    }

}
