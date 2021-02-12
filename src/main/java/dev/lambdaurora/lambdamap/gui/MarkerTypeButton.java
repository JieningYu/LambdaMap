/*
 * Copyright (c) 2021 LambdAurora <aurora42lambda@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.lambdaurora.lambdamap.gui;

import dev.lambdaurora.lambdamap.LambdaMap;
import dev.lambdaurora.lambdamap.map.marker.MarkerType;
import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.widget.SpruceButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

public class MarkerTypeButton extends SpruceButtonWidget {
    private static final Identifier FOCUSED_TEXTURE = LambdaMap.id("textures/gui/icon_selection.png");
    private MarkerType type;

    public MarkerTypeButton(Position position, MarkerType type, Consumer<MarkerType> changeListener) {
        super(position, 20, 20, LiteralText.EMPTY, btn -> {
            MarkerType next = MarkerType.next(((MarkerTypeButton) btn).type);
            ((MarkerTypeButton) btn).type = next;
            changeListener.accept(next);
        });
        this.type = type;
    }

    public MarkerType getType() {
        return this.type;
    }

    public void setType(MarkerType type) {
        this.type = type;
    }

    @Override
    protected void renderWidget(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        matrices.push();
        matrices.translate(this.getX() + 9, this.getY() + 11, 5);
        matrices.scale(2, 2, 1);
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(Tessellator.getInstance().getBuffer());
        this.type.render(matrices, immediate, 180.f, null, LightmapTextureManager.pack(15, 15));
        immediate.draw();
        matrices.pop();
    }

    @Override
    protected void renderBackground(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        if (this.isFocused()) {
            int width = this.getWidth();
            int height = this.getHeight();
            MinecraftClient.getInstance().getTextureManager().bindTexture(FOCUSED_TEXTURE);
            drawTexture(matrices, this.getX(), this.getY(), 0, 0, width, height, width, height);
        }
    }
}
