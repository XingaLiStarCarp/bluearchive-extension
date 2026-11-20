package ba.client.screen;

import ba.client.render.entity.DealerSkinClient;
import com.github.tartaricacid.touhoulittlemaid.compat.ysm.YsmCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import minecraft.core.Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.*;

public class DealerSkinScreen extends Screen {
    public static final int ROWS_PER_PAGE = 8;
    public static final int ROWS_HEIGHT = 16;
    private static final int ROW_TOP = 44;
    private static final int CHOSEN_ROW_H = 14;

    private final List<String> models = new ArrayList<>();
    private final List<String> chosen = new ArrayList<>();
    private final List<String> available = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();

    private int chosenX;
    private int chosenW;
    private int listX;
    private int listW;
    private int previewX;
    private int previewY;
    private int previewH;
    private int previewW;
    private int page = 0;
    private int rowsPerPage = ROWS_PER_PAGE;
    private int chosenScroll = 0;
    private int listTop = ROW_TOP + 22;

    private EntityMaid previewMaid;

    private EditBox searchBox;
    private String query = "";

    private boolean scanned = false;
    private boolean previewReady;
    private boolean previewFailed;

    private String previewId;
    private String maidModelId;

    private float spin;

    public DealerSkinScreen(){
        super(Component.literal("荷官皮肤"));
    }

    @Override
    protected void init(){
        if (!scanned){
            scanned = true;
            models.clear();
            for (String modelId : DealerSkinClient.models()){
                if (!models.contains(modelId)){
                    models.add(modelId);
                }
            }

            for (String modelId : DealerSkinClient.selected()){
                if (!models.contains(modelId)){
                    models.add(modelId);
                }
            }

            chosen.clear();
            chosen.addAll(DealerSkinClient.selected());
        }
        buildWidgets();
    }

    private void refreshAvailable(){
        this.available.clear();
        String raw = this.query.trim();
        String[] tokens = raw.isEmpty() ? new String[0]
                : raw.toLowerCase(Locale.ROOT).split("\\s+");

        List<String> weak = new ArrayList<>();
        for (String modelId : this.models){
            if (this.chosen.contains(modelId)){
                continue;
            }
            if (tokens.length == 0){
                this.available.add(modelId);
                continue;
            }
            String hay = normalize(modelId);
            boolean allStrong = true;
            boolean allMatch = true;
            for (String token : tokens){
                if (token.isEmpty()){
                    continue;
                }
                if (!hay.contains(token)){
                    allStrong = false;
                    if (!isSubsequence(hay, token)){
                        allMatch = false;
                        break;
                    }
                }
            }
            if (!allMatch){
                continue;
            }
            if (allStrong){
                this.available.add(modelId);
            } else {
                weak.add(modelId);
            }
        }
        this.available.addAll(weak);
    }

    private static String normalize(String modelId){
        String lower = modelId.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot > 0){
            lower = lower.substring(0, dot);
        }
        return lower.replaceAll("[\\s_\\-./\\\\·（）()\\[\\]]", "");
    }

    private static boolean isSubsequence(String hay, String token){
        int j = 0;
        for (int i = 0; i < hay.length() && j < token.length(); i++){
            if (hay.charAt(i) == token.charAt(j)){
                j++;
            }
        }
        return j == token.length();
    }

    private int pageCount(){
        return Math.max(1, (this.available.size() + this.rowsPerPage - 1) / this.rowsPerPage);
    }

    private int maxChosenRows(){
        int h = this.previewH + 22;
        return Math.max(1, (h - 18 - 14) / CHOSEN_ROW_H);
    }

    private void rebuildRows(){
        for (Button button : this.rowButtons){
            this.removeWidget(button);
        }
        this.rowButtons.clear();

        int start = this.page * this.rowsPerPage;
        for (int row = 0; row < this.rowsPerPage; row++){
            int index = start + row;
            if (index >= this.available.size()){
                break;
            }
            String modelId = this.available.get(index);
            Button button = Button.builder(Component.literal(this.font.plainSubstrByWidth(modelId, this.listW - 10)), b -> {
                chosen.add(modelId);
                this.rebuildWidgets();
            }).bounds(this.listX, this.listTop + row * ROWS_HEIGHT, this.listW, 14).build();
            this.addRenderableWidget(button);
            this.rowButtons.add(button);
        }
    }

    private void buildWidgets(){
        refreshAvailable();

        this.rowsPerPage = Mth.clamp((this.height - this.listTop - 96) / ROWS_HEIGHT, 4, ROWS_PER_PAGE);
        this.previewH = this.rowsPerPage * ROWS_HEIGHT;

        int pages = pageCount();
        this.page = Mth.clamp(this.page, 0, pageCount() - 1);
        this.chosenScroll = Mth.clamp(this.chosenScroll,
                0, Math.max(0, this.chosen.size() - this.maxChosenRows()));
        if (page >= pages){
            page = 0;
        }
        if (page < 0){
            page = pages - 1;
        }

        this.previewW = Mth.clamp(this.width / 4, 80, 140);
        this.previewX = this.width - 12 - this.previewW;
        this.previewY = ROW_TOP;

        this.chosenX = 12;
        this.chosenW = Mth.clamp(this.width / 5, 56, 110);
        this.listX = this.chosenX + this.chosenW + 8;
        this.listW = Math.clamp(this.previewX - 12 - this.listX, 80, 240);

        this.searchBox = new EditBox(this.font, this.listX, ROW_TOP, this.listW, 16,
                Component.literal("搜索"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.literal("搜索模型…"));
        this.searchBox.setValue(this.query);
        this.searchBox.setResponder(text -> {
            this.query = text;
            this.page = 0;
            refreshAvailable();
            rebuildRows();
        });
        this.addRenderableWidget(this.searchBox);

        rebuildRows();

        int bottom = this.listTop + this.rowsPerPage * ROWS_HEIGHT + 6;
        int buttonWidth = Math.min(96, (this.width - 40 - 12) / 4);
        int gap = 4;
        int totalWidth = buttonWidth * 4 + gap * 3;
        int buttonX = (this.width - totalWidth) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("确定"), button -> {
            DealerSkinClient.save(new ArrayList<>(chosen));
            this.onClose();
        }).bounds(buttonX + (buttonWidth + gap) * 0, bottom, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("全选"), button -> {
            chosen.clear();
            chosen.addAll(models);
            this.rebuildWidgets();
        }).bounds(buttonX + (buttonWidth + gap) * 1, bottom, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("清空"), button -> {
            chosen.clear();
            this.rebuildWidgets();
        }).bounds(buttonX + (buttonWidth + gap) * 2, bottom, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("取消"), button -> {
            this.onClose();
        }).bounds(buttonX + (buttonWidth + gap) * 3, bottom, buttonWidth, 20).build());

        if (pages > 1){
            int navWidth = Math.min(96, buttonWidth * 2 + gap);
            int navX = (this.width - (navWidth * 2 + gap)) / 2;
            int navY = Math.min(bottom + 24, this.height - 26);
            this.addRenderableWidget(Button.builder(Component.literal("< 上一页"), button -> {
                this.page = Math.floorMod(this.page - 1, pageCount());
                this.rebuildWidgets();
            }).bounds(navX, navY, navWidth, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("下一页 >"), button -> {
                this.page = Math.floorMod(this.page + 1, pageCount());
                this.rebuildWidgets();
            }).bounds(navX + navWidth + gap, navY, navWidth, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick){
        guiGraphics.fill(0, 0, this.width, this.height, 0x60000000);
        this.drawChosenList(guiGraphics, mouseX, mouseY);
        panel(guiGraphics, this.listX - 2, ROW_TOP - 2, this.listW + 4, (this.listTop - ROW_TOP) + this.rowsPerPage * ROWS_HEIGHT + 8);
        this.drawPreview(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int center = this.width / 2;
        guiGraphics.drawCenteredString(this.font, this.title, center, 16, 0xFFFFFF);
        String info = models.isEmpty() ? "config/yes_steve_model/custom not exist .ysm model"
                : "可用 " + available.size() + " / 共 " + models.size() + " model，已选 " + chosen.size()
                + " （ " + (page + 1) + "/" + pageCount() + " page）";
        guiGraphics.drawCenteredString(this.font, Component.literal(info), center, 30, 0xA0A0A0);
        if (models.isEmpty()){
            guiGraphics.drawCenteredString(this.font, Component.literal("put .ysm file to config/yes_steve_model/custom and restart this screen"), center, this.height / 2, 0xFFFF55);
        }
    }

    @Override
    public boolean isPauseScreen(){
        return false;
    }

    private int rowAtAll(int mouseX, int mouseY){
        if (mouseX < this.listX || mouseX >= this.listX + this.listW){
            return -1;
        }
        int row = (mouseY - this.listTop) / ROWS_HEIGHT;
        if (row < 0 || row >= this.rowsPerPage){
            return -1;
        }
        int index = this.page * rowsPerPage + row;
        if (index < 0 || index >= this.available.size()){
            return -1;
        }
        return index;
    }

    private int rowAtChosen(int mouseX, int mouseY){
        if (mouseX < this.chosenX || mouseX >= this.chosenX + this.chosenW){
            return -1;
        }
        int row = (mouseY - (ROW_TOP + 18)) / CHOSEN_ROW_H;
        if (row < 0 || row >= this.maxChosenRows()){
            return -1;
        }
        int index = this.chosenScroll + row;
        if (index < 0 || index >= this.chosen.size()){
            return -1;
        }
        return index;
    }

    private void panel(GuiGraphics guiGraphics, int x, int y, int w, int h){
        guiGraphics.fill(x, y, x + w, y + h, 0xFF1E1E28);
        guiGraphics.fill(x, y, x + w, y + 1, 0xFF5A5A6E);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, 0xFF5A5A6E);
        guiGraphics.fill(x, y, x + 1, y + h, 0xFF5A5A6E);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0xFF5A5A6E);
    }

    private void drawChosenList(GuiGraphics guiGraphics, int mouseX, int mouseY){
        int h = this.previewH + 22;
        panel(guiGraphics, this.chosenX, ROW_TOP, this.chosenW, h);
        guiGraphics.drawString(this.font, "已选 " + this.chosen.size(),
                this.chosenX + 5, ROW_TOP + 5, 0xFFFFD97A, false);

        guiGraphics.enableScissor(this.chosenX + 1, ROW_TOP + 18,
                this.chosenX + this.chosenW - 1, ROW_TOP + h - 1);
        int maxRows = this.maxChosenRows();
        for (int i = 0; i < maxRows; i++){
            int index = this.chosenScroll + i;
            if (index < 0 || index >= this.chosen.size()){
                break;
            }
            int y = ROW_TOP + 18 + i * CHOSEN_ROW_H;
            boolean hover = mouseX >= this.chosenX && mouseX < this.chosenX + this.chosenW
                    && mouseY >= y && mouseY < y + CHOSEN_ROW_H;
            if (hover){
                guiGraphics.fill(this.chosenX + 1, y, this.chosenX + this.chosenW - 1, y + CHOSEN_ROW_H, 0xFF33334A);
            }
            guiGraphics.drawString(this.font,
                    this.font.plainSubstrByWidth(this.chosen.get(index), this.chosenW - 8),
                    this.chosenX + 4, y + 3, hover ? 0xFFFFFFFF : 0xFFE8E8E8, false);
        }
        guiGraphics.disableScissor();

        int rest = this.chosen.size() - maxRows - this.chosenScroll;
        if (rest > 0){
            guiGraphics.drawString(this.font, "还有 " + rest + " 个",
                    this.chosenX + 5, ROW_TOP + h - 12, 0xFFA0A0B8, false);
        }
    }

    private void drawPreview(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick){
        panel(guiGraphics, this.previewX, this.previewY, this.previewW, this.previewH);
        guiGraphics.fill(this.previewX + 1, this.previewY + 15, this.previewX + this.previewW - 1, this.previewY + this.previewH - 1, 0xFF0E0E12);
        guiGraphics.renderOutline(this.previewX, this.previewY, this.previewW, this.previewH, 0xFF555555);

        int all = rowAtAll(mouseX, mouseY);
        int left = rowAtChosen(mouseX, mouseY);
        if (all >= 0 && all < this.available.size()){
            this.previewId = this.available.get(all);
        } else if (left >= 0 && left < this.chosen.size()){
            this.previewId = this.chosen.get(left);
        } else if (this.previewId == null || !this.models.contains(this.previewId)){
            this.previewId = this.chosen.isEmpty()
                    ? (this.available.isEmpty() ? null : this.available.getFirst())
                    : this.chosen.getFirst();
        }

        if (this.previewId == null){
            guiGraphics.drawCenteredString(this.font, Component.literal("暂无模型"), this.previewX + this.previewW / 2, this.previewY + this.previewH / 2, 0xFF9A9A9A);
            return;
        }

        String label = this.font.plainSubstrByWidth(this.previewId, this.previewW - 6);
        guiGraphics.drawString(this.font, label, this.previewX + 3, this.previewY + 3, 0xFFE0E0E0, false);

        if (!this.previewReady){
            this.previewReady = setupPreview();
        }
        if (!this.previewReady || this.previewFailed || this.previewMaid == null){
            guiGraphics.drawCenteredString(this.font, Component.literal("无法预览"), this.previewX + this.previewW / 2, this.previewY + this.previewH / 2, 0xFF9A9A9A);
            return;
        }

        if (!this.previewId.equals(this.maidModelId)){
            applyPreviewModel(this.previewId);
            if (this.previewFailed){
                return;
            }
        }

        this.spin = (this.spin + partialTick * 0.8F) % 360.0F;
        this.previewMaid.tickCount = (int) ((System.currentTimeMillis() / 50L) % 20000L);
        this.previewMaid.yBodyRot = 0.0F;
        this.previewMaid.yBodyRotO = 0.0F;
        this.previewMaid.yHeadRot = 0.0F;
        this.previewMaid.yHeadRotO = 0.0F;
        this.previewMaid.setYRot(0.0F);

        int pad = 6;
        int headRoom = 16;
        double bb = Math.max(0.1, this.previewMaid.getBbHeight());
        float scale = (float) Math.min(
                (this.previewW - 2 * pad) * 0.95F / bb,
                (this.previewH - headRoom - 2 * pad) * 0.75F / bb);

        guiGraphics.enableScissor(this.previewX + 1, this.previewY + headRoom,
                this.previewX + this.previewW - 1, this.previewY + this.previewH - 1);
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(this.previewX + this.previewW / 2.0F,
                this.previewY + this.previewH - pad, 240.0F);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F + this.spin));
        guiGraphics.flush();
        Minecraft.getInstance().getEntityRenderDispatcher().render(this.previewMaid,
                0.0, 0.0, 0.0, 0.0F, partialTick, pose,
                guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT);
        pose.popPose();
        guiGraphics.flush();
        guiGraphics.disableScissor();
    }

    private boolean setupPreview(){
        if (!YsmCompat.isInstalled()){
            return false;
        }
        if (Minecraft.getInstance().level == null){
            return false;
        }
        try {
            this.previewMaid = new EntityMaid(Minecraft.getInstance().level);
            this.previewMaid.setIsYsmModel(true);
            this.previewMaid.setNoAi(true);
            return true;
        }catch (Throwable throwable){
            this.previewFailed = true;
            Core.logWarn("创建荷官皮肤预览实体失败", throwable);
            return false;
        }
    }

    private void applyPreviewModel(String modelId){
        try {
            this.previewMaid.setIsYsmModel(true);
            this.previewMaid.setYsmModel(modelId, "default", Component.literal(modelId));
            this.maidModelId = modelId;
        }catch (Throwable throwable){
            this.previewFailed = true;
            Core.logWarn("设置荷官皮肤预览模型失败: " + modelId, throwable);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button){
        if (button == 0){
            int index = rowAtChosen((int) mouseX, (int) mouseY);
            if (index >= 0 && index < this.chosen.size()){
                this.chosen.remove(index);
                this.rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY){
        if (mouseX >= this.chosenX && mouseX < this.chosenX + this.chosenW
                && mouseY >= ROW_TOP && mouseY < ROW_TOP + this.maxChosenRows() * CHOSEN_ROW_H + 18){
            int maxScroll = Math.max(0, this.chosen.size() - this.maxChosenRows());
            this.chosenScroll = Mth.clamp(this.chosenScroll - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick){
    }
}
