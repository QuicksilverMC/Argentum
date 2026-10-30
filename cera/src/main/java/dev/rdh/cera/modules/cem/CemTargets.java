package dev.rdh.cera.modules.cem;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnchantingTableBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.render.block.entity.BannerRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.ChestRenderer;
import net.minecraft.client.render.block.entity.EnchantingTableRenderer;
import net.minecraft.client.render.block.entity.EnderChestRenderer;
import net.minecraft.client.render.block.entity.SignRenderer;
import net.minecraft.client.render.block.entity.SkullRenderer;
import net.minecraft.client.render.entity.BoatRenderer;
import net.minecraft.client.render.entity.EnderCrystalRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LeadKnotRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.MinecartRenderer;
import net.minecraft.client.render.entity.WitherSkullRenderer;
import net.minecraft.client.render.entity.ZombieRenderer;
import net.minecraft.client.render.entity.layer.SheepFurLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.block.entity.BannerModel;
import net.minecraft.client.render.model.block.entity.ChestModel;
import net.minecraft.client.render.model.block.entity.EnchantingTableBookModel;
import net.minecraft.client.render.model.block.entity.EnderCrystalModel;
import net.minecraft.client.render.model.block.entity.SignModel;
import net.minecraft.client.render.model.block.entity.SkullModel;
import net.minecraft.client.render.model.entity.ArmorStandModel;
import net.minecraft.client.render.model.entity.BatModel;
import net.minecraft.client.render.model.entity.BlazeModel;
import net.minecraft.client.render.model.entity.BoatModel;
import net.minecraft.client.render.model.entity.ChickenModel;
import net.minecraft.client.render.model.entity.CreeperModel;
import net.minecraft.client.render.model.entity.EnderDragonModel;
import net.minecraft.client.render.model.entity.EndermiteModel;
import net.minecraft.client.render.model.entity.GhastModel;
import net.minecraft.client.render.model.entity.GuardianModel;
import net.minecraft.client.render.model.entity.HorseModel;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.client.render.model.entity.IronGolemModel;
import net.minecraft.client.render.model.entity.LeashModel;
import net.minecraft.client.render.model.entity.MagmaCubeModel;
import net.minecraft.client.render.model.entity.MinecartModel;
import net.minecraft.client.render.model.entity.OcelotModel;
import net.minecraft.client.render.model.entity.QuadrupedModel;
import net.minecraft.client.render.model.entity.RabbitModel;
import net.minecraft.client.render.model.entity.SilverfishModel;
import net.minecraft.client.render.model.entity.SlimeModel;
import net.minecraft.client.render.model.entity.SnowGolemModel;
import net.minecraft.client.render.model.entity.SpiderModel;
import net.minecraft.client.render.model.entity.SquidModel;
import net.minecraft.client.render.model.entity.VillagerModel;
import net.minecraft.client.render.model.entity.WitchModel;
import net.minecraft.client.render.model.entity.WitherModel;
import net.minecraft.client.render.model.entity.WolfModel;
import net.minecraft.entity.EnderCrystalEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.LeadKnotEntity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.SnowGolemEntity;
import net.minecraft.entity.living.mob.ambient.BatEntity;
import net.minecraft.entity.living.mob.monster.BlazeEntity;
import net.minecraft.entity.living.mob.monster.CaveSpiderEntity;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.EndermanEntity;
import net.minecraft.entity.living.mob.monster.EndermiteEntity;
import net.minecraft.entity.living.mob.monster.GiantEntity;
import net.minecraft.entity.living.mob.monster.GhastEntity;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.entity.living.mob.monster.MagmaCubeEntity;
import net.minecraft.entity.living.mob.monster.SilverfishEntity;
import net.minecraft.entity.living.mob.monster.SkeletonEntity;
import net.minecraft.entity.living.mob.monster.SlimeEntity;
import net.minecraft.entity.living.mob.monster.SpiderEntity;
import net.minecraft.entity.living.mob.monster.WitchEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.mob.monster.ZombiePigmanEntity;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.entity.living.mob.passive.animal.CowEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.mob.passive.animal.MooshroomEntity;
import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import net.minecraft.entity.living.mob.passive.animal.RabbitEntity;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.OcelotEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.entity.living.mob.water.SquidEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.CommandBlockMinecartEntity;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.entity.vehicle.SpawnerMinecartEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

final class CemTargets {
    record Dispatchers(EntityRenderDispatcher entities, BlockEntityRenderDispatcher blocks) {
    }

    record Binding(Object renderer, Map<String, ModelPart> parts, boolean main) {
    }

    static final Predicate<Object> ALWAYS = _ -> true;

    interface Target {
        Binding bind(Dispatchers dispatchers);

        default Predicate<Object> when() {
            return ALWAYS;
        }
    }

    record Variant(String name, Predicate<Object> when) {
    }

    record Placed(BlockEntity blockEntity, Block block) {
    }

    static final Map<String, Target> TARGETS = Map.ofEntries(
            Map.entry("armor_stand", living(ArmorStandEntity.class, model -> armorStand((ArmorStandModel)model))),
            Map.entry("bat", living(BatEntity.class, model -> bat((BatModel)model))),
            Map.entry("blaze", living(BlazeEntity.class, model -> blaze((BlazeModel)model))),
            Map.entry("boat", entity(BoatEntity.class, renderer -> boat((BoatModel)((BoatRenderer)renderer).model))),
            Map.entry("cave_spider", living(CaveSpiderEntity.class, model -> spider((SpiderModel)model))),
            Map.entry("chicken", living(ChickenEntity.class, model -> chicken((ChickenModel)model))),
            Map.entry("cow", living(CowEntity.class, model -> quadruped((QuadrupedModel)model))),
            Map.entry("creeper", living(CreeperEntity.class, model -> creeper((CreeperModel)model))),
            Map.entry("dragon", living(EnderDragonEntity.class, model -> dragon((EnderDragonModel)model))),
            Map.entry("end_crystal", entity(EnderCrystalEntity.class,
                    renderer -> endCrystal((EnderCrystalModel)((EnderCrystalRenderer)renderer).model))),
            Map.entry("enderman", living(EndermanEntity.class, model -> biped((HumanoidModel)model))),
            Map.entry("endermite", living(EndermiteEntity.class, model -> endermite((EndermiteModel)model))),
            Map.entry("ghast", living(GhastEntity.class, model -> ghast((GhastModel)model))),
            Map.entry("guardian", living(GuardianEntity.class, model -> guardian((GuardianModel)model))),
            Map.entry("horse", living(HorseBaseEntity.class, model -> horse((HorseModel)model))),
            Map.entry("iron_golem", living(IronGolemEntity.class, model -> ironGolem((IronGolemModel)model))),
            Map.entry("lead_knot", entity(LeadKnotEntity.class, renderer -> leadKnot(((LeadKnotRenderer)renderer).model))),
            Map.entry("magma_cube", living(MagmaCubeEntity.class, model -> magmaCube((MagmaCubeModel)model))),
            Map.entry("minecart", minecart(MinecartEntity.class)),
            Map.entry("mooshroom", living(MooshroomEntity.class, model -> quadruped((QuadrupedModel)model))),
            Map.entry("ocelot", living(OcelotEntity.class, model -> ocelot((OcelotModel)model))),
            Map.entry("pig", living(PigEntity.class, model -> quadruped((QuadrupedModel)model))),
            Map.entry("rabbit", living(RabbitEntity.class, model -> rabbit((RabbitModel)model))),
            Map.entry("sheep", living(SheepEntity.class, model -> quadruped((QuadrupedModel)model))),
            Map.entry("sheep_wool", layer(entity(SheepEntity.class, CemTargets::sheepWool))),
            Map.entry("silverfish", living(SilverfishEntity.class, model -> silverfish((SilverfishModel)model))),
            Map.entry("skeleton", living(SkeletonEntity.class, model -> biped((HumanoidModel)model))),
            Map.entry("slime", living(SlimeEntity.class, model -> slime((SlimeModel)model))),
            Map.entry("snow_golem", living(SnowGolemEntity.class, model -> snowGolem((SnowGolemModel)model))),
            Map.entry("spawner_minecart", minecart(SpawnerMinecartEntity.class)),
            Map.entry("spider", living(SpiderEntity.class, model -> spider((SpiderModel)model))),
            Map.entry("squid", living(SquidEntity.class, model -> squid((SquidModel)model))),
            Map.entry("tnt_minecart", minecart(TntMinecartEntity.class)),
            Map.entry("villager", living(VillagerEntity.class, model -> villager((VillagerModel)model))),
            Map.entry("witch", living(WitchEntity.class, model -> witch((WitchModel)model))),
            Map.entry("wither", living(WitherEntity.class, model -> wither((WitherModel)model))),
            Map.entry("wither_skull", entity(WitherSkullEntity.class,
                    renderer -> parts("head", ((WitherSkullRenderer)renderer).model.skull))),
            Map.entry("wolf", living(WolfEntity.class, model -> wolf((WolfModel)model))),
            // ZombieRenderer swaps its model per entity, so it holds the zombie model itself
            Map.entry("zombie", only(entity(ZombieEntity.class, renderer -> biped(((ZombieRenderer)renderer).zombieModel)),
                    rendered -> !((ZombieEntity)rendered).isVillager())),
            Map.entry("zombie_villager", only(entity(ZombieEntity.class, renderer -> biped(((ZombieRenderer)renderer).zombieVillagerModel)),
                    rendered -> ((ZombieEntity)rendered).isVillager())),
            Map.entry("giant", living(GiantEntity.class, model -> biped((HumanoidModel)model))),
            Map.entry("zombie_pigman", living(ZombiePigmanEntity.class, model -> biped((HumanoidModel)model))),
            Map.entry("banner", block(BannerBlockEntity.class, renderer -> banner(((BannerRenderer)renderer).model))),
            Map.entry("book", block(EnchantingTableBlockEntity.class, renderer -> book(((EnchantingTableRenderer)renderer).bookModel))),
            Map.entry("chest", block(ChestBlockEntity.class, renderer -> chest(((ChestRenderer)renderer).singleChestModel))),
            Map.entry("chest_large", block(ChestBlockEntity.class, renderer -> chest(((ChestRenderer)renderer).doubleChestModel))),
            Map.entry("ender_chest", block(EnderChestBlockEntity.class, renderer -> chest(((EnderChestRenderer)renderer).model))),
            Map.entry("head_humanoid", block(SkullBlockEntity.class, renderer -> skull(((SkullRenderer)renderer).humanoidModel))),
            Map.entry("head_skeleton", block(SkullBlockEntity.class, renderer -> skull(((SkullRenderer)renderer).model))),
            Map.entry("sign", block(SignBlockEntity.class, renderer -> sign(((SignRenderer)renderer).model)))
    );

    static final Map<String, List<Variant>> VARIANTS = Map.of(
            "skeleton", List.of(new Variant("wither_skeleton", rendered -> ((SkeletonEntity)rendered).getType() == 1)),
            "guardian", List.of(new Variant("elder_guardian", rendered -> ((GuardianEntity)rendered).isElder())),
            "ocelot", List.of(new Variant("cat", rendered -> ((OcelotEntity)rendered).isTamed())),
            "minecart", List.of(new Variant("chest_minecart", rendered -> rendered instanceof ChestMinecartEntity),
                    new Variant("furnace_minecart", rendered -> rendered instanceof FurnaceMinecartEntity),
                    new Variant("hopper_minecart", rendered -> rendered instanceof HopperMinecartEntity),
                    new Variant("command_block_minecart", rendered -> rendered instanceof CommandBlockMinecartEntity)),
            "chest", List.of(new Variant("trapped_chest", rendered -> ((Placed)rendered).block() == Blocks.TRAPPED_CHEST)),
            "sign", List.of(new Variant("wall_sign", rendered -> ((Placed)rendered).block() == Blocks.WALL_SIGN)),
            "book", List.of(new Variant("enchanting_book", ALWAYS)),
            "zombie_pigman", List.of(new Variant("zombified_piglin", ALWAYS)),
            "head_skeleton", List.of(new Variant("head_wither_skeleton", rendered -> skullType(rendered) == 1),
                    new Variant("head_creeper", rendered -> skullType(rendered) == 4)),
            "head_humanoid", List.of(new Variant("head_player", rendered -> skullType(rendered) == 3),
                    new Variant("head_zombie", rendered -> skullType(rendered) == 2))
    );

    private static int skullType(Object rendered) {
        if (rendered instanceof Integer type) return type;
        return rendered instanceof Placed placed && placed.blockEntity() instanceof SkullBlockEntity skull ? skull.getType() : -1;
    }

    private CemTargets() {
    }

    private static Target only(Target target, Predicate<Object> when) {
        return new Target() {
            @Override
            public Binding bind(Dispatchers dispatchers) {
                return target.bind(dispatchers);
            }

            @Override
            public Predicate<Object> when() {
                return when;
            }
        };
    }

    private static Target layer(Target target) {
        return dispatchers -> {
            Binding binding = target.bind(dispatchers);
            return binding == null ? null : new Binding(binding.renderer(), binding.parts(), false);
        };
    }

    private static Target living(Class<? extends Entity> type, Function<Model, Map<String, ModelPart>> parts) {
        return entity(type, renderer -> parts.apply(((LivingEntityRenderer<?>)renderer).getModel()));
    }

    private static Target entity(Class<? extends Entity> type, Function<EntityRenderer<?>, Map<String, ModelPart>> parts) {
        return dispatchers -> {
            if (dispatchers.entities() == null) return null;
            EntityRenderer<?> renderer = dispatchers.entities().getRenderer(type);
            return renderer == null ? null : new Binding(renderer, parts.apply(renderer), true);
        };
    }

    private static Target block(Class<? extends BlockEntity> type, Function<BlockEntityRenderer<?>, Map<String, ModelPart>> parts) {
        return dispatchers -> {
            BlockEntityRenderer<?> renderer = dispatchers.blocks().getRenderer(type);
            return renderer == null ? null : new Binding(renderer, parts.apply(renderer), true);
        };
    }

    private static Target minecart(Class<? extends MinecartEntity> type) {
        return entity(type, renderer -> {
            ModelPart[] parts = ((MinecartModel)((MinecartRenderer<?>)renderer).model).parts;
            return parts("bottom", parts[0], "back", parts[1], "front", parts[2], "right", parts[3], "left", parts[4], "dirt", parts[5]);
        });
    }

    private static Map<String, ModelPart> sheepWool(EntityRenderer<?> renderer) {
        for (Object layer : ((LivingEntityRenderer<?>)renderer).layers) {
            if (layer instanceof SheepFurLayer fur) return quadruped(fur.model);
        }
        return Map.of();
    }

    private static Map<String, ModelPart> parts(Object... namesAndParts) {
        Map<String, ModelPart> parts = new LinkedHashMap<>();
        for (int i = 0; i < namesAndParts.length; i += 2) {
            parts.put((String)namesAndParts[i], (ModelPart)namesAndParts[i + 1]);
        }
        return parts;
    }

    private static Map<String, ModelPart> numbered(Map<String, ModelPart> parts, String prefix, ModelPart[] array) {
        for (int i = 0; i < array.length; i++) parts.put(prefix + (i + 1), array[i]);
        return parts;
    }

    private static Map<String, ModelPart> biped(HumanoidModel model) {
        return parts("head", model.head, "headwear", model.hat, "body", model.body, "left_arm", model.leftArm,
                "right_arm", model.rightArm, "left_leg", model.leftLeg, "right_leg", model.rightLeg);
    }

    private static Map<String, ModelPart> quadruped(QuadrupedModel model) {
        return parts("head", model.head, "body", model.body, "leg1", model.backRightLeg, "leg2", model.backLeftLeg,
                "leg3", model.frontRightLeg, "leg4", model.frontLeftLeg);
    }

    private static Map<String, ModelPart> armorStand(ArmorStandModel model) {
        Map<String, ModelPart> parts = biped(model);
        parts.putAll(parts("right", model.rightBodyStick, "left", model.leftBodyStick, "waist", model.shoulderStick,
                "base", model.basePlate));
        return parts;
    }

    private static Map<String, ModelPart> bat(BatModel model) {
        return parts("head", model.head, "body", model.body, "right_wing", model.rightWing, "left_wing", model.leftWing,
                "outer_right_wing", model.rightWingTip, "outer_left_wing", model.leftWingTip);
    }

    private static Map<String, ModelPart> blaze(BlazeModel model) {
        return numbered(parts("head", model.head), "stick", model.rods);
    }

    private static Map<String, ModelPart> boat(BoatModel model) {
        ModelPart[] parts = model.parts;
        return parts("bottom", parts[0], "back", parts[1], "front", parts[2], "right", parts[3], "left", parts[4]);
    }

    private static Map<String, ModelPart> chicken(ChickenModel model) {
        return parts("head", model.head, "body", model.body, "right_leg", model.rightLeg, "left_leg", model.leftLeg,
                "right_wing", model.rightWing, "left_wing", model.leftWing, "bill", model.beak, "chin", model.waddle);
    }

    private static Map<String, ModelPart> creeper(CreeperModel model) {
        return parts("head", model.head, "armor", model.hat, "body", model.body, "leg1", model.rightBackLeg,
                "leg2", model.leftBackleg, "leg3", model.rightFrontLeg, "leg4", model.leftFrontLeg);
    }

    private static Map<String, ModelPart> dragon(EnderDragonModel model) {
        return parts("head", model.head, "spine", model.neck, "jaw", model.jaw, "body", model.body,
                "rear_leg", model.rearLeg, "front_leg", model.frontLeg, "rear_leg_tip", model.rearLegTip,
                "front_leg_tip", model.frontLegTip, "rear_foot", model.rearFoot, "front_foot", model.frontFoot,
                "wing", model.wing, "wing_tip", model.wingTip);
    }

    private static Map<String, ModelPart> endCrystal(EnderCrystalModel model) {
        return parts("cube", model.cube, "glass", model.glass, "base", model.base);
    }

    private static Map<String, ModelPart> endermite(EndermiteModel model) {
        return numbered(parts(), "body", model.parts);
    }

    private static Map<String, ModelPart> ghast(GhastModel model) {
        return numbered(parts("body", model.body), "tentacle", model.tentacles);
    }

    private static Map<String, ModelPart> guardian(GuardianModel model) {
        return numbered(numbered(parts("body", model.head, "eye", model.eye), "spine", model.spikes), "tail", model.tail);
    }

    private static Map<String, ModelPart> horse(HorseModel model) {
        return parts("head", model.head, "upper_mouth", model.topJaw, "lower_mouth", model.bottomJaw,
                "horse_left_ear", model.donkeyLeftEar, "horse_right_ear", model.donkeyRightEar,
                "mule_left_ear", model.muleEarLeft, "mule_right_ear", model.muleEarRight, "neck", model.neck,
                "horse_face_ropes", model.halster, "mane", model.mane, "body", model.mainBody,
                "tail_base", model.dock, "tail_middle", model.tail, "tail_tip", model.skirt,
                "back_left_leg", model.leftThigh, "back_left_shin", model.backLeftCannon, "back_left_hoof", model.backLeftHoof,
                "back_right_leg", model.rightThigh, "back_right_shin", model.backRightCannon, "back_right_hoof", model.backRightHoof,
                "front_left_leg", model.leftForearm, "front_left_shin", model.frontLeftCannon, "front_left_hoof", model.frontLeftHoof,
                "front_right_leg", model.rightForearm, "front_right_shin", model.frontRightCannon, "front_right_hoof", model.frontRightHoof,
                "mule_left_chest", model.leftDonkeyChest, "mule_right_chest", model.rightDonkeyChest,
                "horse_saddle_bottom", model.saddleSeat, "horse_saddle_front", model.gullet, "horse_saddle_back", model.cantle,
                "horse_left_saddle_rope", model.leftFender, "horse_left_saddle_metal", model.leftStirrup,
                "horse_right_saddle_rope", model.rightFender, "horse_right_saddle_metal", model.rightStirrup,
                "horse_left_face_metal", model.leftSnaffleBit, "horse_right_face_metal", model.rightSnaffleBit,
                "horse_left_rein", model.leftReign, "horse_right_rein", model.rightReign);
    }

    private static Map<String, ModelPart> ironGolem(IronGolemModel model) {
        return parts("head", model.head, "body", model.body, "left_arm", model.leftArm, "right_arm", model.rightArm,
                "left_leg", model.rightLeg, "right_leg", model.leftLeg);
    }

    private static Map<String, ModelPart> leadKnot(LeashModel model) {
        return parts("knot", model.leash);
    }

    private static Map<String, ModelPart> magmaCube(MagmaCubeModel model) {
        return numbered(parts("core", model.body), "segment", model.slices);
    }

    private static Map<String, ModelPart> ocelot(OcelotModel model) {
        return parts("back_left_leg", model.backLeftLeg, "back_right_leg", model.backRightLeg,
                "front_left_leg", model.frontLeftLeg, "front_right_leg", model.frontrightLeg,
                "tail", model.upperTail, "tail2", model.lowerTail, "head", model.head, "body", model.body);
    }

    private static Map<String, ModelPart> rabbit(RabbitModel model) {
        return parts("left_foot", model.backLeftLeg, "right_foot", model.backRightLeg, "left_thigh", model.leftHaunch,
                "right_thigh", model.rightHaunch, "body", model.body, "left_arm", model.frontLeftLeg,
                "right_arm", model.frontRightLeg, "head", model.head, "right_ear", model.rightEar,
                "left_ear", model.leftEar, "tail", model.tail, "nose", model.nose);
    }

    private static Map<String, ModelPart> silverfish(SilverfishModel model) {
        return numbered(numbered(parts(), "body", model.body), "wing", model.scales);
    }

    private static Map<String, ModelPart> slime(SlimeModel model) {
        return parts("body", model.body, "left_eye", model.rightEye, "right_eye", model.leftEye, "mouth", model.mouth);
    }

    private static Map<String, ModelPart> snowGolem(SnowGolemModel model) {
        return parts("body", model.top, "body_bottom", model.bottom, "head", model.head, "left_hand", model.leftArm,
                "right_hand", model.rightArm);
    }

    private static Map<String, ModelPart> spider(SpiderModel model) {
        return parts("head", model.head, "neck", model.neck, "body", model.body, "leg1", model.backRightLeg,
                "leg2", model.backLeftLeg, "leg3", model.backMiddleRightLeg, "leg4", model.backMiddleLeftLeg,
                "leg5", model.frontMiddleRightLeg, "leg6", model.frontMiddleLeftLeg, "leg7", model.frontRightLeg,
                "leg8", model.frontLeftLeg);
    }

    private static Map<String, ModelPart> squid(SquidModel model) {
        return numbered(parts("body", model.body), "tentacle", model.tentacles);
    }

    private static Map<String, ModelPart> villager(VillagerModel model) {
        return parts("head", model.head, "body", model.body, "arms", model.arms, "left_leg", model.leftLeg,
                "right_leg", model.rightLeg, "nose", model.nose);
    }

    private static Map<String, ModelPart> witch(WitchModel model) {
        Map<String, ModelPart> parts = villager(model);
        parts.putAll(parts("mole", model.mole, "hat", model.hat));
        return parts;
    }

    private static Map<String, ModelPart> wither(WitherModel model) {
        return numbered(numbered(parts(), "body", model.body), "head", model.skulls);
    }

    private static Map<String, ModelPart> wolf(WolfModel model) {
        return parts("head", model.head, "body", model.body, "leg1", model.backRightLeg, "leg2", model.backLeftLeg,
                "leg3", model.frontRightLeg, "leg4", model.frontLeftLeg, "tail", model.tail, "mane", model.neck);
    }

    private static Map<String, ModelPart> banner(BannerModel model) {
        return parts("slate", model.flag, "stand", model.pole, "top", model.bar);
    }

    private static Map<String, ModelPart> book(EnchantingTableBookModel model) {
        return parts("cover_right", model.leftCover, "cover_left", model.rightCover, "pages_right", model.leftPage,
                "pages_left", model.rightPage, "flipping_page_right", model.leftFlippîngPage,
                "flipping_page_left", model.rightFlippingPage, "book_spine", model.spine);
    }

    private static Map<String, ModelPart> chest(ChestModel model) {
        return parts("lid", model.lid, "base", model.base, "knob", model.lock);
    }

    private static Map<String, ModelPart> skull(SkullModel model) {
        return parts("head", model.skull);
    }

    private static Map<String, ModelPart> sign(SignModel model) {
        return parts("board", model.board, "stick", model.pole);
    }
}
