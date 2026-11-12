package de.simon.spacemod;

import de.simon.spacemod.block.BiobedBlock;
import de.simon.spacemod.block.CaptainsChairBlock;
import de.simon.spacemod.block.ChairBlock;
import de.simon.spacemod.entity.SeatEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import de.simon.spacemod.block.FacingBlock;
import de.simon.spacemod.block.GlowParticleBlock;
import de.simon.spacemod.block.RailingBlock;
import de.simon.spacemod.block.ShapedBlock;
import de.simon.spacemod.block.ShapedFacingBlock;
import de.simon.spacemod.block.VariantFacingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.shapes.Shapes;
import de.simon.spacemod.block.TransporterPadBlock;
import de.simon.spacemod.block.TurboliftBlock;
import de.simon.spacemod.turbolift.TurboliftMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import de.simon.spacemod.item.CommunicatorItem;
import de.simon.spacemod.item.HandWeaponItem;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.block.entity.BlockEntityType;
import de.simon.spacemod.block.PhaserBlock;
import de.simon.spacemod.block.StarshipDoorBlock;
import de.simon.spacemod.block.entity.DoorBlockEntity;
import de.simon.spacemod.item.KeycardItem;
import de.simon.spacemod.block.WarpCoreBlock;
import de.simon.spacemod.block.RifleRackBlock;
import de.simon.spacemod.block.PhaserHolderBlock;
import de.simon.spacemod.block.ChargingStationBlock;
import de.simon.spacemod.block.entity.ChargingStationBlockEntity;
import de.simon.spacemod.block.TorpedoLauncherBlock;
import de.simon.spacemod.entity.TorpedoEntity;
import de.simon.spacemod.block.WeaponsConsoleBlock;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Alle Blöcke, Items und Menüs der Mod. */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SimonSpaceMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SimonSpaceMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SimonSpaceMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SimonSpaceMod.MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SimonSpaceMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SimonSpaceMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SimonSpaceMod.MODID);
    public static final DeferredRegister<net.minecraft.world.level.gamerules.GameRule<?>> GAME_RULES =
        DeferredRegister.create(Registries.GAME_RULE, SimonSpaceMod.MODID);

    /** Spielregel: höchstens so viele Blöcke darf ein mit der Steuerkonsole gestartetes Schiff haben. */
    public static final RegistryObject<net.minecraft.world.level.gamerules.GameRule<Integer>> MAX_SHIP_BLOCKS = GAME_RULES.register("max_ship_blocks",
        () -> new net.minecraft.world.level.gamerules.GameRule<>(net.minecraft.world.level.gamerules.GameRuleCategory.MISC,
            net.minecraft.world.level.gamerules.GameRuleType.INT, com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1_000_000),
            net.minecraft.world.level.gamerules.GameRuleTypeVisitor::visitInteger, com.mojang.serialization.Codec.intRange(1, 1_000_000),
            i -> i, 3000, net.minecraft.world.flag.FeatureFlagSet.of()));

    private static final List<RegistryObject<? extends Item>> TAB_ITEMS = new ArrayList<>();

    // --- Außenhülle ---
    public static final RegistryObject<Block> HULL_PLATING = block("hull_plating", Block::new, metal(MapColor.QUARTZ));
    public static final RegistryObject<Block> HULL_PANEL = block("hull_panel", Block::new, metal(MapColor.COLOR_LIGHT_GRAY));
    public static final RegistryObject<Block> HULL_DARK = block("hull_dark", Block::new, metal(MapColor.COLOR_GRAY));
    public static final RegistryObject<Block> HULL_WINDOW = block("hull_window", Block::new, glowing(metal(MapColor.COLOR_YELLOW), 12));
    public static final RegistryObject<Block> HULL_MARKING = block("hull_marking", Block::new, metal(MapColor.COLOR_BLACK));
    public static final RegistryObject<Block> DEFLECTOR_VIOLET = block("deflector_violet", Block::new, glowing(metal(MapColor.COLOR_PURPLE), 9));
    public static final RegistryObject<Block> DEFLECTOR_RING = block("deflector_ring", Block::new, metal(MapColor.COLOR_BROWN));
    public static final RegistryObject<Block> DEFLECTOR_DISH = block("deflector_dish", Block::new, glowing(metal(MapColor.COLOR_ORANGE), 13));
    public static final RegistryObject<Block> BUSSARD_COLLECTOR = block("bussard_collector",
        p -> new GlowParticleBlock(p, GlowParticleBlock.Kind.BUSSARD), glowing(metal(MapColor.COLOR_RED), 15));
    public static final RegistryObject<Block> NACELLE_GRILLE = block("nacelle_grille", Block::new, glowing(metal(MapColor.COLOR_LIGHT_BLUE), 14));

    public static final RegistryObject<Block> HULL_PLATING_SLAB = block("hull_plating_slab", SlabBlock::new, metal(MapColor.QUARTZ));
    public static final RegistryObject<Block> HULL_MARKING_SLAB = block("hull_marking_slab", SlabBlock::new, metal(MapColor.COLOR_BLACK));
    public static final RegistryObject<Block> HULL_PENNANT = block("hull_pennant", Block::new, metal(MapColor.COLOR_RED));
    public static final RegistryObject<Block> NAV_LIGHT_RED = block("nav_light_red", Block::new, glowing(glass(MapColor.COLOR_RED), 12));
    public static final RegistryObject<Block> NAV_LIGHT_GREEN = block("nav_light_green", Block::new, glowing(glass(MapColor.COLOR_GREEN), 12));

    // --- Refit (NCC-1701-A) ---
    public static final RegistryObject<Block> HULL_AZTEC = block("hull_aztec", Block::new, metal(MapColor.COLOR_LIGHT_BLUE));
    public static final RegistryObject<Block> DEFLECTOR_DISH_BLUE = block("deflector_dish_blue", Block::new, glowing(metal(MapColor.COLOR_BLUE), 14));
    public static final RegistryObject<Block> BUSSARD_RED = block("bussard_red",
        p -> new GlowParticleBlock(p, GlowParticleBlock.Kind.BUSSARD), glowing(metal(MapColor.COLOR_RED), 15));
    public static final RegistryObject<Block> IMPULSE_ENGINE = block("impulse_engine", Block::new, glowing(metal(MapColor.COLOR_RED), 13));
    public static final RegistryObject<Block> TORPEDO_LAUNCHER = block("torpedo_launcher",
        p -> new TorpedoLauncherBlock(p, false), glowing(metal(MapColor.COLOR_ORANGE), 9));
    public static final RegistryObject<Block> BRIDGE_RAILING_GRAY = block("bridge_railing_gray", RailingBlock::new,
        metal(MapColor.COLOR_GRAY).noOcclusion());
    public static final RegistryObject<Block> BRIDGE_TRIM_BLUE = block("bridge_trim_blue", Block::new, glowing(metal(MapColor.COLOR_BLUE), 8));

    // --- Excelsior (NCC-1701-B) ---
    public static final RegistryObject<Block> HULL_TEAL = block("hull_teal", Block::new, metal(MapColor.COLOR_CYAN));
    public static final RegistryObject<Block> BUSSARD_BLUE = block("bussard_blue",
        p -> new GlowParticleBlock(p, GlowParticleBlock.Kind.BUSSARD), glowing(metal(MapColor.COLOR_LIGHT_BLUE), 15));
    public static final RegistryObject<Block> BRIDGE_RAILING_WOOD = block("bridge_railing_wood", RailingBlock::new,
        metal(MapColor.COLOR_BROWN).noOcclusion());
    public static final RegistryObject<Block> BRIDGE_TRIM_CREAM = block("bridge_trim_cream", Block::new, glowing(metal(MapColor.SAND), 10));
    public static final RegistryObject<Block> BRIDGE_CARPET_TEAL = block("bridge_carpet_teal", Block::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1.5F, 6F).sound(SoundType.WOOL));
    public static final RegistryObject<Block> DECK_PLATE = block("deck_plate", Block::new, metal(MapColor.COLOR_GRAY));
    public static final RegistryObject<Block> BRIDGE_WALL_DARK = block("bridge_wall_dark", Block::new, metal(MapColor.COLOR_BLACK));
    public static final RegistryObject<Block> EXCELSIOR_CONSOLE = block("excelsior_console",
        p -> new VariantFacingBlock(p, Shapes.or(Block.box(0, 0, 0, 16, 10, 16), Block.box(0, 10, 6, 16, 14, 16))),
        glowing(metal(MapColor.COLOR_BLACK), 8).noOcclusion());
    public static final RegistryObject<Block> EXCELSIOR_DISPLAY = block("excelsior_display", VariantFacingBlock::new, glowing(metal(MapColor.COLOR_BLACK), 9));
    public static final RegistryObject<Block> LIGHT_PILLAR = block("light_pillar", Block::new, glowing(metal(MapColor.SAND), 12));
    public static final RegistryObject<Block> CAPTAINS_CHAIR_TEAL = block("captains_chair_teal", CaptainsChairBlock::new, metal(MapColor.COLOR_CYAN).noOcclusion());
    public static final RegistryObject<Block> STATION_CHAIR_TEAL = block("station_chair_teal",
        p -> new ChairBlock(p, ChairBlock.CHAIR_SHAPE),
        metal(MapColor.COLOR_CYAN).noOcclusion());
    public static final RegistryObject<Block> TURBOLIFT_DOOR_TEAL = block("turbolift_door_teal", FacingBlock::new, metal(MapColor.COLOR_CYAN));
    public static final RegistryObject<Block> HELM_CONSOLE_GRAY = block("helm_console_gray",
        p -> new ShapedFacingBlock(p, Block.box(0, 0, 2, 16, 12, 14)), glowing(metal(MapColor.COLOR_LIGHT_GRAY), 6).noOcclusion());

    // --- Next Generation (NCC-1701-C, -D, -E) ---
    public static final RegistryObject<Block> TNG_CARPET = block("tng_carpet", Block::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5F, 6F).sound(SoundType.WOOL));
    public static final RegistryObject<Block> PHASER_STRIP = block("phaser_strip",
        p -> new PhaserBlock(p, PhaserBlock.Kind.PHASER_STRIP), glowing(metal(MapColor.COLOR_ORANGE), 3));
    public static final RegistryObject<Block> TNG_WALL = block("tng_wall", Block::new, metal(MapColor.SAND));
    public static final RegistryObject<Block> TNG_CONSOLE = block("tng_console",
        p -> new ShapedFacingBlock(p, Shapes.or(Block.box(0, 0, 0, 16, 10, 16), Block.box(0, 10, 6, 16, 14, 16))),
        glowing(metal(MapColor.COLOR_BLACK), 9).noOcclusion());
    public static final RegistryObject<Block> TNG_CHAIR = block("tng_chair",
        p -> new ChairBlock(p, ChairBlock.CHAIR_SHAPE),
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.5F).sound(SoundType.WOOL).noOcclusion());
    public static final RegistryObject<Block> CAPTAINS_CHAIR_TNG = block("captains_chair_tng", CaptainsChairBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.5F).sound(SoundType.WOOL).noOcclusion());
    public static final RegistryObject<Block> WARP_CORE_TNG = block("warp_core_tng",
        p -> new WarpCoreBlock(p, 20_000), glowing(glass(MapColor.COLOR_BLUE), 15));
    public static final RegistryObject<Block> MSD_DISPLAY = block("msd_display", VariantFacingBlock::new, glowing(metal(MapColor.COLOR_BLACK), 10));
    public static final RegistryObject<Block> SITUATION_TABLE = block("situation_table",
        p -> new ShapedBlock(p, Block.box(0, 0, 0, 16, 14, 16)), glowing(metal(MapColor.COLOR_BLACK), 8).noOcclusion());
    public static final RegistryObject<Block> HOLODECK_GRID = block("holodeck_grid", Block::new, glowing(metal(MapColor.COLOR_BLACK), 6));
    public static final RegistryObject<Block> FORCE_FIELD = block("force_field", Block::new,
        glowing(glass(MapColor.COLOR_LIGHT_BLUE), 10).noOcclusion()
            .isViewBlocking((_, _, _) -> false).isSuffocating((_, _, _) -> false));
    public static final RegistryObject<Block> STAR_MAP = block("star_map", Block::new, glowing(metal(MapColor.COLOR_BLACK), 7));
    public static final RegistryObject<Block> BAR_COUNTER = block("bar_counter", Block::new, glowing(metal(MapColor.COLOR_PURPLE), 6));

    // --- NX-01 (Star Trek: Enterprise) ---
    public static final RegistryObject<Block> ENT_WALL = block("ent_wall", Block::new, metal(MapColor.COLOR_GRAY));
    public static final RegistryObject<Block> ENT_CONSOLE = block("ent_console",
        p -> new ShapedFacingBlock(p, Shapes.or(Block.box(0, 0, 0, 16, 10, 16), Block.box(0, 10, 6, 16, 14, 16))),
        glowing(metal(MapColor.COLOR_GRAY), 8).noOcclusion());
    public static final RegistryObject<Block> ENT_CHAIR = block("ent_chair",
        p -> new ChairBlock(p, ChairBlock.CHAIR_SHAPE),
        metal(MapColor.COLOR_GRAY).noOcclusion());
    public static final RegistryObject<Block> CAPTAINS_CHAIR_NX = block("captains_chair_nx", CaptainsChairBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion());
    public static final RegistryObject<Block> DECON_LIGHT = block("decon_light", Block::new, glowing(glass(MapColor.COLOR_LIGHT_BLUE), 14));

    // --- Brücke ---
    public static final RegistryObject<Block> BRIDGE_WALL = block("bridge_wall", Block::new, metal(MapColor.COLOR_LIGHT_GRAY));
    public static final RegistryObject<Block> BRIDGE_TRIM = block("bridge_trim", Block::new, glowing(metal(MapColor.COLOR_RED), 4));
    public static final RegistryObject<Block> BRIDGE_STATION = block("bridge_station",
        p -> new VariantFacingBlock(p, Shapes.or(Block.box(0, 0, 0, 16, 10, 16), Block.box(0, 10, 6, 16, 14, 16))),
        glowing(metal(MapColor.COLOR_BLACK), 9).noOcclusion());
    public static final RegistryObject<Block> BRIDGE_MONITOR = block("bridge_monitor", VariantFacingBlock::new, glowing(metal(MapColor.COLOR_GRAY), 8));
    public static final RegistryObject<Block> BRIDGE_RAILING = block("bridge_railing", RailingBlock::new,
        metal(MapColor.COLOR_RED).noOcclusion());
    public static final RegistryObject<Block> STATION_CHAIR = block("station_chair",
        p -> new ChairBlock(p, ChairBlock.CHAIR_SHAPE),
        metal(MapColor.COLOR_LIGHT_BLUE).noOcclusion());
    public static final RegistryObject<Block> HELM_CONSOLE = block("helm_console",
        p -> new ShapedFacingBlock(p, Block.box(0, 0, 2, 16, 12, 14)), glowing(metal(MapColor.COLOR_RED), 7).noOcclusion());
    public static final RegistryObject<Block> TURBOLIFT_DOOR = block("turbolift_door", FacingBlock::new, metal(MapColor.COLOR_RED));
    public static final RegistryObject<Block> TURBOLIFT_FRAME = block("turbolift_frame", Block::new, glowing(metal(MapColor.COLOR_ORANGE), 10));
    public static final RegistryObject<Block> STEP_SLAB = block("step_slab", SlabBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F, 6F).sound(SoundType.WOOL));

    // --- Transporterraum ---
    public static final RegistryObject<Block> TRANSPORTER_WALL = block("transporter_wall", Block::new, metal(MapColor.COLOR_PURPLE));
    public static final RegistryObject<Block> TRANSPORTER_PLATFORM = block("transporter_platform", Block::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5F, 6F).sound(SoundType.WOOL));
    public static final RegistryObject<Block> TRANSPORTER_ENERGIZER = block("transporter_energizer",
        p -> new GlowParticleBlock(p, GlowParticleBlock.Kind.ENERGIZER), glowing(glass(MapColor.COLOR_GREEN), 11));
    public static final RegistryObject<Block> TRANSPORTER_PILLAR = block("transporter_pillar", Block::new, glowing(metal(MapColor.COLOR_GRAY), 5));
    public static final RegistryObject<Block> TRANSPORTER_EMITTER = block("transporter_emitter", Block::new, glowing(glass(MapColor.COLOR_RED), 13));
    public static final RegistryObject<Block> TRANSPORTER_CONSOLE = block("transporter_console",
        p -> new ShapedFacingBlock(p, Block.box(1, 0, 1, 15, 14, 15)), glowing(metal(MapColor.COLOR_RED), 7).noOcclusion());

    // --- Möbel für die Decks ---
    public static final RegistryObject<Block> CONFERENCE_TABLE = block("conference_table",
        p -> new ShapedBlock(p, Block.box(0, 0, 0, 16, 14, 16)), metal(MapColor.COLOR_BLACK).noOcclusion());
    public static final RegistryObject<Block> TABLE_VIEWER = block("table_viewer",
        p -> new ShapedBlock(p, Block.box(4, 0, 4, 12, 9, 12)), glowing(metal(MapColor.COLOR_GRAY), 6).noOcclusion());
    public static final RegistryObject<Block> BIOBED = block("biobed", BiobedBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2F, 6F).sound(SoundType.WOOL).noOcclusion());
    public static final RegistryObject<Block> BIOBED_MONITOR = block("biobed_monitor", FacingBlock::new, glowing(metal(MapColor.COLOR_CYAN), 7).noOcclusion());
    public static final RegistryObject<Block> EXAM_TABLE = block("exam_table",
        p -> new ShapedFacingBlock(p, Block.box(0, 0, 0, 16, 14, 16)), metal(MapColor.COLOR_LIGHT_GRAY).noOcclusion());
    public static final RegistryObject<Block> MEDICAL_CABINET = block("medical_cabinet", FacingBlock::new, metal(MapColor.COLOR_CYAN));
    public static final RegistryObject<Block> SICKBAY_WALL = block("sickbay_wall", Block::new, metal(MapColor.COLOR_CYAN));
    public static final RegistryObject<Block> SICKBAY_FLOOR = block("sickbay_floor", Block::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(1.5F, 6F).sound(SoundType.WOOL));
    public static final RegistryObject<Block> CREW_BED = block("crew_bed",
        p -> new ShapedFacingBlock(p, Block.box(0, 0, 0, 16, 9, 16)),
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1F).sound(SoundType.WOOL).noOcclusion());
    public static final RegistryObject<Block> CREW_DESK = block("crew_desk",
        p -> new ShapedFacingBlock(p, Block.box(0, 0, 0, 16, 14, 16)), glowing(metal(MapColor.COLOR_GRAY), 4).noOcclusion());
    public static final RegistryObject<Block> CARGO_CONTAINER = block("cargo_container", Block::new, metal(MapColor.COLOR_YELLOW));
    public static final RegistryObject<Block> MESS_TABLE = block("mess_table",
        p -> new ShapedBlock(p, Block.box(1, 0, 1, 15, 14, 15)), metal(MapColor.SNOW).noOcclusion());

    // --- Innenausstattung ---
    public static final RegistryObject<Block> DECK_FLOOR = block("deck_floor", Block::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5F, 6F).sound(SoundType.WOOL));
    public static final RegistryObject<Block> BRIDGE_DECK = block("bridge_deck", Block::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5F, 6F).sound(SoundType.WOOL));
    public static final RegistryObject<Block> CORRIDOR_WALL = block("corridor_wall", Block::new, metal(MapColor.SAND));
    public static final RegistryObject<Block> CEILING_PANEL = block("ceiling_panel", Block::new, metal(MapColor.SNOW));
    public static final RegistryObject<Block> CEILING_LIGHT = block("ceiling_light", de.simon.spacemod.block.CeilingLightBlock::new,
        glass(MapColor.SNOW).noOcclusion().lightLevel(s -> s.getValue(de.simon.spacemod.block.CeilingLightBlock.LIT) ? 15 : 0)
            .emissiveRendering(s -> s.getValue(de.simon.spacemod.block.CeilingLightBlock.LIT)));
    public static final RegistryObject<Block> BRIDGE_CONSOLE = block("bridge_console", FacingBlock::new, glowing(metal(MapColor.COLOR_BLACK), 7));
    public static final RegistryObject<Block> ENGINEERING_CONSOLE = block("engineering_console", FacingBlock::new, glowing(metal(MapColor.COLOR_BLACK), 7));
    public static final RegistryObject<Block> LCARS_PANEL = block("lcars_panel", FacingBlock::new, glowing(metal(MapColor.COLOR_BLACK), 8));
    public static final RegistryObject<Block> VIEWSCREEN = block("viewscreen", FacingBlock::new, glowing(metal(MapColor.COLOR_BLACK), 6));
    public static final RegistryObject<Block> CAPTAINS_CHAIR = block("captains_chair", CaptainsChairBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion());
    // Warpkerne liefern Strom fürs Bordnetz (RF pro Tick und Block): NX-01 5, Originalserie bis B 10, C/D 20, E 30
    public static final RegistryObject<Block> WARP_CORE = block("warp_core",
        p -> new WarpCoreBlock(p, 10_000), glowing(glass(MapColor.COLOR_BLUE), 15));
    public static final RegistryObject<Block> WARP_CORE_NX = block("warp_core_nx",
        p -> new WarpCoreBlock(p, 5_000), glowing(glass(MapColor.COLOR_LIGHT_BLUE), 13));
    public static final RegistryObject<Block> WARP_CORE_E = block("warp_core_e",
        p -> new WarpCoreBlock(p, 30_000), glowing(glass(MapColor.COLOR_BLUE), 15));
    public static final RegistryObject<Block> PLASMA_CONDUIT = block("plasma_conduit", Block::new, glowing(metal(MapColor.COLOR_BLUE), 10));
    public static final RegistryObject<Block> TRANSPORTER_PAD = block("transporter_pad", TransporterPadBlock::new, glowing(metal(MapColor.METAL), 9).noOcclusion());
    public static final RegistryObject<Block> TURBOLIFT = block("turbolift", TurboliftBlock::new, glowing(metal(MapColor.COLOR_YELLOW), 8));
    // Turbolifte und Türen im Stil der anderen Schiffe (funktionieren wie der normale Turbolift)
    public static final RegistryObject<Block> TURBOLIFT_NX = block("turbolift_nx", TurboliftBlock::new, glowing(metal(MapColor.COLOR_GRAY), 6));
    public static final RegistryObject<Block> TURBOLIFT_REFIT = block("turbolift_refit", TurboliftBlock::new, glowing(metal(MapColor.COLOR_LIGHT_GRAY), 6));
    public static final RegistryObject<Block> TURBOLIFT_EXCELSIOR = block("turbolift_excelsior", TurboliftBlock::new, glowing(metal(MapColor.COLOR_CYAN), 6));
    public static final RegistryObject<Block> TURBOLIFT_TNG = block("turbolift_tng", TurboliftBlock::new, glowing(metal(MapColor.SAND), 6));
    public static final RegistryObject<Block> TURBOLIFT_DOOR_NX = block("turbolift_door_nx", FacingBlock::new, metal(MapColor.COLOR_GRAY));
    public static final RegistryObject<Block> TURBOLIFT_DOOR_REFIT = block("turbolift_door_refit", FacingBlock::new, metal(MapColor.COLOR_LIGHT_GRAY));
    public static final RegistryObject<Block> TURBOLIFT_DOOR_TNG = block("turbolift_door_tng", FacingBlock::new, metal(MapColor.SAND));

    // --- Waffen ---
    public static final RegistryObject<Block> PHASE_CANNON = block("phase_cannon",
        p -> new PhaserBlock(p, PhaserBlock.Kind.PHASE_CANNON), glowing(metal(MapColor.COLOR_GRAY), 4));
    public static final RegistryObject<Block> PHASER_BANK = block("phaser_bank",
        p -> new PhaserBlock(p, PhaserBlock.Kind.PHASER_BANK), glowing(metal(MapColor.COLOR_BLACK), 3));
    public static final RegistryObject<Block> PHASER_EMITTER = block("phaser_emitter",
        p -> new PhaserBlock(p, PhaserBlock.Kind.PHASER_EMITTER), glowing(metal(MapColor.COLOR_RED), 4));
    public static final RegistryObject<Block> QUANTUM_LAUNCHER = block("quantum_launcher",
        p -> new TorpedoLauncherBlock(p, true), glowing(metal(MapColor.COLOR_BLUE), 10));
    public static final RegistryObject<Block> WEAPONS_CONSOLE = block("weapons_console", WeaponsConsoleBlock::new,
        glowing(metal(MapColor.COLOR_RED), 7).noOcclusion());

    // --- Waffenkammer ---
    public static final RegistryObject<Block> RIFLE_RACK = block("rifle_rack", RifleRackBlock::new, metal(MapColor.COLOR_GRAY).noOcclusion());
    public static final RegistryObject<Block> PHASER_HOLDER = block("phaser_holder", PhaserHolderBlock::new, metal(MapColor.COLOR_GRAY).noOcclusion());
    public static final RegistryObject<Block> CHARGING_STATION = block("charging_station", ChargingStationBlock::new,
        metal(MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> s.getValue(ChargingStationBlock.RED) ? 9 : 6).emissiveRendering(_ -> true).noOcclusion());
    public static final RegistryObject<BlockEntityType<RifleRackBlock.RackBE>> RIFLE_RACK_BE = BLOCK_ENTITIES.register("rifle_rack",
        () -> new BlockEntityType<>(RifleRackBlock.RackBE::new, java.util.Set.of(RIFLE_RACK.get())));
    public static final RegistryObject<BlockEntityType<PhaserHolderBlock.HolderBE>> PHASER_HOLDER_BE = BLOCK_ENTITIES.register("phaser_holder",
        () -> new BlockEntityType<>(PhaserHolderBlock.HolderBE::new, java.util.Set.of(PHASER_HOLDER.get())));
    public static final RegistryObject<BlockEntityType<ChargingStationBlockEntity>> CHARGING_STATION_BE = BLOCK_ENTITIES.register("charging_station",
        () -> new BlockEntityType<>(ChargingStationBlockEntity::new, java.util.Set.of(CHARGING_STATION.get())));

    // --- Steuerkonsole für eigene fliegende Schiffe (nicht auf den Enterprises) ---
    public static final RegistryObject<Block> SHIP_HELM = block("ship_helm", de.simon.spacemod.block.ShipHelmBlock::new,
        glowing(metal(MapColor.COLOR_GRAY), 5).noOcclusion());

    // --- Uran und Reaktor ---
    public static final RegistryObject<Block> URANIUM_ORE = block("uranium_ore", Block::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3F, 3F).sound(SoundType.STONE).requiresCorrectToolForDrops().lightLevel(_ -> 3));
    public static final RegistryObject<Block> DEEPSLATE_URANIUM_ORE = block("deepslate_uranium_ore", Block::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5F, 3F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops().lightLevel(_ -> 3));
    public static final RegistryObject<Item> RAW_URANIUM = simpleItem("raw_uranium");
    public static final RegistryObject<Item> URANIUM_INGOT = simpleItem("uranium_ingot");

    public static final RegistryObject<Block> REACTOR_CASING = block("reactor_casing", Block::new, metal(MapColor.COLOR_GRAY));
    public static final RegistryObject<Block> REACTOR_GLASS = block("reactor_glass", net.minecraft.world.level.block.TransparentBlock::new,
        glass(MapColor.COLOR_LIGHT_GREEN).strength(3F, 9F).noOcclusion());
    public static final RegistryObject<Block> REACTOR_CONTROLLER = block("reactor_controller", de.simon.spacemod.block.ReactorControllerBlock::new,
        metal(MapColor.COLOR_GREEN).lightLevel(s -> s.getValue(de.simon.spacemod.block.ReactorControllerBlock.LIT) ? 12 : 2));
    public static final RegistryObject<BlockEntityType<de.simon.spacemod.block.entity.ReactorControllerBlockEntity>> REACTOR_BE =
        BLOCK_ENTITIES.register("reactor_controller", () -> new BlockEntityType<>(de.simon.spacemod.block.entity.ReactorControllerBlockEntity::new,
            java.util.Set.of(REACTOR_CONTROLLER.get())));

    // --- Luftschleusen: einzige Tür, die bei fliegenden Schiffen als Hülle zählt ---
    public static final RegistryObject<Block> AIRLOCK = block("airlock", p -> new de.simon.spacemod.block.AirlockBlock(p, 2),
        metal(MapColor.COLOR_LIGHT_GRAY).noOcclusion());
    public static final RegistryObject<Block> AIRLOCK_SMALL = block("airlock_small", p -> new de.simon.spacemod.block.AirlockBlock(p, 1),
        metal(MapColor.COLOR_LIGHT_GRAY).noOcclusion());

    // --- Weltraum: Asteroiden mit Rohstoffen für Warpkerne ---
    public static final RegistryObject<Block> DILITHIUM_ORE = block("dilithium_ore", Block::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5F, 6F).sound(SoundType.DEEPSLATE)
            .requiresCorrectToolForDrops().lightLevel(_ -> 5));
    public static final RegistryObject<Block> ANTIMATTER_CLUSTER = block("antimatter_cluster", Block::new,
        glowing(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(5F, 6F).sound(SoundType.AMETHYST)
            .requiresCorrectToolForDrops(), 10));
    public static final RegistryObject<Block> DEUTERIUM_ICE = block("deuterium_ice", Block::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(2.5F, 3F).sound(SoundType.GLASS).friction(0.98F));
    public static final RegistryObject<Item> DILITHIUM_CRYSTAL = simpleItem("dilithium_crystal");
    public static final RegistryObject<Item> ANTIMATTER = simpleItem("antimatter");
    public static final RegistryObject<Item> DEUTERIUM = simpleItem("deuterium");
    /** Magnetische Eindämmung für Antimaterie (Zwischenprodukt der Warpkerne). */
    public static final RegistryObject<Item> ANTIMATTER_POD = simpleItem("antimatter_pod");

    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES =
        DeferredRegister.create(Registries.FEATURE, SimonSpaceMod.MODID);
    public static final RegistryObject<net.minecraft.world.level.levelgen.feature.Feature<?>> SHUTTLE_WRECK = FEATURES.register("shuttle_wreck",
        () -> new de.simon.spacemod.world.ShuttleWreckFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<net.minecraft.world.level.levelgen.feature.Feature<?>> ASTEROID = FEATURES.register("asteroid",
        () -> new de.simon.spacemod.space.AsteroidFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));

    private static RegistryObject<Item> simpleItem(String name) {
        RegistryObject<Item> item = ITEMS.register(name, () -> new Item(new Item.Properties().setId(ITEMS.key(name))));
        TAB_ITEMS.add(item);
        return item;
    }

    // --- Zweiseitige Wand / Decke zwischen verschiedenen Räumen ---
    public static final RegistryObject<Block> DUAL_WALL = block("dual_wall", de.simon.spacemod.block.DualWallBlock::new, metal(MapColor.COLOR_LIGHT_GRAY));
    public static final RegistryObject<Block> DUAL_FLOOR = block("dual_floor", de.simon.spacemod.block.DualFloorBlock::new, metal(MapColor.COLOR_LIGHT_GRAY));

    // --- Türen: 2 x 2, je Epoche normal / Sicherheit / Kommando ---
    public static final String[] DOOR_ERAS = {"nx", "tos", "refit", "tng"};
    public static final java.util.Map<String, RegistryObject<Block>> DOORS = new java.util.LinkedHashMap<>();
    static {
        for (String era : DOOR_ERAS)
            for (StarshipDoorBlock.Kind kind : StarshipDoorBlock.Kind.values()) {
                String name = "door_" + era + "_" + kind.name().toLowerCase();
                DOORS.put(era + "_" + kind.name().toLowerCase(), block(name, p -> new StarshipDoorBlock(p, kind),
                    metal(kind == StarshipDoorBlock.Kind.COMMAND ? MapColor.COLOR_RED : kind == StarshipDoorBlock.Kind.SECURITY
                        ? MapColor.COLOR_YELLOW : MapColor.COLOR_LIGHT_GRAY).noOcclusion()));
            }
    }

    public static Block door(String era, StarshipDoorBlock.Kind kind) {
        return DOORS.get(era + "_" + kind.name().toLowerCase()).get();
    }

    public static final RegistryObject<BlockEntityType<DoorBlockEntity>> DOOR_BE = BLOCK_ENTITIES.register("door",
        () -> new BlockEntityType<>(DoorBlockEntity::new, DOORS.values().stream().map(RegistryObject::get).collect(java.util.stream.Collectors.toSet())));

    // --- Schlüsselkarten: Sicherheit (gelb), Kommando (rot), Captain (öffnet beides) ---
    public static final RegistryObject<Item> KEYCARD_SECURITY = keycard("keycard_security", KeycardItem.Level.SECURITY);
    public static final RegistryObject<Item> KEYCARD_COMMAND = keycard("keycard_command", KeycardItem.Level.COMMAND);
    public static final RegistryObject<Item> KEYCARD_CAPTAIN = keycard("keycard_captain", KeycardItem.Level.CAPTAIN);

    private static RegistryObject<Item> keycard(String name, KeycardItem.Level level) {
        RegistryObject<Item> item = ITEMS.register(name, () -> new KeycardItem(new Item.Properties().setId(ITEMS.key(name)), level));
        TAB_ITEMS.add(item);
        return item;
    }

    // --- Items ---
    public static final RegistryObject<Item> COMMUNICATOR = ITEMS.register("communicator",
        () -> new CommunicatorItem(new Item.Properties().setId(ITEMS.key("communicator")).stacksTo(1)));
    static { TAB_ITEMS.add(COMMUNICATOR); }

    // --- Handwaffen: Ladung (RF) und Modus am Item ---
    public static final RegistryObject<DataComponentType<Integer>> WEAPON_ENERGY = COMPONENTS.register("weapon_energy",
        () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final RegistryObject<DataComponentType<Integer>> WEAPON_MODE = COMPONENTS.register("weapon_mode",
        () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    /**
     * Handphaser und Phasergewehre je Epoche (Gewehre nur, wo es im Original welche gab):
     * NX-01: Phasenpistole EM-33 und Phasengewehr der MACOs; Originalserie: Typ-2-Phaser und
     * Phasergewehr; Kinofilme (A, B): Handphaser; Next Generation (C, D): Typ-2 und Typ-3-Gewehr;
     * Enterprise-E: Typ-2 und Kompressions-Phasergewehr.
     */
    public static final RegistryObject<Item> PHASER_NX = weapon("phaser_nx", 1, false, 0x7FC8FF, 0xE8F8FF, true);
    public static final RegistryObject<Item> PHASER_TOS = weapon("phaser_tos", 2, false, 0x4F8CFF, 0xD8E6FF, false);
    public static final RegistryObject<Item> PHASER_MOVIE = weapon("phaser_movie", 3, false, 0xFF4A20, 0xFFD8B0, false);
    public static final RegistryObject<Item> PHASER_TNG = weapon("phaser_tng", 4, false, 0xFF8418, 0xFFE8B0, false);
    public static final RegistryObject<Item> PHASER_FC = weapon("phaser_fc", 5, false, 0xFF6A18, 0xFFF0C8, false);
    public static final RegistryObject<Item> RIFLE_NX = weapon("rifle_nx", 1, true, 0x7FC8FF, 0xE8F8FF, true);
    public static final RegistryObject<Item> RIFLE_TOS = weapon("rifle_tos", 2, true, 0x4F8CFF, 0xD8E6FF, false);
    public static final RegistryObject<Item> RIFLE_TNG = weapon("rifle_tng", 4, true, 0xFF8418, 0xFFE8B0, false);
    public static final RegistryObject<Item> RIFLE_FC = weapon("rifle_fc", 5, true, 0xFF6A18, 0xFFF0C8, false);

    private static RegistryObject<Item> weapon(String name, int tier, boolean rifle, int color, int core, boolean pulse) {
        RegistryObject<Item> item = ITEMS.register(name,
            () -> new HandWeaponItem(new Item.Properties().setId(ITEMS.key(name)), tier, rifle, color, core, pulse));
        TAB_ITEMS.add(item);
        return item;
    }

    // --- Uniformen: je Epoche Kommando, Wissenschaft und Betrieb, dazu Hosen und Stiefel ---
    public static final String[] UNIFORM_ERAS = {"nx", "tos", "movie", "tng", "fc"};
    public static final String[] UNIFORM_DIVISIONS = {"command", "science", "operations"};
    static {
        for (String era : UNIFORM_ERAS)
            for (String div : UNIFORM_DIVISIONS) armor("uniform_" + era + "_" + div, ArmorType.CHESTPLATE);
        armor("trousers_nx", ArmorType.LEGGINGS);
        armor("trousers_black", ArmorType.LEGGINGS);
        armor("boots_starfleet", ArmorType.BOOTS);
    }

    /** Raumanzug: Kreativflug, solange geladen. */
    public static final RegistryObject<Item> SPACE_SUIT = spaceSuit();

    private static RegistryObject<Item> spaceSuit() {
        String name = "space_suit";
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, name));
        ArmorMaterial material = new ArmorMaterial(20,
            Map.of(ArmorType.BOOTS, 2, ArmorType.LEGGINGS, 5, ArmorType.CHESTPLATE, 6, ArmorType.HELMET, 2, ArmorType.BODY, 6),
            10, SoundEvents.ARMOR_EQUIP_IRON, 1F, 0F, ItemTags.REPAIRS_IRON_ARMOR, asset);
        RegistryObject<Item> item = ITEMS.register(name, () -> new de.simon.spacemod.item.SpaceSuitItem(
            new Item.Properties().setId(ITEMS.key(name)).humanoidArmor(material, ArmorType.CHESTPLATE).stacksTo(1)));
        TAB_ITEMS.add(item);
        return item;
    }

    private static void armor(String name, ArmorType type) {
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, name));
        ArmorMaterial material = new ArmorMaterial(8,
            Map.of(ArmorType.BOOTS, 1, ArmorType.LEGGINGS, 2, ArmorType.CHESTPLATE, 3, ArmorType.HELMET, 1, ArmorType.BODY, 3),
            12, SoundEvents.ARMOR_EQUIP_LEATHER, 0F, 0F, ItemTags.REPAIRS_LEATHER_ARMOR, asset);
        RegistryObject<Item> item = ITEMS.register(name,
            () -> new Item(new Item.Properties().setId(ITEMS.key(name)).humanoidArmor(material, type)));
        TAB_ITEMS.add(item);
    }

    // --- Entities ---
    /** Unsichtbarer Sitz für die Stühle. */
    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
        () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.001F, 0.001F)
            .noSummon().clientTrackingRange(10).build(ENTITIES.key("seat")));

    /** Photonen-/Quantentorpedo im Flug. */
    public static final RegistryObject<EntityType<TorpedoEntity>> TORPEDO = ENTITIES.register("torpedo",
        () -> EntityType.Builder.<TorpedoEntity>of(TorpedoEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
            .noSummon().noSave().clientTrackingRange(16).updateInterval(1).build(ENTITIES.key("torpedo")));

    /** Sichtbarer Phaserstrahl (Schiffsphaser, Handphaser, Gewehre). */
    public static final RegistryObject<EntityType<de.simon.spacemod.entity.PhaserBeamEntity>> PHASER_BEAM = ENTITIES.register("phaser_beam",
        () -> EntityType.Builder.<de.simon.spacemod.entity.PhaserBeamEntity>of(de.simon.spacemod.entity.PhaserBeamEntity::new, MobCategory.MISC)
            .sized(0.2F, 0.2F).noSummon().noSave().clientTrackingRange(16).updateInterval(1).build(ENTITIES.key("phaser_beam")));

    /** Fliegendes Schiff (mit der Steuerkonsole abgehoben). */
    public static final RegistryObject<EntityType<de.simon.spacemod.entity.FlyingShipEntity>> FLYING_SHIP = ENTITIES.register("flying_ship",
        () -> EntityType.Builder.<de.simon.spacemod.entity.FlyingShipEntity>of(de.simon.spacemod.entity.FlyingShipEntity::new, MobCategory.MISC)
            .sized(1F, 1F).noSummon().clientTrackingRange(16).updateInterval(1).build(ENTITIES.key("flying_ship")));

    // --- Menüs ---
    public static final RegistryObject<MenuType<TurboliftMenu>> TURBOLIFT_MENU = MENUS.register("turbolift",
        () -> IForgeMenuType.create(TurboliftMenu::new));
    public static final RegistryObject<MenuType<de.simon.spacemod.reactor.ReactorMenu>> REACTOR_MENU = MENUS.register("reactor",
        () -> IForgeMenuType.create(de.simon.spacemod.reactor.ReactorMenu::new));

    // --- Energieverteilung (Maschinenraum), Alarm und Schilde ---
    public static final RegistryObject<Block> POWER_CONSOLE = block("power_console",
        p -> new de.simon.spacemod.defense.ConsoleBlock(p, de.simon.spacemod.defense.ConsoleBlock.Kind.POWER),
        glowing(metal(MapColor.COLOR_ORANGE), 6).noOcclusion());
    public static final RegistryObject<Block> SHIELD_CONSOLE = block("shield_console",
        p -> new de.simon.spacemod.defense.ConsoleBlock(p, de.simon.spacemod.defense.ConsoleBlock.Kind.SHIELD),
        glowing(metal(MapColor.COLOR_LIGHT_BLUE), 6).noOcclusion());
    public static final RegistryObject<BlockEntityType<de.simon.spacemod.defense.PowerConsoleBlockEntity>> POWER_CONSOLE_BE =
        BLOCK_ENTITIES.register("power_console", () -> new BlockEntityType<>(de.simon.spacemod.defense.PowerConsoleBlockEntity::new,
            java.util.Set.of(POWER_CONSOLE.get())));
    public static final RegistryObject<BlockEntityType<de.simon.spacemod.defense.ShieldConsoleBlockEntity>> SHIELD_CONSOLE_BE =
        BLOCK_ENTITIES.register("shield_console", () -> new BlockEntityType<>(de.simon.spacemod.defense.ShieldConsoleBlockEntity::new,
            java.util.Set.of(SHIELD_CONSOLE.get())));
    public static final RegistryObject<MenuType<de.simon.spacemod.defense.ConsoleMenu>> POWER_CONSOLE_MENU = MENUS.register("power_console",
        () -> IForgeMenuType.create((id, inv, buf) -> new de.simon.spacemod.defense.ConsoleMenu(ModBlocks.POWER_CONSOLE_MENU.get(), id,
            new net.minecraft.world.inventory.SimpleContainerData(10), b -> {}, p -> true)));
    public static final RegistryObject<MenuType<de.simon.spacemod.defense.ConsoleMenu>> SHIELD_CONSOLE_MENU = MENUS.register("shield_console",
        () -> IForgeMenuType.create((id, inv, buf) -> new de.simon.spacemod.defense.ConsoleMenu(ModBlocks.SHIELD_CONSOLE_MENU.get(), id,
            new net.minecraft.world.inventory.SimpleContainerData(7), b -> {}, p -> true)));
    public static final RegistryObject<Block> ALARM_LIGHT = block("alarm_light", de.simon.spacemod.defense.AlarmLightBlock::new,
        metal(MapColor.COLOR_RED).noOcclusion().lightLevel(s -> s.getValue(de.simon.spacemod.defense.AlarmLightBlock.LIT) ? 14 : 0)
            .emissiveRendering(s -> s.getValue(de.simon.spacemod.defense.AlarmLightBlock.LIT)));
    /** Schildemitter je Schiff: NCC-1701 (Stufe 1) bis NCC-1701-E (Stufe 6). */
    public static final String[] SHIELD_EMITTERS = {"tos", "refit", "excelsior", "c", "d", "e"};
    public static final java.util.List<RegistryObject<Block>> SHIELD_EMITTER = new java.util.ArrayList<>();
    static {
        for (int i = 0; i < SHIELD_EMITTERS.length; i++) {
            int tier = i + 1;
            SHIELD_EMITTER.add(block("shield_emitter_" + SHIELD_EMITTERS[i], p -> new de.simon.spacemod.defense.ShieldEmitterBlock(p, tier),
                glowing(metal(MapColor.COLOR_LIGHT_BLUE), 4 + tier)));
        }
    }
    public static final RegistryObject<EntityType<de.simon.spacemod.defense.ShieldFlashEntity>> SHIELD_FLASH = ENTITIES.register("shield_flash",
        () -> EntityType.Builder.<de.simon.spacemod.defense.ShieldFlashEntity>of(de.simon.spacemod.defense.ShieldFlashEntity::new, MobCategory.MISC)
            .sized(0.2F, 0.2F).noSummon().noSave().clientTrackingRange(12).updateInterval(20).build(ENTITIES.key("shield_flash")));

    // --- Batterie (behält ihre Ladung als Item) ---
    public static final RegistryObject<Block> BATTERY = BLOCKS.register("battery", () -> new de.simon.spacemod.power.BatteryBlock(
        metal(MapColor.COLOR_YELLOW).setId(BLOCKS.key("battery"))));
    public static final RegistryObject<Item> BATTERY_ITEM = batteryItem();
    public static final RegistryObject<BlockEntityType<de.simon.spacemod.power.BatteryBlockEntity>> BATTERY_BE =
        BLOCK_ENTITIES.register("battery", () -> new BlockEntityType<>(de.simon.spacemod.power.BatteryBlockEntity::new, java.util.Set.of(BATTERY.get())));

    private static RegistryObject<Item> batteryItem() {
        RegistryObject<Item> item = ITEMS.register("battery", () -> new BlockItem(BATTERY.get(),
                new Item.Properties().setId(ITEMS.key("battery")).useBlockDescriptionPrefix().stacksTo(1)) {
            @Override
            public void appendHoverText(net.minecraft.world.item.ItemStack stack, Item.TooltipContext ctx,
                                        net.minecraft.world.item.component.TooltipDisplay display,
                                        java.util.function.Consumer<net.minecraft.network.chat.Component> out,
                                        net.minecraft.world.item.TooltipFlag flag) {
                de.simon.spacemod.power.BatteryBlock.tooltip(stack, out);
            }

            @Override
            public boolean isBarVisible(net.minecraft.world.item.ItemStack stack) {
                return true;
            }

            @Override
            public int getBarWidth(net.minecraft.world.item.ItemStack stack) {
                return Math.round(13F * stack.getOrDefault(WEAPON_ENERGY.get(), 0) / de.simon.spacemod.power.BatteryBlockEntity.CAPACITY);
            }

            @Override
            public int getBarColor(net.minecraft.world.item.ItemStack stack) {
                return 0x40C0FF;
            }
        });
        TAB_ITEMS.add(item);
        return item;
    }

    // --- Schiffsteil-Fertigung: baut alle Blöcke der Mod ---
    public static final RegistryObject<Block> SHIP_PART_ASSEMBLY = block("ship_part_assembly", de.simon.spacemod.assembly.AssemblyBlock::new,
        metal(MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> s.getValue(de.simon.spacemod.assembly.AssemblyBlock.LIT) ? 10 : 3));
    public static final RegistryObject<BlockEntityType<de.simon.spacemod.assembly.AssemblyBlockEntity>> ASSEMBLY_BE =
        BLOCK_ENTITIES.register("ship_part_assembly", () -> new BlockEntityType<>(de.simon.spacemod.assembly.AssemblyBlockEntity::new,
            java.util.Set.of(SHIP_PART_ASSEMBLY.get())));
    public static final RegistryObject<MenuType<de.simon.spacemod.assembly.AssemblyMenu>> ASSEMBLY_MENU = MENUS.register("ship_part_assembly",
        () -> IForgeMenuType.create(de.simon.spacemod.assembly.AssemblyMenu::new));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("space", () -> CreativeModeTab.builder()
        .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
        .title(Component.translatable("itemGroup.simonspacemod.space"))
        .icon(() -> CAPTAINS_CHAIR.get().asItem().getDefaultInstance())
        .displayItems((_, output) -> TAB_ITEMS.forEach(item -> output.accept(item.get())))
        .build());

    public static void register(BusGroup modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        GAME_RULES.register(modBus);
        FEATURES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
    }

    private static BlockBehaviour.Properties metal(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3F, 9F).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties glass(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1F, 3F).sound(SoundType.GLASS);
    }

    private static BlockBehaviour.Properties glowing(BlockBehaviour.Properties props, int light) {
        return props.lightLevel(_ -> light).emissiveRendering(_ -> true);
    }

    private static <B extends Block> RegistryObject<Block> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                                 BlockBehaviour.Properties props) {
        RegistryObject<Block> block = BLOCKS.register(name, () -> factory.apply(props.setId(BLOCKS.key(name))));
        RegistryObject<Item> item = ITEMS.register(name,
            () -> new BlockItem(block.get(), new Item.Properties().setId(ITEMS.key(name)).useBlockDescriptionPrefix()));
        TAB_ITEMS.add(item);
        return block;
    }
}
