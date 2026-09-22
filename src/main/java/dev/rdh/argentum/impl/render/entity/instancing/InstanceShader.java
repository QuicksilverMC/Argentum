package dev.rdh.argentum.impl.render.entity.instancing;

import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat3v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformInt;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderComponent;

import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class InstanceShader {
    /** Every texel, the way an alpha-tested pass draws. */
    static final int ALPHA_ALL = 0;
    /** Only texels the model can be seen through - drawn blended, over the opaque ones. */
    static final int ALPHA_TRANSLUCENT = 1;
    /** Only fully opaque texels, so they can lay down depth first. */
    static final int ALPHA_OPAQUE = 2;

    private static final Vector3fc LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3fc LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();

    private final Vector3f lightDirection = new Vector3f();

    private final GlUniformInt texture;
    private final GlUniformInt lightmap;
    private final GlUniformInt textureArray;
    private final GlUniformInt textureArrayEnabled;
    private final GlUniformInt emissive;
    private final GlUniformInt glintPass;
    private final GlUniformInt chargePass;
    private final GlUniformInt itemGlintPass;
    private final GlUniformInt boxInstancing;
    private final GlUniformInt alphaPass;
    private final GlUniformMatrix4f itemGlintMatrix;
    private final GlUniformFloat3v lightDirection0;
    private final GlUniformFloat3v lightDirection1;
    private final ChunkShaderComponent fog;

    InstanceShader(ShaderBindingContext context, boolean textureArraysSupported,
            ChunkShaderComponent.Factory<?> fogFactory) {
        this.texture = context.bindUniform("uTexture", GlUniformInt::new);
        this.lightmap = context.bindUniform("uLightmap", GlUniformInt::new);
        this.textureArray = textureArraysSupported
                ? context.bindUniform("uTextureArray", GlUniformInt::new)
                : null;
        this.textureArrayEnabled = textureArraysSupported
                ? context.bindUniform("uTextureArrayEnabled", GlUniformInt::new)
                : null;
        this.emissive = context.bindUniform("uEmissive", GlUniformInt::new);
        this.glintPass = context.bindUniform("uGlintPass", GlUniformInt::new);
        this.chargePass = context.bindUniform("uChargePass", GlUniformInt::new);
        this.itemGlintPass = context.bindUniform("uItemGlintPass", GlUniformInt::new);
        this.boxInstancing = context.bindUniform("uBoxInstancing", GlUniformInt::new);
        this.alphaPass = context.bindUniform("uAlphaPass", GlUniformInt::new);
        this.itemGlintMatrix = context.bindUniform("uItemGlintMatrix", GlUniformMatrix4f::new);
        this.lightDirection0 = context.bindUniform("uLightDirection0", GlUniformFloat3v::new);
        this.lightDirection1 = context.bindUniform("uLightDirection1", GlUniformFloat3v::new);
        this.fog = fogFactory.create(context);
    }

    void initialize() {
        this.texture.setInt(0);
        this.lightmap.setInt(1);
        if (this.textureArray != null) {
            this.textureArray.setInt(2);
        }
        this.glintPass.setInt(-1);
        this.chargePass.setInt(0);
        this.itemGlintPass.setInt(-1);
        this.boxInstancing.setInt(0);
        this.alphaPass.setInt(ALPHA_ALL);
    }

    void setUniforms() {
        this.fog.setup();
        this.setLightDirection(this.lightDirection0, LIGHT_0);
        this.setLightDirection(this.lightDirection1, LIGHT_1);
    }

    private void setLightDirection(GlUniformFloat3v uniform, Vector3fc direction) {
        ArgentumWorldRenderer.instance().createChunkRenderMatrices().modelView()
                .transformDirection(direction, this.lightDirection).normalize();
        uniform.set(this.lightDirection.x, this.lightDirection.y, this.lightDirection.z);
    }

    void setTextureArray(boolean enabled) {
        if (this.textureArrayEnabled != null) {
            this.textureArrayEnabled.setInt(enabled ? 1 : 0);
        }
    }

    void setEmissive(boolean emissive) {
        this.emissive.setInt(emissive ? 1 : 0);
    }

    void setGlintPass(int pass) {
        this.glintPass.setInt(pass);
    }

    void setChargePass(int pass) {
        this.chargePass.setInt(pass);
    }

    void setBoxInstancing(boolean enabled) {
        this.boxInstancing.setInt(enabled ? 1 : 0);
    }

    void setAlphaPass(int pass) {
        this.alphaPass.setInt(pass);
    }

    void setItemGlintPass(int pass) {
        this.itemGlintPass.setInt(pass);
    }

    void setItemGlintPass(int pass, Matrix4fc matrix) {
        this.itemGlintPass.setInt(pass);
        this.itemGlintMatrix.set(matrix);
    }
}
