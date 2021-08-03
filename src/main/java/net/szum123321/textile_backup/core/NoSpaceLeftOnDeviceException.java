/*
 * A simple backup mod for Fabric
 * Copyright (C) 2021  Szum123321
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.szum123321.textile_backup.core;

import java.io.IOException;

/**
 * Wrapper for specific IOException. Temporary way to get more info about issue #51
 */
public class NoSpaceLeftOnDeviceException extends IOException {
    public NoSpaceLeftOnDeviceException(Throwable cause) {
        super(cause);
    }
}