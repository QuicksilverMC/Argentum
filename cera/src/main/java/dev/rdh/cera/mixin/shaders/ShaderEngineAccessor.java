package dev.rdh.cera.mixin.shaders;

import com.ventooth.swansong.shader.ShaderEngine;
import com.ventooth.swansong.shader.shaderobjects.ManagedShader;
import it.unimi.dsi.fastutil.objects.AbstractObjectList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ShaderEngine.class, remap = false)
public interface ShaderEngineAccessor {
    @Accessor("shaderStack")
    AbstractObjectList<ManagedShader> cera$getShaderStack();
}
