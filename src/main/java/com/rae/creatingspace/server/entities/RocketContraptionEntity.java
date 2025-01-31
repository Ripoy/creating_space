package com.rae.creatingspace.server.entities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.rae.creatingspace.init.ingameobject.EntityInit;
import com.rae.creatingspace.init.ingameobject.FluidInit;
import com.rae.creatingspace.init.worldgen.DimensionInit;
import com.rae.creatingspace.server.contraption.RocketContraption;
import com.rae.creatingspace.utilities.CustomTeleporter;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;

import static com.google.common.primitives.Floats.constrainToRange;

public class RocketContraptionEntity extends AbstractContraptionEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    public BlockPos rocketEntryCoordinate = new BlockPos(0,0,0);
    public boolean reentry = false;
    public boolean havePropellantsTanks = false;
    public float trust = 0;
    public float dryMass = 0;
    public float inertFluidsMass = 0;
    private int propellantConsumption = 0;
    public ResourceKey<Level> originDimension = Level.OVERWORLD;
    public ResourceKey<Level> destination;

    //initializing and saving methods

    public RocketContraptionEntity(EntityType<?> type, Level level) {
        super(type, level);
    }
    public static RocketContraptionEntity create(Level level, RocketContraption contraption, ResourceKey<Level> destination) {
        RocketContraptionEntity entity =
                new RocketContraptionEntity(EntityInit.ROCKET_CONTRAPTION.get(), level);
        entity.setContraption(contraption);
        entity.havePropellantsTanks = entity.trySearchTanks(entity);
        entity.dryMass = contraption.getDryMass();
        entity.trust = contraption.getTrust();
        entity.propellantConsumption = contraption.getPropellantConsumption();
        entity.originDimension = level.dimension();
        entity.destination = destination;
        LOGGER.info("finishing setting up parameters");
        entity.noPhysics = false;

        return entity;
    }
    static final EntityDataAccessor<Float> SPEED_ENTITY_DATA_ACCESSOR =
            SynchedEntityData.defineId(AbstractContraptionEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> ACCELERATION_ENTITY_DATA_ACCESSOR =
            SynchedEntityData.defineId(AbstractContraptionEntity.class, EntityDataSerializers.FLOAT);


    static final EntityDataAccessor<Float> OXYGEN_AMOUNT_DATA_ACCESSOR =
            SynchedEntityData.defineId(AbstractContraptionEntity.class, EntityDataSerializers.FLOAT);

    static final EntityDataAccessor<Float> METHANE_AMOUNT_DATA_ACCESSOR =
            SynchedEntityData.defineId(AbstractContraptionEntity.class, EntityDataSerializers.FLOAT);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SPEED_ENTITY_DATA_ACCESSOR, 0f);
        this.entityData.define(ACCELERATION_ENTITY_DATA_ACCESSOR,0f);
        this.entityData.define(OXYGEN_AMOUNT_DATA_ACCESSOR, 0f);
        this.entityData.define(METHANE_AMOUNT_DATA_ACCESSOR, 0f);
    }

    @Override
    protected void readAdditional(CompoundTag compound, boolean spawnData) {
        super.readAdditional(compound, spawnData);
        this.havePropellantsTanks = compound.getBoolean("havePropellantsTanks");
        this.trust = compound.getFloat("trust");
        this.dryMass = compound.getFloat("dryMass");
        this.propellantConsumption = compound.getInt("propellantConsumption");
        this.reentry = compound.getBoolean("reentry");
        this.entityData.set(SPEED_ENTITY_DATA_ACCESSOR, compound.getFloat("verticalSpeed"));
        this.entityData.set(ACCELERATION_ENTITY_DATA_ACCESSOR,compound.getFloat("verticalAcceleration"));
        this.destination = ResourceKey.create(Registry.DIMENSION_REGISTRY,
                new ResourceLocation(
                        compound.getString("destination:nameSpace"),
                        compound.getString("destination:path")));
    }
    @Override
    protected void writeAdditional(CompoundTag compound, boolean spawnPacket) {
        compound.putBoolean("havePropellantsTanks",this.havePropellantsTanks);
        compound.putInt("propellantConsumption", this.propellantConsumption);
        compound.putFloat("trust",this.trust);
        compound.putFloat("dryMass",this.dryMass);
        compound.putFloat("verticalSpeed",this.entityData.get(SPEED_ENTITY_DATA_ACCESSOR));
        compound.putFloat("verticalAcceleration",this.entityData.get(ACCELERATION_ENTITY_DATA_ACCESSOR));
        compound.putBoolean("reentry",this.reentry);
        compound.putString("destination:nameSpace",this.destination.location().getNamespace());
        compound.putString("destination:path",this.destination.location().getPath());
        super.writeAdditional(compound, spawnPacket);
    }


    @Override
    protected void tickContraption() {
        tickActors();
        if (!(level.isClientSide())){
            float speed = this.entityData.get(SPEED_ENTITY_DATA_ACCESSOR);
            float gravity = DimensionInit.gravity(this.level.dimensionTypeId());


            float o2mass = this.entityData.get(OXYGEN_AMOUNT_DATA_ACCESSOR) *
                    FluidInit.LIQUID_OXYGEN.getType().getDensity() / 1000;
            float ch4mass =  this.entityData.get(METHANE_AMOUNT_DATA_ACCESSOR) *
                    FluidInit.LIQUID_METHANE.getType().getDensity() / 1000;

            if (this.havePropellantsTanks && !this.reentry && !(o2mass == 0f || ch4mass == 0f)) {

                float acceleration  = (float) ((this.trust/((this.dryMass + this.inertFluidsMass + o2mass + ch4mass)*9.81)- gravity )/20);
                this.entityData.set(ACCELERATION_ENTITY_DATA_ACCESSOR,acceleration);

                consumePropellant(this);

            } else if(gravity!=0 || speed!=0){
                this.entityData.set(ACCELERATION_ENTITY_DATA_ACCESSOR,
                        - gravity /20 );
            }
            else {
                disassemble();
            }
            float acceleration = this.entityData.get(ACCELERATION_ENTITY_DATA_ACCESSOR);
            speed = constrainToRange(speed + acceleration,-1,2);

            this.entityData.set(SPEED_ENTITY_DATA_ACCESSOR,speed);

        }

        setContraptionMotion(new Vec3(0,this.entityData.get(SPEED_ENTITY_DATA_ACCESSOR), 0));
        move(0, this.entityData.get(SPEED_ENTITY_DATA_ACCESSOR), 0);

        if (ContraptionCollider.collideBlocks(this)) {
            if (!level.isClientSide) {
                //unstuck(this);
                LOGGER.debug("rocket collide" + "acceleration :" + this.getAcceleration());
                disassemble();
            }
        }

        if (!level.isClientSide ) {

            if (position().get(Direction.Axis.Y) > 300  ){


                ServerLevel destServerLevel = this.level.getServer().getLevel(this.destination);

                if (destServerLevel!=null && level.dimension() == this.originDimension && (this.entityData.get(SPEED_ENTITY_DATA_ACCESSOR) > 1.5 || DimensionInit.gravity(this.level.dimensionTypeId())==0)) {

                    this.changeDimension(destServerLevel,new CustomTeleporter(destServerLevel));
                }
                else {
                    LOGGER.info("dimension change failed at first step");
                    LOGGER.info("rocket info :");
                    LOGGER.info("destination :" + destServerLevel);
                    LOGGER.info("current dimension :" + level.dimension());
                    LOGGER.info("origin Dimension : " + this.originDimension);
                    LOGGER.info("speed :" + this.entityData.get(SPEED_ENTITY_DATA_ACCESSOR));
                    LOGGER.info("gravity of current dimension" + DimensionInit.gravity(this.level.dimensionTypeId()));
                }
                disassemble();
            }
        }
    }

    /*@Override
    public void disassemble() {
        BlockPos anchor = this.contraption.anchor;
        Block anchoringBlock = this.contraption.getBlocks().get(anchor).state.getBlock();
        if (anchoringBlock instanceof ControlsBlock){

        }
        super.disassemble();
    }*/

    //utility methods
    private void unstuck(RocketContraptionEntity contraptionEntity){
        while (ContraptionCollider.collideBlocks(contraptionEntity)){
            contraptionEntity.move(0,1,0);
            contraptionEntity.setContraptionMotion(new Vec3(0,1,0));
        }
    }
    private void consumePropellant(RocketContraptionEntity rocketContraptionEntity) {
        RocketContraption rocketContraption = (RocketContraption) rocketContraptionEntity.contraption;
        IFluidHandler fluidHandler = rocketContraption.getSharedFluidTanks();

        int drainAmount = this.propellantConsumption;
        fluidHandler.drain(new FluidStack(FluidInit.LIQUID_METHANE.get(),drainAmount) , IFluidHandler.FluidAction.EXECUTE );//drain methane
        fluidHandler.drain(new FluidStack(FluidInit.LIQUID_OXYGEN.get(),drainAmount) , IFluidHandler.FluidAction.EXECUTE );//drain oxygen

        rocketContraptionEntity.havePropellantsTanks = trySearchTanks(rocketContraptionEntity);

    }

    private boolean trySearchTanks(RocketContraptionEntity contraptionEntity){
        IFluidHandler fluidHandler = contraptionEntity.contraption.getSharedFluidTanks();
        boolean foundMethaneTank = false;
        boolean foundOxygenTank = false;
        float o2amount = 0;
        float ch4amount = 0;


        int nbrOfTank = fluidHandler.getTanks();

        for (int i=0 ; i < nbrOfTank; i++) {
            FluidStack fluidInTank = fluidHandler.getFluidInTank(i);
            FluidType fluidType = fluidInTank.getFluid().getFluidType();

            if (fluidType == FluidInit.LIQUID_METHANE.getType()){
                foundMethaneTank = !fluidHandler.getFluidInTank(i).isEmpty();
                ch4amount = fluidHandler.getFluidInTank(i).getAmount();

            }
            else if (fluidType == FluidInit.LIQUID_OXYGEN.getType()){
                foundOxygenTank = !fluidHandler.getFluidInTank(i).isEmpty();
                o2amount = fluidHandler.getFluidInTank(i).getAmount();
            }
            else {

                contraptionEntity.inertFluidsMass += (float) (fluidInTank.getAmount() * fluidType.getDensity()) /1000;
            }
        }
        contraptionEntity.entityData.set(OXYGEN_AMOUNT_DATA_ACCESSOR,o2amount);
        contraptionEntity.entityData.set(METHANE_AMOUNT_DATA_ACCESSOR,ch4amount);

        return foundMethaneTank && foundOxygenTank;
    }
    @Nullable
    @Override
    public Entity changeDimension(ServerLevel destLevel, ITeleporter teleporter) {
        //rewrite so passengers get teleported with it
        if (!ForgeHooks.onTravelToDimension(this, destLevel.dimension())) return null;
        if (this.level instanceof ServerLevel && !this.isRemoved()) {
            this.level.getProfiler().push("changeDimension");

            List<Entity> passengers = this.getPassengers();

            this.unRide();
            this.level.getProfiler().push("reposition");
            PortalInfo portalinfo = teleporter.getPortalInfo(this, destLevel, this::findDimensionEntryPoint);
            if (portalinfo == null) {
                return null;
            } else {
                Entity transportedEntity = teleporter.placeEntity(this, (ServerLevel) this.level, destLevel, this.getYRot(),

                        spawnPortal -> { //Forge: Start custom logic
                            this.level.getProfiler().popPush("reloading");

                            RocketContraptionEntity entity = (RocketContraptionEntity) this.getType().create(destLevel);

                            if (entity != null) {

                                entity.restoreFrom(this);//copy the contraption first
                                entity.moveTo(portalinfo.pos.x, portalinfo.pos.y, portalinfo.pos.z, portalinfo.yRot, entity.getXRot());
                                entity.setDeltaMovement(portalinfo.speed);
                                entity.entityData.set(SPEED_ENTITY_DATA_ACCESSOR,(float) portalinfo.speed.y());
                                //adding previously riding passengers
                                for (int i = 0; i < passengers.size(); i++) {
                                    Entity passenger = passengers.get(i);
                                    passenger.moveTo(portalinfo.pos.x, portalinfo.pos.y, portalinfo.pos.z, portalinfo.yRot, passenger.getXRot());

                                    if (passenger instanceof ServerPlayer player) {
                                        player.changeDimension(destLevel, new CustomTeleporter(destLevel));
                                        entity.addSittingPassenger(player, i);
                                    } else {
                                        if (!(passenger instanceof Player)) {
                                            passenger.changeDimension(destLevel, new CustomTeleporter(destLevel));
                                            entity.addSittingPassenger(passenger, i);
                                        }
                                    }
                                }


                                destLevel.addDuringTeleport(entity);
                                if (DimensionInit.gravity(destLevel.dimensionTypeId()) == 0f){
                                    entity.disassemble();
                                }
                                else{
                                    entity.reentry = true;
                                }
                            }
                            return entity;
                        }); //Forge: End custom logic

                this.removeAfterChangingDimensions();
                this.level.getProfiler().pop();
                ((ServerLevel) this.level).resetEmptyTime();
                destLevel.resetEmptyTime();
                this.level.getProfiler().pop();
                return transportedEntity;
            }
        } else {
            return null;
        }
    }

    @Override
    public boolean startControlling(BlockPos controlsLocalPos, Player player) {
        return false;
    }

    @Override
    public boolean control(BlockPos controlsLocalPos, Collection<Integer> heldControls, Player player) {
        return true;
    }

    @Override
    public boolean handlePlayerInteraction(Player player, BlockPos localPos, Direction side, InteractionHand interactionHand) {
        return super.handlePlayerInteraction(player, localPos, side, interactionHand);
    }

    @Override
    public Vec3 applyRotation(Vec3 localPos, float partialTicks) {
        return localPos;
    }

    @Override
    public Vec3 reverseRotation(Vec3 localPos, float partialTicks) {
        return localPos;
    }





    @Override
    protected StructureTransform makeStructureTransform() {
        return new StructureTransform(new BlockPos(getAnchorVec().add(.5, .5, .5)), 0, 0, 0);
    }

    @Override
    protected float getStalledAngle() {
        return 0;
    }

    @Override
    protected void handleStallInformation(double x, double y, double z, float angle) {

    }

    @Override
    public ContraptionRotationState getRotationState() {
        return ContraptionRotationState.NONE;
    }

    @Override
    public void applyLocalTransforms(PoseStack matrixStack, float partialTicks) {

    }


    public float getAcceleration() {
        return this.entityData.get(ACCELERATION_ENTITY_DATA_ACCESSOR);
    }
}
