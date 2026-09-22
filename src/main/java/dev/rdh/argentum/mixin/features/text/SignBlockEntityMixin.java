package dev.rdh.argentum.mixin.features.text;

import dev.rdh.argentum.impl.ext.SignTextCache;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;
import java.util.List;

@Mixin(SignBlockEntity.class)
public class SignBlockEntityMixin implements SignTextCache {
    @Unique
    private final Text[] argentum$keys = new Text[4];
    @Unique
    @SuppressWarnings("unchecked")
    private final List<Text>[] argentum$wrapped = new List[4];
    @Unique
    private boolean argentum$unicode;
    @Unique
    private int argentum$cursor;

    @Override
    public List<Text> argentum$getWrappedLine(Text line, boolean unicode) {
        if (unicode != this.argentum$unicode) {
            return null;
        }
        for (int i = 0; i < this.argentum$keys.length; i++) {
            if (this.argentum$keys[i] == line) {
                return this.argentum$wrapped[i];
            }
        }
        return null;
    }

    @Override
    public void argentum$putWrappedLine(Text line, boolean unicode, List<Text> wrapped) {
        // wrapping measures glyphs, so a font switch invalidates everything
        if (unicode != this.argentum$unicode) {
            this.argentum$unicode = unicode;
            Arrays.fill(this.argentum$keys, null);
            Arrays.fill(this.argentum$wrapped, null);
            this.argentum$cursor = 0;
        }
        int slot = this.argentum$cursor;
        this.argentum$keys[slot] = line;
        this.argentum$wrapped[slot] = wrapped;
        this.argentum$cursor = (slot + 1) % this.argentum$keys.length;
    }
}
