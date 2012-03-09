package nbt.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;

import nbt.map.Chunk;
import nbt.read.MapReader;
import nbt.read.MapReader.Pair;
import net.minecraft.world.level.chunk.storage.RegionFile;

public class MapViewer extends JComponent {

    private static final long serialVersionUID = 553314683721005657L;

    private final Map<Pair, Chunk> chunks;

    private final Map<Pair, File> reload;

    private final Map<Pair, Pair> otherPos;

    private final Set<Chunk> mayUnload;

    private int offX;

    private int offZ;

    private final double scale;

    private final MapFrame frame;

    public MapViewer(final MapFrame frame, final double scale) {
        this.frame = frame;
        chunks = new HashMap<Pair, Chunk>();
        reload = new HashMap<Pair, File>();
        otherPos = new HashMap<Pair, Pair>();
        mayUnload = new HashSet<Chunk>();
        this.scale = scale;
        final MouseAdapter mouse = new MouseAdapter() {

            private boolean drag;

            private int tmpX;

            private int tmpY;

            private int tmpOffX;

            private int tmpOffZ;

            @Override
            public void mousePressed(final MouseEvent e) {
                tmpX = e.getX();
                tmpY = e.getY();
                tmpOffX = offX;
                tmpOffZ = offZ;
                drag = true;
            }

            @Override
            public void mouseDragged(final MouseEvent e) {
                if (drag) {
                    final int x = e.getX();
                    final int z = e.getY();
                    setOffset(x, z);
                    setToolTipText(null);
                }
            }

            @Override
            public void mouseMoved(final MouseEvent e) {
                final int x = e.getX();
                final int z = e.getY();
                selectAtScreen(x, z);
            }

            private void setOffset(final int x, final int y) {
                offX = tmpOffX + (tmpX - x);
                offZ = tmpOffZ + (tmpY - y);
                repaint();
            }

            @Override
            public void mouseReleased(final MouseEvent e) {
                if (drag) {
                    final int x = e.getX();
                    final int z = e.getY();
                    setOffset(x, z);
                    drag = false;
                }
            }

            @Override
            public void mouseExited(final MouseEvent e) {
                setToolTipText(null);
            }

        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setBackground(Color.BLACK);
        final BufferedImage loading = new BufferedImage((int)(scale*16), (int)(scale*16), BufferedImage.TYPE_INT_RGB);
        final Graphics2D g =(Graphics2D) loading.getGraphics();
        g.setColor(new Color(0x404040));
        final Rectangle r = new Rectangle(loading.getWidth(), loading.getHeight());
        g.fill(r);
        g.dispose();
        this.loading = loading;
    }

    public Chunk getChunkAtScreen(final int x, final int z) {
        final int cx = (int) ((offX + x) / scale) / 16 - (offX + x < 0 ? 1 : 0);
        final int cz = (int) ((offZ + z) / scale) / 16 - (offZ + z < 0 ? 1 : 0);
        Chunk c;
        synchronized (chunks) {            
            c=chunks.get(new Pair(cx * 16, cz * 16));
        }
        return c;
    }

    public Pair getPosInChunkAtScreen(final int x, final int z) {
        final int cx = (int) ((offX + x) / scale) % 16
                + (offX + x < 0 ? 15 : 0);
        final int cz = (int) ((offZ + z) / scale) % 16
                + (offZ + z < 0 ? 15 : 0);
        return new Pair(cx, cz);
    }

    private Chunk selChunk;

    private Pair selPos;

    public void selectAtScreen(final int x, final int z) {
        selChunk = getChunkAtScreen(x, z);
        selPos = getPosInChunkAtScreen(x, z);
        if (selChunk != null) {
            final String tt = "x:" + (selPos.x + selChunk.getX()) + " z:"
                    + (selPos.z + selChunk.getZ()) + " b: "
                    + selChunk.getBiome(selPos.x, selPos.z).name;
            frame.setTitleText(tt);
            setToolTipText(tt);
        } else {
            frame.setTitleText(null);
            setToolTipText(null);
        }
        repaint();
    }

    public void setFolder(final File folder) {
        synchronized (chunks) {            
            chunks.clear();
        }
        final File[] files = folder.listFiles(new FileFilter() {

            @Override
            public boolean accept(final File f) {
                return f.isFile()
                        && f.getName().endsWith(RegionFile.ANVIL_EXTENSION);
            }

        });
        final double size = files.length;
        double i = 0;
        for (final File f : files) {
            final MapReader r = new MapReader(f);
            final List<Pair> chunkList = r.getChunks();
            final double sizeInner = chunkList.size();
            double j = 0;
            for (final Pair p : chunkList) {
                try {
                    checkHeapStatus();
                    final Chunk chunk = new Chunk(r.read(p.x, p.z), f, p);
                    synchronized (chunks) {
                    chunks.put(chunk.getPos(), chunk);
                    }
                    synchronized (mayUnload) {                        
                        mayUnload.add(chunk);
                    }
                } catch (final OutOfMemoryError e) {
                    boolean canUnload;
                    synchronized (mayUnload) {
                        canUnload = !mayUnload.isEmpty();
                    }
                    if (canUnload) {
                        handleFullMemory();
                    } else {
                        throw new Error(e);
                    }
                }
                ++j;
                final double perc = (i + j / sizeInner) / size * 100;
                System.out.println("loading " + perc + "%");
            }
            ++i;
        }
        repaint();
        System.out.println("loading finished...");
    }

    private static Pair[] asArrayPair(final Collection<Pair> entries) {
        return entries.toArray(new Pair[entries.size()]);
    }

    @Override
    public void paint(final Graphics gfx) {
        super.paint(gfx);
        final Graphics2D g = (Graphics2D) gfx;
        final Rectangle r = getBounds();
        g.setColor(getBackground());
        g.fill(r);
        g.translate(-offX, -offZ);
        final Pair[] reloadEntries;
        synchronized (reload) {            
            reloadEntries = asArrayPair(reload.keySet());
        }
        for (final Pair pos : reloadEntries) {
            if (isValidPos(g, pos)) {
                synchronized (chunksToReload) {                
                    chunksToReload.add(pos);
                }
                synchronized (reloader) {
                    reloader.notify();
                }
            }
        }
        final Pair[] chunksEntries;
        synchronized (chunks) {
            chunksEntries = asArrayPair(chunks.keySet());
        }
        for (final Pair pos : chunksEntries) {
            final Chunk c;
            synchronized (chunks) {                
                c = chunks.get(pos);
            }
            if (!isValidPos(g, pos)) {
                if(c != null) {
                    synchronized (mayUnload) {                        
                        mayUnload.add(c);
                    }
                }
                continue;
            }
            if(c != null) {
                synchronized (mayUnload) {                    
                    mayUnload.remove(c);
                }
            }
            final double x = pos.x * scale;
            final double z = pos.z * scale;
            final Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(x, z);
            drawChunk(g2, c);
            g2.dispose();
        }
    }

    private boolean isValidPos(final Graphics2D g, final Pair pos) {
        final double x = pos.x * scale;
        final double z = pos.z * scale;
        final Rectangle2D rect = new Rectangle2D.Double(x, z, 16 * scale,
                16 * scale);
        return g.hitClip((int) rect.getMinX() - 1, (int) rect.getMinY() - 1,
                (int) rect.getWidth() + 2, (int) rect.getHeight() + 2);
    }

    private void handleFullMemory() {
        System.err.println("full memory cleanup");
        for (;;) {
            // allocating no more memory but avoiding concurrent modification
            // exception
            final Chunk c;
            synchronized (mayUnload) {
            final Iterator<Chunk> it = mayUnload.iterator();
            if (!it.hasNext()) {
                break;
            }
            c= it.next();
            it.remove();
            }
            unloadChunk(c);
        }
        System.gc();
    }

    protected void unloadChunk(final Chunk chunk) {
        final Image img;
        synchronized (imgCache) {            
            img = imgCache.remove(chunk);
        }
        if (img != null) {
            img.flush();
        }
        if (selChunk == chunk) {
            selChunk = null;
        }
        final Pair pos = chunk.getPos();
        synchronized (chunks) {            
            chunks.remove(pos);
        }
        synchronized (reload) {            
            reload.put(pos, chunk.getFile());
        }
        synchronized (otherPos) {
            otherPos.put(pos, chunk.getOtherPos());
        }
        synchronized (mayUnload) {            
            mayUnload.remove(chunk);
        }
    }
    
    public static final double MEM_RATIO = 0.2;
    
    private static void checkHeapStatus() {
        final Runtime r = Runtime.getRuntime();
        final long free = r.freeMemory();
        final long max = r.maxMemory();
        final double ratio = (double) free / (double) max;
        if(ratio <= 0.2) {
            throw new OutOfMemoryError();
        }
    }

    protected void reloadChunk(final Pair pos) {
        boolean end = false;
        do {
            try {
                checkHeapStatus();
                final File f;
                synchronized (reload) {                    
                    f = reload.get(pos);
                }
                if(f == null) {
                    return;
                }
                final Pair op;
                synchronized (otherPos) {                    
                    op = otherPos.get(pos);
                }
                final MapReader r = new MapReader(f);
                final Chunk chunk = new Chunk(r.read(op.x, op.z), f, op);
                synchronized (chunks) {                    
                    chunks.put(pos, chunk);
                }
                synchronized (reload) {                    
                    reload.remove(pos);
                }
                synchronized (otherPos) {                    
                    otherPos.remove(pos);
                }
                end = true;
            } catch (final OutOfMemoryError e) {
                boolean canUnload;
                synchronized (mayUnload) {
                    canUnload = !mayUnload.isEmpty();
                }
                if (canUnload) {
                    handleFullMemory();
                } else {
                    throw new Error(e);
                }
            }
        } while (!end);
    }
    
    private final Image loading;
    
    private final Set<Chunk> chunksToDraw = new HashSet<Chunk>();
    
    private final Set<Pair> chunksToReload = new HashSet<Pair>();

    private final Map<Chunk, Image> imgCache = new HashMap<Chunk, Image>();

    private void drawChunk(final Graphics2D g, final Chunk chunk) {
        if(chunk == null) {
            g.drawImage(loading, 0, 0, this);
            return;
        }
        boolean contains;
        synchronized (imgCache) {
            contains = imgCache.containsKey(chunk);
        }
        if (chunk.oneTimeHasChanged() || !contains) {
            synchronized (imgCache) {
                imgCache.put(chunk, loading);
            }
            synchronized (chunksToDraw) {                
                chunksToDraw.add(chunk);
            }
            synchronized (drawer) {
                drawer.notify();
            }
        }
        Image img;
        synchronized (imgCache) {
            img = imgCache.get(chunk);
        }
        g.drawImage(img, 0, 0, this);
        if (selChunk == chunk) {
            final Rectangle2D rect = new Rectangle2D.Double(selPos.x * scale,
                    selPos.z * scale, scale, scale);
            g.setColor(new Color(0x80000000, true));
            g.fill(rect);
        }
    }
    
    private final Thread reloader = new Thread() {
        
        @Override
        public void run() {
            try {
                while(!isInterrupted()) {
                    Pair p;
                    for(;;) {
                        boolean b; 
                    synchronized (chunksToReload) {
                        b = chunksToReload.isEmpty();
                    }
                    if(!b) {
                        break;
                    }
                    synchronized (reloader) {
                        wait();
                    }
                    }
                    synchronized (chunksToReload) {
                        final Iterator<Pair> it = chunksToReload.iterator();
                        p = it.next();
                        it.remove();                        
                    }
                    reloadChunk(p);
                    repaint();
                }
            } catch(final InterruptedException e) {
                interrupt();
            }
        }
        
    };
    
    {
        reloader.setDaemon(true);
        reloader.start();
    }
    
    private final Thread drawer = new Thread() {
        
        @Override
        public void run() {
            try {
                while(!isInterrupted()) {
                    Chunk c;
                    for(;;) {
                        boolean b; 
                    synchronized (chunksToDraw) {
                        b = chunksToDraw.isEmpty();
                    }
                    if(!b) {
                        break;
                    }
                    synchronized (drawer) {
                        wait();
                    }
                    }
                    synchronized (chunksToDraw) {
                        final Iterator<Chunk> it = chunksToDraw.iterator();
                        c = it.next();
                        it.remove();                        
                    }
                    drawChunk0(c);
                    repaint();
                }
            } catch(final InterruptedException e) {
                interrupt();
            }
        }

    };
    
    {
        drawer.setDaemon(true);
        drawer.start();
    }
    
    private void drawChunk0(final Chunk chunk) {
        final BufferedImage img = new BufferedImage((int) (scale * 16),
                (int) (scale * 16), BufferedImage.TYPE_INT_RGB);
        final Graphics2D gi = (Graphics2D) img.getGraphics();
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                final Rectangle2D rect = new Rectangle2D.Double(x * scale,
                        z * scale, scale, scale);
                gi.setColor(chunk.getColorForColumn(x, z));
                gi.fill(rect);
            }
        }
        gi.dispose();
        synchronized (imgCache) {            
            if(imgCache.containsKey(chunk)) {
                imgCache.put(chunk, img);
            }
        }
    }

}
