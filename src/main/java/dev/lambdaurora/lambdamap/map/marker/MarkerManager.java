/*
 * Copyright (c) 2021-2022 LambdAurora <email@lambdaurora.dev>
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

package dev.lambdaurora.lambdamap.map.marker;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import com.electronwill.nightconfig.core.file.FileConfig;
import dev.lambdaurora.lambdamap.map.WorldMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapIcon;
import net.minecraft.item.map.MapState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages the markers of a world map.
 *
 * @author LambdAurora
 * @version 1.0.0
 * @since 1.0.0
 */
public class MarkerManager implements Iterable<Marker> {
	private static final Logger LOGGER = LogManager.getLogger();

	private final List<Marker> markers = new ArrayList<>();
	private final WorldMap map;
	private final File file;
	private final FileConfig config;

	private ItemStack lastFilledMapStack;

	public MarkerManager(WorldMap map) {
		this.map = map;
		this.file = new File(this.map.getDirectory(), "markers.toml");
		this.config = FileConfig.builder(this.file).autosave().build();
	}

	public Marker addMarker(MarkerType type, MarkerSource source, int x, int y, int z, float rotation, @Nullable Text text) {
		Marker marker = new Marker(type, source, x, y, z, rotation, text);
		this.addMarker(marker);

		this.save();
		return marker;
	}

	public void addMarker(Marker marker) {
		for (Marker o : this) {
			if (o.isAt(marker)) {
				o.merge(marker);
				return;
			}
		}

		this.markers.add(marker);
	}

	public void removeMarkersAt(BlockPos pos) {
		this.markers.removeIf(other -> pos.getX() == other.getX() && pos.getY() == other.getY() && pos.getZ() == other.getZ());
	}

	public void removeMarker(Marker marker) {
		this.markers.remove(marker);
	}

	@Override
	public Iterator<Marker> iterator() {
		return this.markers.iterator();
	}

	public void forEachInBox(int minX, int minZ, int sizeX, int sizeZ, Consumer<Marker> consumer) {
		int maxX = minX + sizeX;
		int maxZ = minZ + sizeZ;
		for (Marker marker : this.markers) {
			if (marker.isIn(minX, minZ, maxX, maxZ))
				consumer.accept(marker);
		}
	}

	public void tick(World world) {
		// Check for existence of the banner markers in the world if possible.
		Iterator<Marker> it = this.markers.iterator();
		while (it.hasNext()) {
			Marker marker = it.next();
			if (marker.getSource() != MarkerSource.BANNER)
				continue;
			Chunk chunk = world.getChunk(marker.getChunkX(), marker.getChunkZ(), ChunkStatus.FULL, false);
			if (chunk == null)
				continue;
			Marker bannerMarker = Marker.fromBanner(world, marker.getPos());
			if (bannerMarker == null)
				it.remove();
		}

		MinecraftClient client = MinecraftClient.getInstance();

		// Filled map stuff
		// 1. Try to import the markers of the filled map.
		// 2. Try to import the colors of the filled map if it has absolute coordinates markers.
		ItemStack stack = client.player.getMainHandStack();
		if (!stack.isEmpty() && stack.isOf(Items.FILLED_MAP) && stack.hasNbt() && stack != this.lastFilledMapStack) {
			NbtCompound nbt = stack.getNbt();
			var mapMarkers = new ArrayList<Marker>();
			nbt.getList("Decorations", NbtType.COMPOUND).stream().map(decoration -> ((NbtCompound) decoration)).forEach(decoration -> {
				var type = MapIcon.Type.byId(decoration.getByte("type"));
				if (type.isAlwaysRendered()) {
					mapMarkers.add(this.addMarker(MarkerType.getVanillaMarkerType(type), MarkerSource.FILLED_MAP,
							(int) decoration.getDouble("x"), 64, (int) decoration.getDouble("z"),
							decoration.getFloat("rot"), null));
				}
			});

			if (!mapMarkers.isEmpty()) {
				Integer mapId = FilledMapItem.getMapId(stack);
				if (mapId != null) {
					MapState mapState = FilledMapItem.getMapState(mapId, world);
					if (mapState != null) {
						this.map.importMapState(mapState, mapMarkers);
					}
				}
			}
			this.lastFilledMapStack = stack;
		}
	}

	public void load() {
		if (!this.file.exists()) {
			var nbtFile = new File(this.file.getParentFile(), this.file.getName().replace(".toml", ".nbt"));
			if (nbtFile.exists()) {
				try {
					this.readNbt(NbtIo.readCompressed(nbtFile));
					return;
				} catch (IOException e) {
					LOGGER.error("Failed to read markers from " + nbtFile + ".", e);
				}
			}
		}

		this.config.load();

		this.markers.clear();
		List<Config> markers = this.config.getOrElse("markers", ArrayList::new);
		markers.forEach(config -> this.markers.add(Marker.fromConfig(config)));
	}

	public void save() {
        /*try {
            NbtIo.writeCompressed(this.toNbt(), this.file);
        } catch (IOException e) {
            LOGGER.error("Failed to save markers to " + this.file + ".", e);
        }*/

		List<Config> markers = new ArrayList<>();
		this.markers.forEach(marker -> markers.add(
				marker.writeTo(InMemoryCommentedFormat.defaultInstance().createConfig(Object2ObjectOpenHashMap::new))
		));

		this.config.set("markers", markers);
	}

	public void readNbt(NbtCompound nbt) {
		this.markers.clear();

		NbtList list = nbt.getList("markers", NbtType.COMPOUND);
		list.forEach(child -> this.markers.add(Marker.fromNbt((NbtCompound) child)));
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.put("markers", this.markers.stream().map(Marker::toNbt).collect(Collectors.toCollection(NbtList::new)));
		return nbt;
	}
}
