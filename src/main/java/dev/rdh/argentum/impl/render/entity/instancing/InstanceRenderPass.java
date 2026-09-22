package dev.rdh.argentum.impl.render.entity.instancing;

public enum InstanceRenderPass {
    NORMAL(0),
    CULL_FRONT(0),
    CULL_BACK(0),
    NO_CULL(0),
    ITEM(0),
    TRANSLUCENT(0),
    EMISSIVE(0),
    EMISSIVE_REPLACE(0),
    GLINT(0),
    ITEM_GLINT_0(0),
    ITEM_GLINT_1(0),
    CREEPER_CHARGE(1),
    WITHER_CHARGE(2);

    final int chargePass;

    InstanceRenderPass(int chargePass) {
        this.chargePass = chargePass;
    }
}
