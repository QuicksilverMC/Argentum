package dev.rdh.argentum.impl.gui;

import net.minecraft.client.Minecraft;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.structure.OptionStorage;

import dev.rdh.argentum.impl.compat.PylonFullscreen;

import java.util.Set;

final class FullscreenOptionStorage<T> implements OptionStorage<T> {
    private final OptionStorage<T> delegate;

    FullscreenOptionStorage(OptionStorage<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T getData() {
        return this.delegate.getData();
    }

    @Override
    public void save() {
        this.save(Set.of());
    }

    @Override
    public void save(Set<OptionFlag> flags) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean fullscreenModeChanged = PylonFullscreen.sync();
        if (minecraft.isFullscreen() != minecraft.options.fullscreen) {
            minecraft.toggleFullscreen();
            minecraft.options.fullscreen = minecraft.isFullscreen();
        } else if (fullscreenModeChanged && minecraft.isFullscreen()) {
            PylonFullscreen.reenter();
        }
        this.delegate.save(flags);
        if (this.delegate != ArgentumOptionPages.VANILLA) minecraft.options.save();
    }
}
