package dev.rdh.cera.mixin;

import dev.rdh.cera.ext.CeraItemStackExtension;
import dev.rdh.cera.modules.cit.CustomItems;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemStack.class)
public class ItemStackMixin implements CeraItemStackExtension {
    @Unique
    private CustomItems.StackCache cera$citCache;

    @Override
    public CustomItems.StackCache cera$getCitCache() {
        return this.cera$citCache;
    }

    @Override
    public void cera$setCitCache(CustomItems.StackCache cache) {
        this.cera$citCache = cache;
    }
}
