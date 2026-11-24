package dev.rdh.cera.mixin.shaders;

import com.ventooth.swansong.StackStateTracker;
import com.ventooth.swansong.shader.StateGraph;
import com.ventooth.swansong.shader.StateGraph.Node;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = StateGraph.class, remap = false)
public interface StateGraphAccessor {
    @Accessor("current")
    void cera$setCurrent(Node node);

    @Mutable
    @Accessor("stack")
    void cera$setStack(StackStateTracker<Void> stack);
}
