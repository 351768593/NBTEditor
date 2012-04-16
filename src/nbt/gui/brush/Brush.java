package nbt.gui.brush;

import nbt.gui.ClickReceiver;
import nbt.gui.MapViewer;
import nbt.map.Chunk;
import nbt.map.ChunkEdit;
import nbt.map.Pair;

/**
 * Brushes paint on the map.
 * 
 * @author Joschi <josua.krause@googlemail.com>
 */
public abstract class Brush implements ClickReceiver {

  private final MapViewer viewer;

  private final ChunkEdit edit;

  private final boolean circle;

  private int radius;

  private int r2;

  /**
   * Creates a brush for a given map viewer.
   * 
   * @param viewer The viewer.
   * @param radius The initial radius.
   * @param circle Whether the brush has a circular form.
   */
  public Brush(final MapViewer viewer, final int radius, final boolean circle) {
    this.viewer = viewer;
    this.circle = circle;
    setRadius(radius);
    edit = new ChunkEdit() {

      @Override
      public void edit(final Chunk c, final Pair posInChunk) {
        Brush.this.edit(c, posInChunk);
      }

    };
  }

  @Override
  public void setRadius(final int newRadius) {
    radius = newRadius;
    r2 = radius * radius;
  }

  @Override
  public int radius() {
    return radius;
  }

  @Override
  public void clicked(final int x, final int z) {
    for(int i = -radius * 2; i <= radius * 2; ++i) {
      for(int j = -radius * 2; j <= radius * 2; ++j) {
        if(i * i + j * j > 4 * r2) {
          continue;
        }
        final int posX = x + i;
        final int posZ = z + j;
        viewer.editChunk(posX, posZ, edit);
      }
    }
    viewer.editFinished();
  }

  @Override
  public boolean isCircle() {
    return circle;
  }

  /**
   * Edits a single column in a chunk.
   * 
   * @param c The chunk.
   * @param posInChunk The position within the chunk.
   */
  protected abstract void edit(Chunk c, Pair posInChunk);

}
