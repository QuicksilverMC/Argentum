package dev.rdh.argentum.impl.compat;

import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.linux.LinuxLibrary;
import org.lwjgl.system.windows.DISPLAY_DEVICE;
import org.lwjgl.system.windows.Kernel32;
import org.lwjgl.system.windows.User32;

import dev.rdh.argentum.impl.Argentum;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class NvidiaWorkarounds {
    private static final String WINDOWS_COMMAND_LINE = "dev.rdh.argentum";

    private static String originalCommandLine;
    private static ByteBuffer commandLineBuffer;

    private NvidiaWorkarounds() {
    }

    public static void install() {
        if (!Boolean.parseBoolean(System.getProperty("argentum.nvidiaWorkarounds", "true"))) {
            return;
        }

        try {
            switch (Platform.get()) {
                case WINDOWS -> {
                    if (hasNvidiaAdapterWindows()) {
                        Argentum.LOGGER.warn("Applying workaround: hiding Minecraft from the NVIDIA driver so it does not enable Threaded Optimization");
                        setWindowsCommandLine(WINDOWS_COMMAND_LINE);
                        setWindowsEnvironmentVariable("SHIM_MCCOMPAT", "0x800000001");
                    }
                }
                case LINUX -> {
                    if (Files.exists(Path.of("/proc/driver/nvidia/version"))) {
                        Argentum.LOGGER.warn("Applying workaround: setting __GL_THREADED_OPTIMIZATIONS=0 for the NVIDIA driver");
                        setLinuxEnvironmentVariable("__GL_THREADED_OPTIMIZATIONS", "0");
                    }
                }
                default -> {}
            }
        } catch (Throwable t) {
            Argentum.LOGGER.error("Failed to apply the NVIDIA driver workaround; expect frame time spikes or crashes on this driver", t);
        }
    }

    public static void uninstall() {
        if (commandLineBuffer == null) {
            return;
        }

        MemoryUtil.memUTF16(originalCommandLine, true, commandLineBuffer);
        originalCommandLine = null;
        commandLineBuffer = null;
    }

    private static boolean hasNvidiaAdapterWindows() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DISPLAY_DEVICE device = DISPLAY_DEVICE.calloc(stack).cb(DISPLAY_DEVICE.SIZEOF);

            for (int i = 0; User32.nEnumDisplayDevices(0L, i, device.address(), 0) != 0; i++) {
                if (device.DeviceIDString().toUpperCase(Locale.ROOT).contains("VEN_10DE")
                        || device.DeviceStringString().toUpperCase(Locale.ROOT).contains("NVIDIA")) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void setWindowsCommandLine(String replacement) {
        if (commandLineBuffer != null) {
            throw new IllegalStateException("Command line is already modified");
        }

        long pfnGetCommandLineW = Kernel32.getLibrary().getFunctionAddress("GetCommandLineW");

        long pCommandLine = JNI.invokeP(pfnGetCommandLineW);
        String commandLine = MemoryUtil.memUTF16(pCommandLine);
        int commandLineLength = MemoryUtil.memLengthUTF16(commandLine, true);

        if (MemoryUtil.memLengthUTF16(replacement, true) > commandLineLength) {
            throw new BufferOverflowException();
        }

        ByteBuffer buffer = MemoryUtil.memByteBuffer(pCommandLine, commandLineLength);
        MemoryUtil.memUTF16(replacement, true, buffer);

        if (!replacement.equals(MemoryUtil.memUTF16(pCommandLine))) {
            throw new IllegalStateException("Sanity check failed, the command line did not change");
        }

        originalCommandLine = commandLine;
        commandLineBuffer = buffer;
    }

    private static void setWindowsEnvironmentVariable(String name, String value) {
        long pfnSetEnvironmentVariableW = Kernel32.getLibrary().getFunctionAddress("SetEnvironmentVariableW");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (JNI.invokePPI(MemoryUtil.memAddress(stack.UTF16(name)), MemoryUtil.memAddress(stack.UTF16(value)), pfnSetEnvironmentVariableW) == 0) {
                throw new IllegalStateException("SetEnvironmentVariableW failed for " + name);
            }
        }
    }

    private static void setLinuxEnvironmentVariable(String name, String value) {
        long pfnSetenv = new LinuxLibrary("libc.so.6").getFunctionAddress("setenv");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (JNI.invokePPI(MemoryUtil.memAddress(stack.UTF8(name)), MemoryUtil.memAddress(stack.UTF8(value)), 1, pfnSetenv) != 0) {
                throw new IllegalStateException("setenv failed for " + name);
            }
        }
    }
}
