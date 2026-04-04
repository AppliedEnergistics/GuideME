/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package guideme.internal.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Creates a {@link Blitter} to draw fluids into the user interface.
 */
public final class FluidBlitter {

    private FluidBlitter() {
    }

    public static Blitter create(FluidStack stack) {
        if (stack.isEmpty() && stack.getFluid() != Fluids.EMPTY) {
            stack = new FluidStack(stack.typeHolder(), 1, stack.getComponentsPatch());
        }

        var modelSet = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        Fluid fluid = stack.getFluid();
        // TODO: stack-aware fluid models, should they be added back
        var model = modelSet.get(fluid.defaultFluidState());
        int tintColor = model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(stack) : -1;

        return Blitter.sprite(model.stillMaterial().sprite())
                .colorRgb(tintColor)
                // Most fluid texture have transparency, but we want an opaque slot
                .blending(false);
    }

}
