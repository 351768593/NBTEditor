package nbt.map;

import java.io.File;
import java.io.FileFilter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nbt.map.pos.ChunkPosition;
import nbt.map.pos.InChunkPosition;
import nbt.map.pos.OwnChunkPosition;
import nbt.read.MapReader;
import net.minecraft.world.level.chunk.storage.RegionFile;

/**
 * A chunk manager that does not rely on threads but ensures that a chunk will
 * be loaded on request. This way is easier to use for non interactive edits.
 * 
 * @author Joschi <josua.krause@googlemail.com>
 */
public class SerialChunkManager {

  private final Map<OwnChunkPosition, Chunk> chunks;

  private final Map<OwnChunkPosition, File> reload;

  private final Map<OwnChunkPosition, ChunkPosition> otherPos;

  /**
   * Creates a serial chunk manager.
   */
  public SerialChunkManager() {
    chunks = new HashMap<OwnChunkPosition, Chunk>();
    reload = new HashMap<OwnChunkPosition, File>();
    otherPos = new HashMap<OwnChunkPosition, ChunkPosition>();
  }

  /**
   * Setter.
   * 
   * @param folder The folder whose chunks are loaded.
   * @param clearCache Whether to clear the map reader cache.
   */
  public void setFolder(final File folder, final boolean clearCache) {
    chunks.clear();
    if(clearCache) {
      MapReader.clearCache();
    }
    final File[] files = folder.listFiles(new FileFilter() {

      @Override
      public boolean accept(final File f) {
        return f.isFile()
            && f.getName().endsWith(RegionFile.ANVIL_EXTENSION);
      }

    });
    for(final File f : files) {
      final MapReader r = MapReader.getForFile(f);
      final List<ChunkPosition> chunkList = r.getChunks();
      for(final ChunkPosition p : chunkList) {
        final Chunk chunk = new Chunk(r.read(p), f, p);
        unloadChunk(chunk);
      }
    }
  }

  /**
   * Removes the chunk from the memory and saves changes.
   * 
   * @param chunk The chunk to unload.
   */
  public void unloadChunk(final Chunk chunk) {
    final OwnChunkPosition pos = chunk.getPos();
    chunks.remove(pos);
    reload.put(pos, chunk.getFile());
    otherPos.put(pos, chunk.getOtherPos());
    // writes the chunk if changed
    chunk.unload();
  }

  /**
   * Gets the chunk at the given position. The chunk should be unloaded with
   * {@link #unloadChunk(Chunk)} after usage.
   * 
   * @param x The x position.
   * @param z The z position.
   * @return The chunk.
   */
  public Chunk getChunk(final int x, final int z) {
    final int cx = x / 16 - (x < 0 ? 1 : 0);
    final int cz = z / 16 - (z < 0 ? 1 : 0);
    return getChunk0(cx, cz);
  }

  /**
   * Gets the chunk at the given position.
   * 
   * @param x The x position.
   * @param z The z position.
   * @return The chunk.
   */
  private Chunk getChunk0(final int x, final int z) {
    return getChunk(new OwnChunkPosition(x * 16, z * 16));
  }

  /**
   * Getter.
   * 
   * @param pos The position.
   * @return The chunk at the given position.
   */
  private Chunk getChunk(final OwnChunkPosition pos) {
    if(!chunks.containsKey(pos)) {
      reloadChunk(pos);
    }
    return chunks.get(pos);
  }

  /**
   * Reloads a chunk.
   * 
   * @param pos The position of the chunk.
   */
  private void reloadChunk(final OwnChunkPosition pos) {
    final File f = reload.get(pos);
    if(f == null) return;
    final ChunkPosition op = otherPos.get(pos);
    final MapReader r = MapReader.getForFile(f);
    final Chunk chunk = new Chunk(r.read(op), f, op);
    chunks.put(pos, chunk);
    reload.remove(pos);
    otherPos.remove(pos);
  }

  /**
   * Calculates the position within the chunk.
   * 
   * @param x The world x position.
   * @param z The world z position.
   * @return The position in the chunk.
   */
  public InChunkPosition getPosInChunk(final int x, final int z) {
    final int cx = x % 16 + (x < 0 ? 15 : 0);
    final int cz = z % 16 + (z < 0 ? 15 : 0);
    return new InChunkPosition(cx, cz);
  }

}
