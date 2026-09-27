package dev.rdh.argentum.impl.render.terrain.compile;

import dev.rdh.argentum.impl.render.blockentity.BakedItemFrames;
import dev.rdh.argentum.impl.render.blockentity.SlotSheet;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.texture.TextureAtlasSprite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PrimitiveBuiltRenderSectionData extends MinecraftBuiltRenderSectionData<TextureAtlasSprite, BlockEntity> {
    public final Set<SlotSheet.Entry> slots = new ReferenceArraySet<>();
    public final List<BakedItemFrames.Baked> frames = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && this.slots.equals(((PrimitiveBuiltRenderSectionData)o).slots)
                && this.frames.equals(((PrimitiveBuiltRenderSectionData)o).frames);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * super.hashCode() + this.slots.hashCode()) + this.frames.hashCode();
    }
}
