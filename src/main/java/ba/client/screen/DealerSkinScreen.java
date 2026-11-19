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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DealerSkinScreen extends Screen {
    public static final int ROWS_PER_PAGE = 8;
    public static final int ROWS_HEIGHT = 20;
    private static final int ROW_TOP = 44;
    private static final int CHOSEN_ROW_H = 14;

    private final List<String> models = new ArrayList<>();
    private final List<String> chosen = new ArrayList<>();
    private final List<String> available = new ArrayList<>();

    private int chosenX;
    private int chosenW;
    private int listX;
    private int listW;
    private int previewX;
    private int previewY;
    private int previewH;
    private int previewW;
    private int page = 0;

    private EntityMaid previewMaid;

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
        for (String modelId : this.models){
            if (!this.chosen.contains(modelId)){
                this.available.add(modelId);
            }
        }
    }

    private int pageCount(){
        return Math.max(1, (this.available.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    private void buildWidgets(){
        refreshAvailable();
        int pages = pageCount();
        if (page >= pages){
            page = 0;
        }
        if (page < 0){
            page = pages - 1;
        }

        this.previewW = Mth.clamp(this.width / 4, 72, 130);
        this.previewX = this.width - 12 - this.previewW;
        this.previewY = ROW_TOP;
        this.previewH = Math.max(80, Math.min(ROWS_PER_PAGE * ROWS_HEIGHT, this.height - ROW_TOP - 70));

        this.chosenX = 12;
        this.chosenW = Mth.clamp(this.width / 5, 56, 110);
        this.listX = this.chosenX + this.chosenW + 8;
        this.listW = Math.max(80, this.previewX - 12 - this.listX);

        int rowWidth = this.listW;
        int rowX = this.listX;
        int top = ROW_TOP;

        int start = page * ROWS_PER_PAGE;
        for (int row = 0; row < ROWS_PER_PAGE; row++){
            int index = start + row;
            if (index >= available.size()){
                break;
            }
            String modelId = available.get(index);
            this.addRenderableWidget(Button.builder(Component.literal(shorten(modelId)), button -> {
                chosen.add(modelId);
                this.rebuildWidgets();
            }).bounds(rowX, top + row * ROWS_HEIGHT, rowWidth, 18).build());
        }

        int bottom = top + ROWS_PER_PAGE * ROWS_HEIGHT + 6;
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
            int navY = bottom + 24;
            this.addRenderableWidget(Button.builder(Component.literal("< 上一页"), button -> {
                this.page --;
                this.rebuildWidgets();
            }).bounds(navX, navY, navWidth, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("下一页 >"), button -> {
                this.page ++;
                this.rebuildWidgets();
            }).bounds(navX + navWidth + gap, navY, navWidth, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick){
        drawChosenList(guiGraphics, mouseX, mouseY);
        drawPreview(guiGraphics, mouseX, mouseY, partialTick);
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

    private static String shorten(String text){
        int max = 36;
        return text.length() <= max ? text : text.substring(0, max - 1) + "-";
    }

    private int rowAtAll(int mouseX, int mouseY){
        if (mouseX < this.listX || mouseX >= this.listX + this.listW){
            return -1;
        }
        int row = (mouseY - ROW_TOP) / ROWS_HEIGHT;
        if (row < 0 || row >= ROWS_PER_PAGE){
            return -1;
        }
        int index = this.page * ROWS_PER_PAGE + row;
        return index >= 0 && index < this.available.size() ? index : -1;
    }

    private int rowAtChosen(int mouseX, int mouseY){
        if (mouseX < this.chosenX || mouseX >= this.chosenX + this.chosenW){
            return -1;
        }
        int row = (mouseY - (ROW_TOP + 19)) / CHOSEN_ROW_H;
        if (row < 0 || mouseY >= ROW_TOP + this.previewH){
            return -1;
        }
        int maxRows = Math.max(1, (this.previewH - 24) / CHOSEN_ROW_H);
        return row >= 0 && row < Math.min(this.chosen.size(), maxRows) ? row : -1;
    }

    private void drawChosenList(GuiGraphics guiGraphics, int mouseX, int mouseY){
        guiGraphics.fill(this.chosenX, ROW_TOP, this.chosenX + this.chosenW, ROW_TOP + this.previewH, 0xFF1E1E1E);
        guiGraphics.renderOutline(this.chosenX, ROW_TOP, this.chosenW, this.previewH, 0xFF555555);
        guiGraphics.fill(this.chosenX + 1, ROW_TOP + 1, this.chosenX + this.chosenW - 1, ROW_TOP + 18, 0xFF2C2C2C);
        guiGraphics.drawString(this.font, "已选" + this.chosen.size(), this.chosenX + 3, ROW_TOP + 5, 0xFF9A9A9A, false);

        int hover = rowAtChosen(mouseX, mouseY);
        int maxRows = Math.max(1, (this.previewH - 24) / CHOSEN_ROW_H);
        int max = Math.min(this.chosen.size(), maxRows);
        for (int i = 0; i < max; i ++){
            int y = ROW_TOP + 19 + i * CHOSEN_ROW_H;
            if (i == hover){
                guiGraphics.fill(this.chosenX + 1, y - 1, this.chosenX + this.chosenW - 1, y + 13, 0x40FFFFFF);
            }
            String label = this.font.plainSubstrByWidth(this.chosen.get(i), this.chosenW - 8);
            guiGraphics.drawString(this.font, label, this.chosenX + 4, y + 1, 0xFFE0E0E0, false);
        }
        if (this.chosen.size() > maxRows){
            guiGraphics.drawString(this.font, "还有" + (this.chosen.size() - maxRows) + "个", this.chosenX + 3, ROW_TOP + this.previewH - 12, 0xFF9A9A9A, false);
        }
    }

    private void drawPreview(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick){
        guiGraphics.fill(this.previewX, this.previewY, this.previewX + this.previewW, this.previewH, 0xFF101010);
        guiGraphics.renderOutline(this.previewX, this.previewY, this.previewW, this.previewH, 0xFF555555);

        int all = rowAtAll(mouseX, mouseY);
        int left = rowAtChosen(mouseX, mouseY);
        if (all >= 0){
            this.previewId = this.available.get(all);
        }else if (left >= 0){
            this.previewId = this.chosen.get(left);
        }else if (this.previewId == null){
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

        float scale = Math.min(this.previewW * 0.9F, this.previewH * 0.62F) / 1.9F;
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(this.previewX + this.previewW / 2.0F, this.previewY + this.previewH * 0.88F, 240.0F);
        pose.scale(-scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F + this.spin));
        guiGraphics.flush();
        Minecraft.getInstance().getEntityRenderDispatcher().render(this.previewMaid,
                0.0, 0.0, 0.0, 0.0F, partialTick, pose,
                guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT);
        pose.popPose();
        guiGraphics.flush();
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
            int row = rowAtChosen((int) mouseX, (int) mouseY);
            if (row >= 0){
                this.chosen.remove(row);
                this.rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
