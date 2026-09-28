package dev.rdh.cera.mixin;

import dev.rdh.cera.ext.CeraTextureAtlasExtension;
import dev.rdh.cera.modules.BetterGrass;
import dev.rdh.cera.modules.colors.CustomColormaps;
import dev.rdh.cera.modules.NaturalTextures;
import dev.rdh.cera.modules.ctm.ConnectedTextures;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

@Mixin(TextureAtlas.class)
public class TextureAtlasMixin implements CeraTextureAtlasExtension {
    @Shadow @Final
    private String path;
    @Shadow @Final
    private Map<String, TextureAtlasSprite> sourcedSprites;
    @Unique
    private final BetterGrass cera$betterGrass = new BetterGrass();
    @Unique
    private final ConnectedTextures cera$connectedTextures = new ConnectedTextures();
    @Unique
    private final NaturalTextures cera$naturalTextures = new NaturalTextures();
    @Unique
    private final CustomColormaps cera$customColormaps = new CustomColormaps();
    @Unique
    private final Set<String> cera$registeredSprites = new ObjectOpenHashSet<>();

    @Override
    public BetterGrass cera$getBetterGrass() {
        return this.cera$betterGrass;
    }

    @Override
    public CustomColormaps cera$getCustomColormaps() {
        return this.cera$customColormaps;
    }

    @Override
    public ConnectedTextures cera$getConnectedTextures() {
        return this.cera$connectedTextures;
    }

    @Override
    public NaturalTextures cera$getNaturalTextures() {
        return this.cera$naturalTextures;
    }

    @Inject(method = "loadAndStitch", at = @At("HEAD"))
    private void cera$loadTextureModules(ResourceManager resources, CallbackInfo ci) {
        if ("textures".equals(this.path)) {
            this.cera$registeredSprites.forEach(this.sourcedSprites::remove);
            this.cera$registeredSprites.clear();
            var known = new ObjectOpenHashSet<>(this.sourcedSprites.keySet());

            var oslResources = net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager.client();
            TextureAtlas thiz = (TextureAtlas) (Object) this;
            this.cera$betterGrass.reload(oslResources, thiz, this.sourcedSprites);
            this.cera$connectedTextures.reload(oslResources, thiz, this.sourcedSprites);
            Minecraft.getInstance().getTextureManager().cera$getEmissiveTextures().reload(oslResources, thiz, this.sourcedSprites);

            this.cera$registeredSprites.addAll(this.sourcedSprites.keySet());
            this.cera$registeredSprites.removeAll(known);
        }
    }

    @Inject(method = "loadAndStitch", at = @At("RETURN"))
    private void cera$bakeTextureModules(ResourceManager resources, CallbackInfo ci) {
        if ("textures".equals(this.path)) {
            this.cera$betterGrass.bake();
            this.cera$connectedTextures.bake();
            this.cera$naturalTextures.bake((TextureAtlas) (Object) this);
        }
    }

    @ModifyReturnValue(method = "getResourceId", at = @At("RETURN"))
    private Identifier cera$resolveConnectedTexture(Identifier original, Identifier texture, int mipmap) {
        String path = texture.getPath();
        if (!path.startsWith("optifine/") && !path.startsWith("mcpatcher/")) return original;
        return new Identifier(texture.getNamespace(), mipmap == 0 ? path + ".png" : path + "mipmap" + mipmap + ".png");
    }
}
