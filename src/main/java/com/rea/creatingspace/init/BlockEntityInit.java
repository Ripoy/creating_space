package com.rea.creatingspace.init;

import com.rea.creatingspace.blockentities.*;
import com.rea.creatingspace.renderer.FlowGaugeBlockRenderer;
import com.rea.creatingspace.renderer.MechanicalElectrolyserBlockRenderer;
import com.rea.creatingspace.renderer.RocketStarterBlockRenderer;
import com.simibubi.create.content.kinetics.base.ShaftInstance;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import static com.rea.creatingspace.CreatingSpace.REGISTRATE;

public class BlockEntityInit {
    public static final BlockEntityEntry<RocketControlsBlockEntity> CONTROLS =
            REGISTRATE.blockEntity("controls", RocketControlsBlockEntity::new)
            .validBlocks(BlockInit.ROCKET_CONTROLS)
            .register();

    /*public static final BlockEntityEntry<GroundBuilderBlockEntity> GROUND_STATION =
            REGISTRATE.blockEntity("station", GroundBuilderBlockEntity::new)
                    .validBlocks(BlockInit.GROUND_STATION)
                    .register();*/

    public static final BlockEntityEntry<RocketStarterBlockEntity> STARTER =
            REGISTRATE.blockEntity("starter", RocketStarterBlockEntity::new )
                    .instance(() -> ShaftInstance::new, false)
                    .validBlocks(BlockInit.EXPLOSIVE_STARTER)
                    .renderer(() -> RocketStarterBlockRenderer::new)
                    .register();

    public static final BlockEntityEntry<ChemicalSynthesizerBlockEntity> SYNTHESIZER =
            REGISTRATE.blockEntity("synthesizer", ChemicalSynthesizerBlockEntity::new)
                    .validBlocks(BlockInit.CHEMICAL_SYNTHESIZER)
                    .register();

    public static final BlockEntityEntry<RocketEngineBlockEntity> ENGINE =
            REGISTRATE.blockEntity(
                    "engine",RocketEngineBlockEntity::new)
                    .validBlocks(BlockInit.SMALL_ROCKET_ENGINE)
                    .register();


    public static final BlockEntityEntry<MechanicalElectrolyzerBlockEntity> ELECTROLIZER =
            REGISTRATE.blockEntity(
                    "electrolizer", MechanicalElectrolyzerBlockEntity::new)
                    .instance(()-> ShaftInstance::new)
                    .validBlocks( BlockInit.MECHANICAL_ELECTROLYZER)
                    .renderer(()-> MechanicalElectrolyserBlockRenderer::new)
                    .register();

    public static final BlockEntityEntry<FlowGaugeBlockEntity> FLOW_METER =
            REGISTRATE.blockEntity(
                            "flow_meter", FlowGaugeBlockEntity::new)
                    .validBlocks( BlockInit.FLOW_METER)
                    .renderer(()-> FlowGaugeBlockRenderer::new)
                    .register();

    public static void register() {}
}
