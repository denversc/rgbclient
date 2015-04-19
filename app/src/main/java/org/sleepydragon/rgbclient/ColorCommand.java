/*
 * Copyright 2015 Denver Coneybeare <denver@sleepydragon.org>
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sleepydragon.rgbclient;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.util.UUID;

/**
 * Stores information about a command received from the server.
 */
public class ColorCommand implements Parcelable {

    public enum Instruction {
        RELATIVE,
        ABSOLUTE,
    }

    @NonNull
    public final UUID id;

    @NonNull
    public final Instruction instruction;
    public final int r;
    public final int g;
    public final int b;

    public ColorCommand(@NonNull Instruction instruction, int r, int g, int b) {
        this(UUID.randomUUID(), instruction, r, g, b);
    }

    public ColorCommand(@NonNull UUID id, @NonNull Instruction instruction,
            int r, int g, int b) {
        this.id = id;
        this.instruction = instruction;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeParcelable(new ParcelUuid(id), 0);
        dest.writeInt(instruction.ordinal());
        dest.writeInt(r);
        dest.writeInt(g);
        dest.writeInt(b);
    }

    @Override
    public String toString() {
        return instruction + " (" + r + ", " + g + ", " + b + ")";
    }

    @Override
    public int hashCode() {
        int hashCode = id.hashCode();
        hashCode += r * 97813;
        hashCode += g * 97787;
        hashCode += b * 97673;
        hashCode += instruction.ordinal();
        return hashCode;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof ColorCommand)) {
            return false;
        }
        final ColorCommand other = (ColorCommand) o;
        return id.equals(other.id) &&
                r == other.r && g == other.g && b == other.b &&
                instruction == other.instruction;
    }

    public static final Parcelable.Creator<ColorCommand> CREATOR =
            new Parcelable.Creator<ColorCommand>() {

                @Override
                public ColorCommand createFromParcel(final Parcel src) {
                    final ParcelUuid parcelUuid = src.readParcelable(null);
                    final int instructionOrdinal = src.readInt();
                    final int r = src.readInt();
                    final int g = src.readInt();
                    final int b = src.readInt();

                    final UUID id = parcelUuid.getUuid();
                    final Instruction instruction = Instruction.values()[instructionOrdinal];
                    return new ColorCommand(id, instruction, r, g, b);
                }

                @Override
                public ColorCommand[] newArray(final int size) {
                    return new ColorCommand[size];
                }

            };

}
