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
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SimonSpaceMod.MODID);

    private static final List<RegistryObject<? extends Item>> TAB_ITEMS = new ArrayList<>();

    // --- Außenhülle ---
    public static final RegistryObject<Block> HULL_PLATING = block("hull_plating", Block::new, metal(MapColor.QUARTZ));
    public static final RegistryObject<Block> HULL_PANEL = block("hull_panel", Block::new, metal(MapColor.COLOR_LIGHT_GRAY));
    public static final RegistryObject<Block> HULL_DARK = block("hull_dark", Block::new, metal(MapColor.COLOR_GRAY));
    public static final RegistryObject<Block> HULL_WINDOW = block("hull_window", Block::new, glowing(metal(MapColor.COLOR_YELLOW), 12));
    public static final RegistryObject<Block> HULL_MARKING = block("hull_marking", Block::new, metal(MapColor.COLOR_BLACK));
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
    public static final RegistryObject<Block> TORPEDO_LAUNCHER = block("torpedo_launcher", Block::new, glowing(metal(MapColor.COLOR_ORANGE), 9));
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
    public static final RegistryObject<Block> PHASER_STRIP = block("phaser_strip", Block::new, glowing(metal(MapColor.COLOR_ORANGE), 3));
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
        p -> new GlowParticleBlock(p, GlowParticleBlock.Kind.WARP_CORE), glowing(glass(MapColor.COLOR_BLUE), 15));
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
    public static final RegistryObject<Block> CEILING_LIGHT = block("ceiling_light", Block::new, glowing(glass(MapColor.SNOW), 15));
    public static final RegistryObject<Block> BRIDGE_CONSOLE = block("bridge_console", FacingBlock::new, glowing(metal(MapColor.COLOR_BLACK), 7));
    public static final RegistryObject<Block> ENGINEERING_CONSOLE = block("engineering_console", FacingBlock::new, glowing(metal(MapColor.COLOR_BLACK), 7));
    public static final RegistryObject<Block> LCARS_PANEL = block("lcars_panel", FacingBlock::new, glowing(metal(MapColor.COLOR_BLACK), 8));
    public static final RegistryObject<Block> VIEWSCREEN = block("viewscreen", FacingBlock::new, glowing(metal(MapColor.COLOR_BLACK), 6));
    public static final RegistryObject<Block> CAPTAINS_CHAIR = block("captains_chair", CaptainsChairBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion());
    public static final RegistryObject<Block> WARP_CORE = block("warp_core",
        p -> new GlowParticleBlock(p, GlowParticleBlock.Kind.WARP_CORE), glowing(glass(MapColor.COLOR_BLUE), 15));
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

    // --- Items ---
    public static final RegistryObject<Item> COMMUNICATOR = ITEMS.register("communicator",
        () -> new CommunicatorItem(new Item.Properties().setId(ITEMS.key("communicator")).stacksTo(1)));
    static { TAB_ITEMS.add(COMMUNICATOR); }

    // --- Entities ---
    /** Unsichtbarer Sitz für die Stühle. */
    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
        () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.001F, 0.001F)
            .noSummon().clientTrackingRange(10).build(ENTITIES.key("seat")));

    // --- Menüs ---
    public static final RegistryObject<MenuType<TurboliftMenu>> TURBOLIFT_MENU = MENUS.register("turbolift",
        () -> IForgeMenuType.create(TurboliftMenu::new));

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
        TABS.register(modBus);
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
