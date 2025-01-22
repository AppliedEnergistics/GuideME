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

package guideme.ui;

import net.minecraft.client.renderer.Rect2i;

/**
 * Represents an integer x,y coordinate in the UI.
 */
public record UiPoint(int x, int y) {
    public static final UiPoint ZERO = new UiPoint(0, 0);

    public static UiPoint fromTopLeft(Rect2i bounds) {
        return new UiPoint(bounds.getX(), bounds.getY());
    }

    public UiPoint move(int x, int y) {
        return new UiPoint(this.x + x, this.y + y);
    }

    public boolean isIn(Rect2i rect) {
        return x >= rect.getX()
                && y >= rect.getY()
                && x < rect.getX() + rect.getWidth()
                && y < rect.getY() + rect.getHeight();
    }
}
