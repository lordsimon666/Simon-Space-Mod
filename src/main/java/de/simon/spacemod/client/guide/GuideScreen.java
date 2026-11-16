package de.simon.spacemod.client.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import de.simon.spacemod.guide.GuideStructure;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Handbuch der Sternenflotte, aufgebaut wie das Lexica Botania: ein offenes dunkelblaues Buch mit Sternen
 * und zwei hellen Seiten. Die Startseite hat die Einleitung und die Kategorien als Symbole, eine Kategorie
 * listet ihre Kapitel, ein Kapitel liest man zwei Seiten auf einmal. Seiten zeigen Text, ein Item, ein Rezept
 * (Werkbank, Ofen oder Schiffsteil-Fertigung) oder einen Multiblock in 3D, den man mit der Maus drehen,
 * Schicht für Schicht aufbauen und als Bauplan in die Welt stellen kann. Das Buch öffnet dort, wo es
 * geschlossen wurde.
 */
public class GuideScreen extends Screen {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 186;
    private static final int PAGE_WIDTH = 126;
    private static final int PAGE_TOP = 14;
    private static final int PAGE_HEIGHT = 148;
    private static final int LEFT_PAGE = 16;
    private static final int RIGHT_PAGE = 158;
    private static final int LINE = 9;
    private static final int HEADER = 16;
    private static final int CATEGORY_COLUMNS = 4;
    private static final int CATEGORY_CELL = 30;
    private static final int ENTRY_ROW = 18;
    private static final int ENTRY_ROWS = 7;
    private static final int SPOTLIGHT_HEIGHT = 48;
    private static final int CRAFTING_HEIGHT = 70;
    private static final int VIEW_HEIGHT = 92;
    private static final int STRUCTURE_HEIGHT = 130;

    // Weltraum: dunkelblauer Einband mit Sternen, helle Seiten wie eine Sternenkarte, goldene Titel,
    // orange Akzentlinien (wie die LCARS-Anzeigen) und Sternenhimmel hinter Items und Strukturen
    private static final int COVER = 0xFF0A1433;
    private static final int COVER_LIGHT = 0xFF14245A;
    private static final int PAGE = 0xFFE9EEF8;
    private static final int PAGE_SHADE = 0xFFC8D3EC;
    private static final int TEXT = 0xFF1A2440;
    private static final int HEADING = 0xFF1C3C96;
    private static final int TITLE = 0xFFFFD45A;
    private static final int SUBTITLE = 0xFFAFC8FF;
    private static final int SLOT = 0xFFD5DEF2;
    private static final int SLOT_BORDER = 0xFF6F7FA8;
    private static final int VIEW = 0xFF0E1A3E;
    private static final int VIEW_HOVER = 0xFF1B2D63;
    private static final int ACCENT = 0xFFE8A040;
    private static final int BUTTON = 0xFF1C3C96;
    private static final int BUTTON_HOVER = 0xFF3A66D0;
    private static final int HIGHLIGHT = 0x40FFFFFF;

    private enum View { HOME, CATEGORY, ENTRY }

    /** A page as it is drawn: the chapter's title on its first page, what it shows and the text lines that fit. */
    private record Laid(@Nullable Component header, @Nullable GuideBook.Page special, List<FormattedCharSequence> lines) {
    }

    /** Something to click on this frame, and what it does. */
    private record Hotspot(int x0, int y0, int x1, int y1, Runnable action) {
        boolean contains(double x, double y) {
            return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
        }
    }

    /** How a structure is looked at: turned, tilted, zoomed, moved and how many layers are shown (0 for all). */
    private static final class StructureView {
        float yaw = 45;
        float pitch = 30;
        float zoom = 1;
        // How far it is moved from the middle of its box, in GUI pixels
        float panX;
        float panY;
        int layers;
    }

    /** Where a structure's 3D picture is on screen this frame, for dragging and zooming it. */
    private record ViewBox(int x0, int y0, int x1, int y1, StructureView view) {
        boolean contains(double x, double y) {
            return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
        }
    }

    // Where the book was when it was closed
    private static View lastView = View.HOME;
    @Nullable
    private static GuideBook.Category lastCategory;
    @Nullable
    private static GuideBook.Entry lastEntry;
    private static int lastSpread;
    private static final Map<GuideStructure, StructureView> VIEWS = new HashMap<>();

    private View view = lastView;
    @Nullable
    private GuideBook.Category category = lastCategory;
    @Nullable
    private GuideBook.Entry entry = lastEntry;
    // The left page of the two shown, or the first chapter shown in a category
    private int spread = lastSpread;
    private List<Laid> pages = List.of();
    private int left;
    private int top;
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final List<ViewBox> viewBoxes = new ArrayList<>();
    @Nullable
    private StructureView dragging;
    // Dragging with the right button moves the structure instead of turning it
    private boolean panning;
    @Nullable
    private ItemStack hoveredItem;
    private List<Component> hoveredText = List.of();

    public GuideScreen() {
        super(Component.translatable("item.simonspacemod.guide_book"));
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        if (this.view == View.ENTRY && this.entry != null)
            this.pages = this.layout(this.entry);
        else if (this.view == View.ENTRY)
            this.view = View.HOME;
    }

    private void open(View view, @Nullable GuideBook.Category category, @Nullable GuideBook.Entry entry) {
        this.view = view;
        this.category = category;
        this.entry = entry;
        this.spread = 0;
        this.pages = entry == null ? List.of() : this.layout(entry);
        this.remember();
    }

    private void back() {
        if (this.view == View.ENTRY)
            this.open(View.CATEGORY, this.category, null);
        else if (this.view == View.CATEGORY)
            this.open(View.HOME, null, null);
    }

    private void turn(int pages) {
        int count = switch (this.view) {
            case ENTRY -> this.pages.size();
            case CATEGORY -> this.category == null ? 0 : this.category.entries().size();
            case HOME -> 0;
        };
        int step = this.view == View.ENTRY ? 2 : ENTRY_ROWS;
        int next = this.spread + pages * step;
        if (next >= 0 && next < count)
            this.spread = next;
        this.remember();
    }

    private void remember() {
        lastView = this.view;
        lastCategory = this.category;
        lastEntry = this.entry;
        lastSpread = this.spread;
    }

    /** Splits a chapter into pages: every page of it starts a new one, and text that doesn't fit goes on over the next. */
    private List<Laid> layout(GuideBook.Entry entry) {
        List<Laid> laid = new ArrayList<>();
        boolean first = true;
        for (GuideBook.Page page : entry.pages()) {
            List<FormattedCharSequence> lines = page.text() == null ? List.of()
                : this.font.split(Component.translatable(GuideBook.key(page.text())), PAGE_WIDTH);
            GuideBook.Page special = page instanceof GuideBook.Text ? null : page;
            int index = 0;
            do {
                Component header = first ? entry.title() : null;
                int space = PAGE_HEIGHT - (header == null ? 0 : HEADER) - height(special);
                int fit = Math.max(0, space / LINE);
                // A page with nothing special always takes at least a line, so the text can't get stuck
                if (special == null)
                    fit = Math.max(1, fit);
                int end = Math.min(lines.size(), index + fit);
                laid.add(new Laid(header, special, lines.subList(index, end)));
                index = end;
                first = false;
                special = null;
            } while (index < lines.size());
        }
        return laid;
    }

    private static int height(@Nullable GuideBook.Page page) {
        return switch (page) {
            case null -> 0;
            case GuideBook.Spotlight _ -> SPOTLIGHT_HEIGHT;
            case GuideBook.Crafting _ -> CRAFTING_HEIGHT;
            case GuideBook.Structure _ -> STRUCTURE_HEIGHT;
            case GuideBook.Text _ -> 0;
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.hotspots.clear();
        this.viewBoxes.clear();
        this.hoveredItem = null;
        this.hoveredText = List.of();
        this.book(graphics);
        switch (this.view) {
            case HOME -> this.home(graphics, mouseX, mouseY);
            case CATEGORY -> this.category(graphics, mouseX, mouseY);
            case ENTRY -> this.entry(graphics, mouseX, mouseY);
        }
        if (this.hoveredItem != null)
            graphics.setTooltipForNextFrame(this.font, this.hoveredItem, mouseX, mouseY);
        else if (!this.hoveredText.isEmpty())
            graphics.setComponentTooltipForNextFrame(this.font, this.hoveredText, mouseX, mouseY);
    }

    /** Das offene Buch: dunkelblauer Einband mit funkelnden Sternen, zwei Seiten und der Rücken dazwischen. */
    private void book(GuiGraphicsExtractor graphics) {
        int x = this.left, y = this.top;
        graphics.fill(x - 4, y - 4, x + WIDTH + 4, y + HEIGHT + 4, 0xFF050A1C);
        graphics.fill(x - 3, y - 3, x + WIDTH + 3, y + HEIGHT + 3, COVER);
        stars(graphics, x - 3, y - 3, x + WIDTH + 3, y + HEIGHT + 3, 90, 7);
        int middle = x + WIDTH / 2;
        graphics.fill(x + 8, y + 6, middle - 2, y + HEIGHT - 6, PAGE_SHADE);
        graphics.fill(x + 9, y + 7, middle - 3, y + HEIGHT - 7, PAGE);
        graphics.fill(middle + 2, y + 6, x + WIDTH - 8, y + HEIGHT - 6, PAGE_SHADE);
        graphics.fill(middle + 3, y + 7, x + WIDTH - 9, y + HEIGHT - 7, PAGE);
        graphics.fill(middle - 2, y + 4, middle + 2, y + HEIGHT - 4, COVER_LIGHT);
        for (int stitch = y + 8; stitch < y + HEIGHT - 10; stitch += 7)
            graphics.fill(middle - 1, stitch, middle + 1, stitch + 3, TITLE);
    }

    /**
     * Sterne in einem Rechteck: immer an denselben Stellen (nach {@code seed}), einige funkeln langsam. Auf
     * dem Einband werden nur die Sterne gezeichnet, die nicht unter den Seiten liegen.
     */
    private void stars(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int count, long seed) {
        java.util.Random random = new java.util.Random(seed);
        long time = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            int sx = x0 + random.nextInt(Math.max(1, x1 - x0)), sy = y0 + random.nextInt(Math.max(1, y1 - y0));
            int kind = random.nextInt(10);
            float phase = random.nextFloat() * 6.283F;
            if (seed == 7 && sx > this.left + 7 && sx < this.left + WIDTH - 7 && sy > this.top + 5 && sy < this.top + HEIGHT - 5) continue;
            int bright = (int) (170 + 85 * Math.sin(time / 600.0 + phase));
            int colour = kind == 0 ? 0xFF000000 | (bright << 16) | (bright * 3 / 4 << 8) | 0x60 // goldener Stern
                : kind == 1 ? 0xFF000000 | (bright * 3 / 4 << 16) | (bright * 3 / 4 << 8) | bright  // bläulicher Stern
                : 0xFF000000 | (bright << 16) | (bright << 8) | bright;
            graphics.fill(sx, sy, sx + 1, sy + 1, colour);
            if (kind == 0 && bright > 220) { // große Sterne funkeln als kleines Kreuz
                graphics.fill(sx - 1, sy, sx, sy + 1, colour & 0x80FFFFFF);
                graphics.fill(sx + 1, sy, sx + 2, sy + 1, colour & 0x80FFFFFF);
                graphics.fill(sx, sy - 1, sx + 1, sy, colour & 0x80FFFFFF);
                graphics.fill(sx, sy + 1, sx + 1, sy + 2, colour & 0x80FFFFFF);
            }
        }
    }

    /** Ein kleines Stück Sternenhimmel als Hintergrund (Items, Strukturen, Titelband). */
    private void sky(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int colour, long seed) {
        graphics.fill(x0, y0, x1, y1, colour);
        stars(graphics, x0, y0, x1, y1, Math.max(4, (x1 - x0) * (y1 - y0) / 120), seed);
    }

    private void home(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.left + LEFT_PAGE, y = this.top + PAGE_TOP;
        // Titel auf einem Band aus Sternenhimmel
        this.sky(graphics, this.left + 4, y - 2, this.left + WIDTH / 2 - 8, y + 22, COVER, 11);
        graphics.fill(this.left + 4, y + 22, this.left + WIDTH / 2 - 8, y + 23, ACCENT);
        this.centered(graphics, Component.translatable("guide.simonspacemod.title"), x + PAGE_WIDTH / 2, y + 2, TITLE);
        this.centered(graphics, Component.translatable("guide.simonspacemod.subtitle"), x + PAGE_WIDTH / 2, y + 12, SUBTITLE);
        this.lines(graphics, this.font.split(Component.translatable("guide.simonspacemod.intro"), PAGE_WIDTH), x, y + 30, PAGE_HEIGHT - 30);

        int rx = this.left + RIGHT_PAGE;
        this.heading(graphics, Component.translatable("guide.simonspacemod.categories"), rx, y);
        int gridLeft = rx + (PAGE_WIDTH - CATEGORY_COLUMNS * CATEGORY_CELL) / 2;
        for (int i = 0; i < GuideBook.CATEGORIES.size(); i++) {
            GuideBook.Category category = GuideBook.CATEGORIES.get(i);
            int cx = gridLeft + (i % CATEGORY_COLUMNS) * CATEGORY_CELL, cy = y + HEADER + 4 + (i / CATEGORY_COLUMNS) * CATEGORY_CELL;
            boolean hovered = mouseX >= cx && mouseX < cx + CATEGORY_CELL - 2 && mouseY >= cy && mouseY < cy + CATEGORY_CELL - 2;
            this.sky(graphics, cx, cy, cx + CATEGORY_CELL - 2, cy + CATEGORY_CELL - 2, hovered ? VIEW_HOVER : VIEW, 100 + i);
            if (hovered) graphics.fill(cx, cy + CATEGORY_CELL - 3, cx + CATEGORY_CELL - 2, cy + CATEGORY_CELL - 2, ACCENT);
            graphics.item(GuideBook.item(category.icon()), cx + (CATEGORY_CELL - 2 - 16) / 2, cy + (CATEGORY_CELL - 2 - 16) / 2);
            if (hovered)
                this.hoveredText = List.of(category.title());
            this.hotspots.add(new Hotspot(cx, cy, cx + CATEGORY_CELL - 2, cy + CATEGORY_CELL - 2, () -> this.open(View.CATEGORY, category, null)));
        }
    }

    private void category(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        GuideBook.Category category = this.category;
        if (category == null) {
            this.open(View.HOME, null, null);
            return;
        }
        int x = this.left + LEFT_PAGE, y = this.top + PAGE_TOP;
        this.sky(graphics, this.left + 4, y - 2, this.left + WIDTH / 2 - 8, y + 12, COVER, 12);
        graphics.fill(this.left + 4, y + 12, this.left + WIDTH / 2 - 8, y + 13, ACCENT);
        this.centered(graphics, category.title(), x + PAGE_WIDTH / 2, y + 1, TITLE);
        this.lines(graphics, this.font.split(category.description(), PAGE_WIDTH), x, y + 20, PAGE_HEIGHT - 20);

        int rx = this.left + RIGHT_PAGE;
        this.heading(graphics, Component.translatable("guide.simonspacemod.chapters"), rx, y);
        List<GuideBook.Entry> entries = category.entries();
        for (int row = 0; row < ENTRY_ROWS && this.spread + row < entries.size(); row++) {
            GuideBook.Entry entry = entries.get(this.spread + row);
            int ry = y + HEADER + 2 + row * ENTRY_ROW;
            boolean hovered = mouseX >= rx && mouseX < rx + PAGE_WIDTH && mouseY >= ry && mouseY < ry + ENTRY_ROW - 1;
            if (hovered)
                graphics.fill(rx - 2, ry - 1, rx + PAGE_WIDTH + 2, ry + ENTRY_ROW - 1, PAGE_SHADE);
            graphics.item(GuideBook.item(entry.icon()), rx, ry);
            FormattedCharSequence name = this.firstLine(entry.title(), PAGE_WIDTH - 20);
            graphics.text(this.font, name, rx + 20, ry + 4, hovered ? HEADING : TEXT, false);
            this.hotspots.add(new Hotspot(rx, ry, rx + PAGE_WIDTH, ry + ENTRY_ROW - 1, () -> this.open(View.ENTRY, category, entry)));
        }
        this.navigation(graphics, mouseX, mouseY, this.spread > 0, this.spread + ENTRY_ROWS < entries.size());
    }

    private void entry(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.entry == null || this.pages.isEmpty()) {
            this.open(View.HOME, null, null);
            return;
        }
        for (int side = 0; side < 2; side++) {
            int index = this.spread + side;
            if (index >= this.pages.size())
                break;
            int x = this.left + (side == 0 ? LEFT_PAGE : RIGHT_PAGE);
            this.page(graphics, this.pages.get(index), x, this.top + PAGE_TOP, mouseX, mouseY);
            Component number = Component.literal((index + 1) + " / " + this.pages.size());
            this.centered(graphics, number, x + PAGE_WIDTH / 2, this.top + HEIGHT - 17, SLOT_BORDER);
        }
        this.navigation(graphics, mouseX, mouseY, this.spread > 0, this.spread + 2 < this.pages.size());
    }

    /** The arrows at the bottom: back a page, forward a page, and up to the category or the start. */
    private void navigation(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean previous, boolean next) {
        int y = this.top + HEIGHT - 18;
        if (previous)
            this.arrow(graphics, "◀", this.left + LEFT_PAGE, y, mouseX, mouseY, () -> this.turn(-1));
        if (next)
            this.arrow(graphics, "▶", this.left + RIGHT_PAGE + PAGE_WIDTH - 10, y, mouseX, mouseY, () -> this.turn(1));
        this.arrow(graphics, "↩", this.left + WIDTH / 2 - 5, this.top + HEIGHT - 12, mouseX, mouseY, this::back);
    }

    private void arrow(GuiGraphicsExtractor graphics, String symbol, int x, int y, int mouseX, int mouseY, Runnable action) {
        boolean hovered = mouseX >= x - 2 && mouseX < x + 12 && mouseY >= y - 2 && mouseY < y + 10;
        graphics.text(this.font, Component.literal(symbol).getVisualOrderText(), x, y, hovered ? BUTTON_HOVER : HEADING, false);
        this.hotspots.add(new Hotspot(x - 2, y - 2, x + 12, y + 10, action));
    }

    /** One page: the chapter title on the first, then what it shows, then its text. */
    private void page(GuiGraphicsExtractor graphics, Laid page, int x, int y, int mouseX, int mouseY) {
        if (page.header() != null) {
            this.heading(graphics, page.header(), x, y);
            y += HEADER;
        }
        switch (page.special()) {
            case GuideBook.Spotlight spotlight -> this.spotlight(graphics, spotlight, x, y, mouseX, mouseY);
            case GuideBook.Crafting crafting -> this.crafting(graphics, crafting, x, y, mouseX, mouseY);
            case GuideBook.Structure structure -> this.structure(graphics, structure, x, y, mouseX, mouseY);
            case null, default -> {
            }
        }
        y += height(page.special());
        this.lines(graphics, page.lines(), x, y, Integer.MAX_VALUE);
    }

    private void spotlight(GuiGraphicsExtractor graphics, GuideBook.Spotlight spotlight, int x, int y, int mouseX, int mouseY) {
        ItemStack stack = GuideBook.item(spotlight.item());
        int ix = x + PAGE_WIDTH / 2 - 16;
        this.sky(graphics, ix - 4, y, ix + 36, y + 36, VIEW, 21);
        graphics.pose().pushMatrix();
        graphics.pose().translate(ix, y + 2);
        graphics.pose().scale(2, 2);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
        if (mouseX >= ix && mouseX < ix + 32 && mouseY >= y && mouseY < y + 36)
            this.hoveredItem = stack;
        this.centered(graphics, stack.getHoverName().copy().withStyle(ChatFormatting.BOLD), x + PAGE_WIDTH / 2, y + 38, HEADING);
    }

    /** A recipe: the crafting grid (or the ingredients in its middle row), the station it is made at and the result. */
    private void crafting(GuiGraphicsExtractor graphics, GuideBook.Crafting crafting, int x, int y, int mouseX, int mouseY) {
        GuideRecipes.Recipe recipe = GuideRecipes.get(crafting.recipe());
        if (recipe == null) {
            this.centered(graphics, Component.translatable("guide.simonspacemod.no_recipe"), x + PAGE_WIDTH / 2, y + 30, TEXT);
            return;
        }
        this.centered(graphics, recipe.result().getHoverName(), x + PAGE_WIDTH / 2, y, HEADING);
        int gx = x + 8, gy = y + 12;
        // Ingredients that can be one of several items take turns, one a second
        long turn = System.currentTimeMillis() / 1000;
        List<List<ItemStack>> ingredients = recipe.ingredients();
        boolean grid = recipe.kind().grid();
        for (int i = 0; i < (grid ? 9 : ingredients.size()); i++) {
            int sx = gx + (i % 3) * 18, sy = gy + (grid ? i / 3 : 1) * 18;
            this.slot(graphics, sx, sy);
            List<ItemStack> options = i < ingredients.size() ? ingredients.get(i) : List.of();
            if (!options.isEmpty())
                this.item(graphics, options.get((int) (turn % options.size())), sx, sy, mouseX, mouseY);
        }
        // The station over the arrow, then the result
        int ax = gx + 58, ay = gy + 18;
        this.item(graphics, recipe.kind().station(), ax + 2, ay - 18, mouseX, mouseY);
        graphics.text(this.font, Component.literal("→").getVisualOrderText(), ax + 6, ay + 4, HEADING, false);
        int rx = ax + 26;
        graphics.fill(rx - 3, ay - 3, rx + 19, ay + 19, SLOT_BORDER);
        graphics.fill(rx - 2, ay - 2, rx + 18, ay + 18, SLOT);
        this.item(graphics, recipe.result(), rx, ay, mouseX, mouseY);
    }

    /** A structure in 3D with its buttons (layers, show in the world) and the blocks it needs. */
    private void structure(GuiGraphicsExtractor graphics, GuideBook.Structure page, int x, int y, int mouseX, int mouseY) {
        GuideStructure structure = page.structure();
        StructureView view = VIEWS.computeIfAbsent(structure, s -> new StructureView());
        int height = structure.height();
        int layers = view.layers <= 0 || view.layers > height ? height : view.layers;
        graphics.fill(x - 1, y - 1, x + PAGE_WIDTH + 1, y + VIEW_HEIGHT + 1, SLOT_BORDER);
        this.sky(graphics, x, y, x + PAGE_WIDTH, y + VIEW_HEIGHT, VIEW, structure.id().hashCode());
        // Big enough that the whole structure fits in the box, however it is turned
        int extent = 1;
        for (GuideStructure.Part part : structure.parts())
            extent = Math.max(extent, Math.max(Math.abs(part.pos().getX()), Math.abs(part.pos().getZ())) * 2 + 1);
        float size = Math.min(PAGE_WIDTH / (extent * 1.5F), VIEW_HEIGHT / (Math.max(extent, height) * 1.3F)) * view.zoom;
        graphics.getRenderState().addPicturesInPictureState(new StructurePictureRenderer.State(structure, view.yaw, view.pitch,
            Math.min(size, 40), view.panX, view.panY, layers, x, y, x + PAGE_WIDTH, y + VIEW_HEIGHT));
        this.viewBoxes.add(new ViewBox(x, y, x + PAGE_WIDTH, y + VIEW_HEIGHT, view));
        if (mouseX >= x && mouseX < x + PAGE_WIDTH && mouseY >= y && mouseY < y + VIEW_HEIGHT)
            // One tooltip line per line of the text
            this.hoveredText = Arrays.stream(Component.translatable("guide.simonspacemod.structure.drag").getString().split("\n"))
                .<Component>map(Component::literal).toList();

        int by = y + VIEW_HEIGHT + 4;
        Component layerText = layers == height ? Component.translatable("guide.simonspacemod.structure.all_layers")
            : Component.translatable("guide.simonspacemod.structure.layers", layers, height);
        this.button(graphics, layerText, x, by, 58, mouseX, mouseY, () -> view.layers = layers >= height ? 1 : layers + 1);
        boolean showing = StructurePreview.isShowing(structure);
        this.button(graphics, Component.translatable(showing ? "guide.simonspacemod.structure.hide" : "guide.simonspacemod.structure.show"),
            x + 62, by, PAGE_WIDTH - 62, mouseX, mouseY, () -> {
                if (showing) {
                    StructurePreview.stop();
                } else {
                    StructurePreview.show(structure);
                    this.minecraft.gui.setScreen(null);
                }
            });

        // What it is built from, with how many of each
        Map<Item, Integer> blocks = new LinkedHashMap<>();
        for (GuideStructure.Part part : structure.parts()) {
            if (part.build())
                blocks.merge(part.state().get().getBlock().asItem(), 1, Integer::sum);
        }
        int mx = x;
        for (Map.Entry<Item, Integer> block : blocks.entrySet()) {
            this.item(graphics, new ItemStack(block.getKey()), mx, by + 16, mouseX, mouseY);
            String count = "×" + block.getValue();
            graphics.text(this.font, Component.literal(count).getVisualOrderText(), mx + 17, by + 21, TEXT, false);
            mx += 20 + this.font.width(count);
        }
    }

    private void button(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int mouseX, int mouseY, Runnable action) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 12;
        graphics.fill(x, y, x + width, y + 12, hovered ? BUTTON_HOVER : BUTTON);
        FormattedCharSequence label = this.firstLine(text, width - 4);
        graphics.text(this.font, label, x + (width - this.font.width(label)) / 2, y + 2, 0xFFFFFFFF, false);
        this.hotspots.add(new Hotspot(x, y, x + width, y + 12, action));
    }

    private void slot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, SLOT);
    }

    /** An item with its count, and its tooltip when the mouse is on it. */
    private void item(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        graphics.item(stack, x, y);
        if (stack.getCount() > 1) {
            String count = String.valueOf(stack.getCount());
            graphics.text(this.font, Component.literal(count).getVisualOrderText(), x + 17 - this.font.width(count), y + 9, 0xFFFFFFFF, true);
        }
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            graphics.fill(x, y, x + 16, y + 16, HIGHLIGHT);
            this.hoveredItem = stack;
        }
    }

    private void heading(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        this.centered(graphics, text.copy().withStyle(ChatFormatting.BOLD), x + PAGE_WIDTH / 2, y, HEADING);
        graphics.fill(x + 10, y + 10, x + PAGE_WIDTH - 10, y + 11, ACCENT);
    }

    private void centered(GuiGraphicsExtractor graphics, Component text, int x, int y, int colour) {
        FormattedCharSequence line = this.firstLine(text, PAGE_WIDTH);
        graphics.text(this.font, line, x - this.font.width(line) / 2, y, colour, false);
    }

    /** As much of the text as fits on one line. */
    private FormattedCharSequence firstLine(Component text, int width) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        return lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.getFirst();
    }

    private void lines(GuiGraphicsExtractor graphics, List<FormattedCharSequence> lines, int x, int y, int height) {
        for (int i = 0; i < lines.size() && (i + 1) * LINE <= height; i++)
            graphics.text(this.font, lines.get(i), x, y + i * LINE, TEXT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (Hotspot hotspot : this.hotspots) {
                if (hotspot.contains(event.x(), event.y())) {
                    hotspot.action().run();
                    return true;
                }
            }
            for (ViewBox box : this.viewBoxes) {
                if (box.contains(event.x(), event.y())) {
                    this.dragging = box.view();
                    this.panning = false;
                    return true;
                }
            }
        } else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            for (ViewBox box : this.viewBoxes) {
                if (box.contains(event.x(), event.y())) {
                    this.dragging = box.view();
                    this.panning = true;
                    return true;
                }
            }
            // Right-click elsewhere goes back, like in the Lexica
            this.back();
            return true;
        } else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            // A middle click puts the structure back as it was
            for (ViewBox box : this.viewBoxes) {
                if (box.contains(event.x(), event.y())) {
                    StructureView view = box.view();
                    view.yaw = 45;
                    view.pitch = 30;
                    view.zoom = 1;
                    view.panX = 0;
                    view.panY = 0;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging != null && this.panning) {
            this.dragging.panX += (float) dragX;
            this.dragging.panY += (float) dragY;
            return true;
        }
        if (this.dragging != null) {
            this.dragging.yaw = (this.dragging.yaw + (float) dragX * 1.5F) % 360;
            this.dragging.pitch = Math.max(0, Math.min(90, this.dragging.pitch + (float) dragY));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (ViewBox box : this.viewBoxes) {
            if (box.contains(mouseX, mouseY)) {
                box.view().zoom = (float) Math.max(0.4, Math.min(3, box.view().zoom * Math.pow(1.15, scrollY)));
                return true;
            }
        }
        if (scrollY != 0) {
            this.turn(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> this.back();
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> this.turn(-1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> this.turn(1);
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
