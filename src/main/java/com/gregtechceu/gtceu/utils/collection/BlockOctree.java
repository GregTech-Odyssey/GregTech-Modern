package com.gregtechceu.gtceu.utils.collection;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Sparse 8-way (octree) index over integer block coordinates, optimised for query-heavy block lookups.
 *
 * <h2>Layout</h2>
 * Everything lives in <b>flat arrays</b>; there is no node object, so descending the tree costs one
 * array load per level instead of chasing {@code Node} objects and their child arrays (two dependent
 * cache misses per level, which dominated the cost once the tree outgrew the CPU cache).
 *
 * <ul>
 * <li><b>Node arena</b> - {@code int[8] per node}. An <i>inner</i> node stores 8 child references
 * (encoded {@code index + 1}, {@code 0} = empty). A <i>leaf</i> node stores
 * {@code [LEAF, start, count, capacity]} in the same 8 slots.</li>
 * <li><b>Entry pool</b> - {@code int[] coords} with three interleaved <b>local</b> coordinates per entry
 * slot plus {@code Object[] values}. A leaf owns a contiguous slot range, so a query touches one
 * contiguous run per leaf and inserts do not allocate per leaf. Released blocks go on a free stack, and
 * splitting partitions a leaf's own block in place (no allocation, no relocation).</li>
 * <li>Bounds are <b>never stored</b>: insert only needs the low bits of the local coordinate, queries
 * pass origin+size down the call stack. Local coordinates make every internal computation
 * translation-invariant, so negative world coordinates and any region shape are handled by
 * construction.</li>
 * </ul>
 *
 * <p>
 * Costs: {@code insert}/{@code get}/{@code remove} are O(depth) array loads plus one linear leaf scan
 * (&lt;= leafCapacity entries); {@code querySphere}/{@code findAny} are output sensitive (nodes
 * overlapping the sphere + entries inside them) and pruned by node-to-sphere distance, with a
 * whole-subtree shortcut when a box is entirely inside the sphere; {@code size()} is O(1).
 *
 * <p>
 * Values must be non-null ({@code null} doubles as "absent"). Not thread-safe. Query methods never
 * allocate unless a collecting overload is used.
 *
 * @param <T> value type stored per block position
 */
@SuppressWarnings("unchecked")
public final class BlockOctree<T> {

    /**
     * Leaf bucket size used by the convenience constructors. Tuned with
     * {@code jmh/.../LeafCapacityBenchmark}: 32 beats 8 by ~2x on sphere queries and is no worse on
     * insert / point lookups, because it removes tree levels that would otherwise have to be walked.
     */
    public static final int DEFAULT_LEAF_CAPACITY = 32;

    /** Leaf blocks start this small and grow on demand, so sparse leaves cost almost nothing. */
    private static final int INITIAL_LEAF_CAPACITY = 4;

    /** Ints per node slot in {@link #nodes}. */
    private static final int NODE_INTS = 8;
    /** Value of {@code nodes[base]} for a leaf node. */
    private static final int LEAF = -1;
    /** The root always lives in node slot 0. */
    private static final int ROOT = 0;

    private static final Object NOT_FOUND = new Object();

    /** World coordinate of local coordinate 0. */
    private final int originX, originY, originZ;
    /** log2 of the root cube edge; local coordinates live in [0, 1 << shift). */
    private final int shift;
    private final int rootSize;
    private final int leafCapacity;
    private final int initialCapacity;

    /** Node arena: 8 ints per node. */
    private int[] nodes;
    private int nodeTop;
    /** Free node list head, encoded {@code index + 1} (0 = empty). */
    private int freeNode;

    /** Entry pool: 3 ints per slot, plus one value reference per slot. */
    private int[] coords;
    private Object[] values;
    private int poolTop;

    /** Released leaf blocks (stack of start/capacity pairs). */
    private int[] freeStart;
    private int[] freeCap;
    private int freeTop;

    /** Reused by {@link #split} so that splitting never allocates. */
    private final int[] scratchCoords;
    private final Object[] scratchValues;
    private final int[] scratchOctant;
    private final int[] scratchCount;
    private final int[] scratchPrefix;
    private final int[] scratchCursor;

    /** Capacity of the block returned by the last {@link #allocBlock} call. */
    private int allocatedCapacity;

    private int size;

    /**
     * Creates a tree over the given bounds, using the same range for X and Z (the usual "chunk column"
     * layout).
     */
    public BlockOctree(int xzMin, int xzMax, int yMin, int yMax) {
        this(xzMin, yMin, xzMin, xzMax, yMax, xzMax, DEFAULT_LEAF_CAPACITY);
    }

    public BlockOctree(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this(minX, minY, minZ, maxX, maxY, maxZ, DEFAULT_LEAF_CAPACITY);
    }

    /**
     * @param minX         inclusive lower corner (likewise minY/minZ)
     * @param maxX         inclusive upper corner (likewise maxY/maxZ)
     * @param leafCapacity entries per leaf before it splits; 4..64 is a sane range
     */
    public BlockOctree(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int leafCapacity) {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("max must be >= min");
        }
        if (leafCapacity < 1) {
            throw new IllegalArgumentException("leafCapacity must be >= 1");
        }
        long span = Math.max((long) maxX - minX, Math.max((long) maxY - minY, (long) maxZ - minZ)) + 1L;
        int s = 64 - Long.numberOfLeadingZeros(span - 1L);
        if (s > 30) {
            throw new IllegalArgumentException("region span too large: " + span);
        }
        int edge = 1 << s;
        if ((long) minX + edge > Integer.MAX_VALUE + 1L ||
                (long) minY + edge > Integer.MAX_VALUE + 1L ||
                (long) minZ + edge > Integer.MAX_VALUE + 1L) {
            throw new IllegalArgumentException("region origin + cube size overflows int");
        }
        this.shift = s;
        this.originX = minX;
        this.originY = minY;
        this.originZ = minZ;
        this.leafCapacity = leafCapacity;
        this.initialCapacity = Math.min(INITIAL_LEAF_CAPACITY, leafCapacity);
        this.rootSize = edge;

        this.nodes = new int[(NODE_INTS << 4)];
        this.coords = new int[(3 << 6)];
        this.values = new Object[64];
        this.freeStart = new int[16];
        this.freeCap = new int[16];
        this.scratchCoords = new int[leafCapacity * 3];
        this.scratchValues = new Object[leafCapacity];
        this.scratchOctant = new int[leafCapacity];
        this.scratchCount = new int[8];
        this.scratchPrefix = new int[8];
        this.scratchCursor = new int[8];

        nodeTop = 1; // slot 0 is the root
        initLeaf(ROOT, initialCapacity);
    }

    // ------------------------------------------------------------------ mutation

    /**
     * Inserts {@code value} at the given block position.
     *
     * @return {@code true} if inserted, {@code false} if the position already holds a value or is
     *         outside this tree's cube
     */
    public boolean insert(int x, int y, int z, T value) {
        if (value == null) throw new NullPointerException("value");
        int lx = x - originX, ly = y - originY, lz = z - originZ;
        if (((lx | ly | lz) >>> shift) != 0) return false;

        int node = ROOT;
        int s = shift;
        for (;;) {
            int base = node << 3;
            if (nodes[base] == LEAF) {
                int start = nodes[base + 1];
                int count = nodes[base + 2];
                for (int i = 0, j = start * 3; i < count; i++, j += 3) {
                    if (coords[j] == lx && coords[j + 1] == ly && coords[j + 2] == lz) return false;
                }
                if (count == leafCapacity) {
                    if (s == 0) return false; // a 1-block cell cannot hold two distinct positions
                    split(node, s - 1);
                    base = node << 3;
                } else {
                    if (count == nodes[base + 3]) start = growLeaf(node, count);
                    int j = (start + count) * 3;
                    coords[j] = lx;
                    coords[j + 1] = ly;
                    coords[j + 2] = lz;
                    values[start + count] = value;
                    nodes[base + 2] = count + 1;
                    size++;
                    return true;
                }
            }
            int idx = ((lx >>> (s - 1)) & 1) | (((ly >>> (s - 1)) & 1) << 1) | (((lz >>> (s - 1)) & 1) << 2);
            s--;
            int enc = nodes[base + idx];
            if (enc == 0) {
                int child = allocNode();
                initLeaf(child, initialCapacity);
                nodes[base + idx] = child + 1;
                node = child;
            } else {
                node = enc - 1;
            }
        }
    }

    /** @return the value at the position, or {@code null} if absent or out of bounds */
    public T get(int x, int y, int z) {
        int lx = x - originX, ly = y - originY, lz = z - originZ;
        if (((lx | ly | lz) >>> shift) != 0) return null;
        int node = ROOT;
        int s = shift;
        for (;;) {
            int base = node << 3;
            if (nodes[base] == LEAF) {
                int start = nodes[base + 1];
                int count = nodes[base + 2];
                for (int i = 0, j = start * 3; i < count; i++, j += 3) {
                    if (coords[j] == lx && coords[j + 1] == ly && coords[j + 2] == lz) {
                        return (T) values[start + i];
                    }
                }
                return null;
            }
            if (s == 0) return null;
            int idx = ((lx >>> (s - 1)) & 1) | (((ly >>> (s - 1)) & 1) << 1) | (((lz >>> (s - 1)) & 1) << 2);
            s--;
            int enc = nodes[base + idx];
            if (enc == 0) return null;
            node = enc - 1;
        }
    }

    /** @return whether the position holds a value */
    public boolean contains(int x, int y, int z) {
        return get(x, y, z) != null;
    }

    /** @return the removed value, or {@code null} if the position was empty or out of bounds */
    public T remove(int x, int y, int z) {
        int lx = x - originX, ly = y - originY, lz = z - originZ;
        if (((lx | ly | lz) >>> shift) != 0) return null;
        Object removed = removeNode(ROOT, lx, ly, lz, shift);
        if (removed == NOT_FOUND) return null;
        size--;
        return (T) removed;
    }

    /** @return number of stored entries, O(1) */
    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        Arrays.fill(values, 0, poolTop, null);
        nodeTop = 1;
        freeNode = 0;
        poolTop = 0;
        freeTop = 0;
        size = 0;
        initLeaf(ROOT, initialCapacity);
    }

    /** @return whether the position falls inside the cube this tree covers */
    public boolean inBounds(int x, int y, int z) {
        return (((x - originX) | (y - originY) | (z - originZ)) >>> shift) == 0;
    }

    /**
     * Repacks the entry pool into octree (Morton) order and drops dead space.
     *
     * <p>
     * A freshly built tree stores leaves in <i>insertion</i> order, so the leaves a query touches are
     * scattered across the pool and every one of them is an independent cache miss. After a repack the
     * leaves are laid out in depth-first (= Z-curve) order, so a sphere query walks a handful of
     * <b>adjacent</b> blocks and the hardware prefetcher does the rest. Costs one O(n) pass; call it
     * after a bulk load (e.g. once per chunk/region), not per insert.
     *
     * @return the number of entries copied
     */
    public int compact() {
        if (size == 0) return 0;
        int slots = Math.max(64, size);
        int[] newCoords = new int[slots * 3];
        Object[] newValues = new Object[slots];
        int written = repack(ROOT, newCoords, newValues, 0);
        this.coords = newCoords;
        this.values = newValues;
        this.poolTop = written;
        this.freeTop = 0;
        return written;
    }

    private int repack(int node, int[] newCoords, Object[] newValues, int write) {
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            if (count > 0) {
                System.arraycopy(coords, start * 3, newCoords, write * 3, count * 3);
                System.arraycopy(values, start, newValues, write, count);
                nodes[base + 1] = write;
                nodes[base + 3] = count;
                return write + count;
            }
            nodes[base + 1] = write;
            nodes[base + 3] = 0;
            return write;
        }
        for (int i = 0; i < 8; i++) {
            int enc = nodes[base + i];
            if (enc != 0) write = repack(enc - 1, newCoords, newValues, write);
        }
        return write;
    }

    // ------------------------------------------------------------------ queries

    /**
     * Visits every entry whose block position is within {@code radius} blocks of {@code (cx, cy, cz)}
     * ({@code distance <= radius}, Euclidean, on block centres). Zero-allocation: results are pushed
     * into {@code sink}.
     *
     * @param filter optional filter, {@code null} accepts everything
     * @param sink   receives matching values
     */
    public void querySphere(int cx, int cy, int cz, int radius,
                            Predicate<? super T> filter, Consumer<? super T> sink) {
        if (radius < 0 || size == 0) return;
        querySphere(ROOT, 0, 0, 0, rootSize,
                cx - originX, cy - originY, cz - originZ, radius, (long) radius * radius, filter, sink, false);
    }

    /**
     * Collects every entry within {@code radius} blocks of {@code (cx, cy, cz)} into {@code out}.
     *
     * @param filter optional filter, {@code null} accepts everything
     * @return the number of values added to {@code out}
     */
    public int querySphere(int cx, int cy, int cz, int radius,
                           Predicate<? super T> filter, Collection<? super T> out) {
        if (radius < 0 || size == 0) return 0;
        int before = out.size();
        querySphere(ROOT, 0, 0, 0, rootSize,
                cx - originX, cy - originY, cz - originZ, radius, (long) radius * radius, filter, out::add, false);
        return out.size() - before;
    }

    /**
     * Returns the first entry inside the sphere that satisfies {@code filter}, stopping the search as
     * soon as one is found. Meant for "is there anything interesting near this block" checks.
     *
     * @param filter optional filter, {@code null} accepts everything
     * @return a matching value, or {@code null} if the sphere holds no match
     */
    public T findAny(int cx, int cy, int cz, int radius, Predicate<? super T> filter) {
        if (radius < 0 || size == 0) return null;
        Object r = findAny(ROOT, 0, 0, 0, rootSize,
                cx - originX, cy - originY, cz - originZ, radius, (long) radius * radius, filter, false);
        return r == NOT_FOUND ? null : (T) r;
    }

    /** @return the first entry inside the sphere, or {@code null} if the sphere is empty */
    public T findAny(int cx, int cy, int cz, int radius) {
        return findAny(cx, cy, cz, radius, null);
    }

    /**
     * Returns the first entry anywhere in the tree that satisfies {@code filter}.
     *
     * @return a matching value, or {@code null} if nothing matched
     */
    public T findAny(Predicate<? super T> filter) {
        if (size == 0) return null;
        Object r = findAnyAll(ROOT, filter);
        return r == NOT_FOUND ? null : (T) r;
    }

    /** Visits every entry, in unspecified order. */
    public void forEach(Consumer<? super T> action) {
        forEach(ROOT, action);
    }

    // ------------------------------------------------------------------ arena management

    private static int octant(int lx, int ly, int lz, int bit) {
        return ((lx >>> bit) & 1) | (((ly >>> bit) & 1) << 1) | (((lz >>> bit) & 1) << 2);
    }

    private int allocNode() {
        int idx;
        if (freeNode != 0) {
            idx = freeNode - 1;
            freeNode = nodes[idx << 3]; // the free list reuses child slot 0 as "next"
        } else {
            idx = nodeTop++;
            int needed = (idx + 1) << 3;
            if (needed > nodes.length) {
                nodes = Arrays.copyOf(nodes, Math.max(needed, nodes.length << 1));
            }
        }
        int base = idx << 3;
        for (int i = 0; i < NODE_INTS; i++) nodes[base + i] = 0;
        return idx;
    }

    private void freeNode(int idx) {
        int base = idx << 3;
        nodes[base] = freeNode;
        freeNode = idx + 1;
    }

    /**
     * Reserves a leaf block with at least {@code capacity} slots.
     *
     * @return the first slot; the reserved capacity is reported in {@link #allocatedCapacity}
     */
    private int allocBlock(int capacity) {
        if (freeTop > 0) {
            int top = freeTop - 1;
            int available = freeCap[top];
            if (available >= capacity) {
                freeTop = top;
                allocatedCapacity = available;
                return freeStart[top];
            }
        }
        int slots = coords.length / 3;
        if (poolTop + capacity > slots) {
            int grown = slots;
            while (grown < poolTop + capacity) grown <<= 1;
            coords = Arrays.copyOf(coords, grown * 3);
            values = Arrays.copyOf(values, grown);
        }
        int start = poolTop;
        poolTop = start + capacity;
        allocatedCapacity = capacity;
        return start;
    }

    private void releaseBlock(int start, int capacity) {
        if (freeTop == freeStart.length) {
            freeStart = Arrays.copyOf(freeStart, freeTop << 1);
            freeCap = Arrays.copyOf(freeCap, freeTop << 1);
        }
        freeStart[freeTop] = start;
        freeCap[freeTop] = capacity;
        freeTop++;
    }

    private void initLeaf(int node, int capacity) {
        int start = allocBlock(capacity);
        int base = node << 3;
        nodes[base] = LEAF;
        nodes[base + 1] = start;
        nodes[base + 2] = 0;
        nodes[base + 3] = allocatedCapacity;
    }

    /** Moves a leaf's entries to a bigger block. @return the new block start */
    private int growLeaf(int node, int count) {
        int base = node << 3;
        int oldStart = nodes[base + 1];
        int oldCapacity = nodes[base + 3];
        int wanted = Math.min(Math.max(count + 1, oldCapacity << 1), leafCapacity);
        int start = allocBlock(wanted);
        int capacity = allocatedCapacity;
        System.arraycopy(coords, oldStart * 3, coords, start * 3, count * 3);
        System.arraycopy(values, oldStart, values, start, count);
        releaseBlock(oldStart, oldCapacity);
        nodes[base + 1] = start;
        nodes[base + 3] = capacity;
        return start;
    }

    /** Splits a full leaf in place: its block is partitioned by octant, children get sub-ranges. */
    private void split(int node, int childShift) {
        int base = node << 3;
        int start = nodes[base + 1];
        int count = nodes[base + 2];
        int[] octantOf = scratchOctant;
        int[] countByOctant = scratchCount;
        Arrays.fill(countByOctant, 0);
        for (int i = 0, j = start * 3; i < count; i++, j += 3) {
            int idx = octant(coords[j], coords[j + 1], coords[j + 2], childShift);
            octantOf[i] = idx;
            countByOctant[idx]++;
        }
        int[] prefix = scratchPrefix;
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            prefix[i] = sum;
            sum += countByOctant[i];
        }
        int[] cursor = scratchCursor;
        System.arraycopy(prefix, 0, cursor, 0, 8);
        int[] sortedCoords = scratchCoords;
        Object[] sortedValues = scratchValues;
        for (int i = 0, j = start * 3; i < count; i++, j += 3) {
            int pos = cursor[octantOf[i]]++;
            sortedCoords[pos * 3] = coords[j];
            sortedCoords[pos * 3 + 1] = coords[j + 1];
            sortedCoords[pos * 3 + 2] = coords[j + 2];
            sortedValues[pos] = values[start + i];
        }
        System.arraycopy(sortedCoords, 0, coords, start * 3, count * 3);
        System.arraycopy(sortedValues, 0, values, start, count);

        for (int i = 0; i < 8; i++) {
            int childCount = countByOctant[i];
            if (childCount == 0) {
                nodes[base + i] = 0;
                continue;
            }
            int child = allocNode();
            int childBase = child << 3;
            nodes[childBase] = LEAF;
            nodes[childBase + 1] = start + prefix[i];
            nodes[childBase + 2] = childCount;
            nodes[childBase + 3] = childCount;
            nodes[base + i] = child + 1;
        }
    }

    private Object removeNode(int node, int lx, int ly, int lz, int s) {
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            for (int i = 0, j = start * 3; i < count; i++, j += 3) {
                if (coords[j] == lx && coords[j + 1] == ly && coords[j + 2] == lz) {
                    Object old = values[start + i];
                    int last = count - 1;
                    if (i != last) {
                        int lastCoord = (start + last) * 3;
                        coords[j] = coords[lastCoord];
                        coords[j + 1] = coords[lastCoord + 1];
                        coords[j + 2] = coords[lastCoord + 2];
                        values[start + i] = values[start + last];
                    }
                    values[start + last] = null;
                    nodes[base + 2] = last;
                    return old;
                }
            }
            return NOT_FOUND;
        }
        if (s == 0) return NOT_FOUND;
        int idx = ((lx >>> (s - 1)) & 1) | (((ly >>> (s - 1)) & 1) << 1) | (((lz >>> (s - 1)) & 1) << 2);
        int enc = nodes[base + idx];
        if (enc == 0) return NOT_FOUND;
        int child = enc - 1;
        Object removed = removeNode(child, lx, ly, lz, s - 1);
        if (removed != NOT_FOUND) {
            int childBase = child << 3;
            if (nodes[childBase] == LEAF && nodes[childBase + 2] == 0) {
                releaseBlock(nodes[childBase + 1], nodes[childBase + 3]);
                freeNode(child);
                nodes[base + idx] = 0;
            }
        }
        return removed;
    }

    // ------------------------------------------------------------------ traversal

    private void querySphere(int node, int ox, int oy, int oz, int size,
                             int cx, int cy, int cz, int radius, long r2,
                             Predicate<? super T> filter, Consumer<? super T> sink, boolean inside) {
        int hiX = ox | (size - 1), hiY = oy | (size - 1), hiZ = oz | (size - 1);
        if (!inside) {
            int mx = cx < ox ? ox - cx : (cx > hiX ? cx - hiX : 0);
            int my = cy < oy ? oy - cy : (cy > hiY ? cy - hiY : 0);
            int mz = cz < oz ? oz - cz : (cz > hiZ ? cz - hiZ : 0);
            if ((long) mx * mx + (long) my * my + (long) mz * mz > r2) return;
            int fx = Math.max(Math.abs(cx - ox), Math.abs(cx - hiX));
            int fy = Math.max(Math.abs(cy - oy), Math.abs(cy - hiY));
            int fz = Math.max(Math.abs(cz - oz), Math.abs(cz - hiZ));
            inside = (long) fx * fx + (long) fy * fy + (long) fz * fz <= r2;
        }
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            if (inside) {
                for (int i = 0; i < count; i++) {
                    T v = (T) values[start + i];
                    if (filter == null || filter.test(v)) sink.accept(v);
                }
            } else {
                for (int i = 0, j = start * 3; i < count; i++, j += 3) {
                    int ex = coords[j] - cx;
                    if (ex > radius || ex < -radius) continue;
                    int ey = coords[j + 1] - cy;
                    if (ey > radius || ey < -radius) continue;
                    int ez = coords[j + 2] - cz;
                    if (ez > radius || ez < -radius) continue;
                    if ((long) ex * ex + (long) ey * ey + (long) ez * ez > r2) continue;
                    T v = (T) values[start + i];
                    if (filter == null || filter.test(v)) sink.accept(v);
                }
            }
            return;
        }
        int half = size >>> 1;
        int midX = ox + half - 1, midY = oy + half - 1, midZ = oz + half - 1;
        int ax0 = cx < ox ? ox - cx : (cx > midX ? cx - midX : 0);
        int ax1 = cx <= midX ? midX + 1 - cx : (cx > hiX ? cx - hiX : 0);
        int ay0 = cy < oy ? oy - cy : (cy > midY ? cy - midY : 0);
        int ay1 = cy <= midY ? midY + 1 - cy : (cy > hiY ? cy - hiY : 0);
        int az0 = cz < oz ? oz - cz : (cz > midZ ? cz - midZ : 0);
        int az1 = cz <= midZ ? midZ + 1 - cz : (cz > hiZ ? cz - hiZ : 0);
        for (int i = 0; i < 8; i++) {
            int enc = nodes[base + i];
            if (enc == 0) continue;
            if (!inside) {
                long dx = (i & 1) == 0 ? ax0 : ax1;
                long dy = (i & 2) == 0 ? ay0 : ay1;
                long dz = (i & 4) == 0 ? az0 : az1;
                if (dx * dx + dy * dy + dz * dz > r2) continue;
            }
            querySphere(enc - 1,
                    ox + ((i & 1) == 0 ? 0 : half),
                    oy + ((i & 2) == 0 ? 0 : half),
                    oz + ((i & 4) == 0 ? 0 : half),
                    half, cx, cy, cz, radius, r2, filter, sink, inside);
        }
    }

    private Object findAny(int node, int ox, int oy, int oz, int size,
                           int cx, int cy, int cz, int radius, long r2,
                           Predicate<? super T> filter, boolean inside) {
        int hiX = ox | (size - 1), hiY = oy | (size - 1), hiZ = oz | (size - 1);
        if (!inside) {
            int mx = cx < ox ? ox - cx : (cx > hiX ? cx - hiX : 0);
            int my = cy < oy ? oy - cy : (cy > hiY ? cy - hiY : 0);
            int mz = cz < oz ? oz - cz : (cz > hiZ ? cz - hiZ : 0);
            if ((long) mx * mx + (long) my * my + (long) mz * mz > r2) return NOT_FOUND;
            int fx = Math.max(Math.abs(cx - ox), Math.abs(cx - hiX));
            int fy = Math.max(Math.abs(cy - oy), Math.abs(cy - hiY));
            int fz = Math.max(Math.abs(cz - oz), Math.abs(cz - hiZ));
            inside = (long) fx * fx + (long) fy * fy + (long) fz * fz <= r2;
        }
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            if (inside) {
                for (int i = 0; i < count; i++) {
                    Object v = values[start + i];
                    if (filter == null || filter.test((T) v)) return v;
                }
            } else {
                for (int i = 0, j = start * 3; i < count; i++, j += 3) {
                    int ex = coords[j] - cx;
                    if (ex > radius || ex < -radius) continue;
                    int ey = coords[j + 1] - cy;
                    if (ey > radius || ey < -radius) continue;
                    int ez = coords[j + 2] - cz;
                    if (ez > radius || ez < -radius) continue;
                    if ((long) ex * ex + (long) ey * ey + (long) ez * ez > r2) continue;
                    Object v = values[start + i];
                    if (filter == null || filter.test((T) v)) return v;
                }
            }
            return NOT_FOUND;
        }
        int half = size >>> 1;
        int midX = ox + half - 1, midY = oy + half - 1, midZ = oz + half - 1;
        int ax0 = cx < ox ? ox - cx : (cx > midX ? cx - midX : 0);
        int ax1 = cx <= midX ? midX + 1 - cx : (cx > hiX ? cx - hiX : 0);
        int ay0 = cy < oy ? oy - cy : (cy > midY ? cy - midY : 0);
        int ay1 = cy <= midY ? midY + 1 - cy : (cy > hiY ? cy - hiY : 0);
        int az0 = cz < oz ? oz - cz : (cz > midZ ? cz - midZ : 0);
        int az1 = cz <= midZ ? midZ + 1 - cz : (cz > hiZ ? cz - hiZ : 0);
        for (int i = 0; i < 8; i++) {
            int enc = nodes[base + i];
            if (enc == 0) continue;
            if (!inside) {
                long dx = (i & 1) == 0 ? ax0 : ax1;
                long dy = (i & 2) == 0 ? ay0 : ay1;
                long dz = (i & 4) == 0 ? az0 : az1;
                if (dx * dx + dy * dy + dz * dz > r2) continue;
            }
            Object r = findAny(enc - 1,
                    ox + ((i & 1) == 0 ? 0 : half),
                    oy + ((i & 2) == 0 ? 0 : half),
                    oz + ((i & 4) == 0 ? 0 : half),
                    half, cx, cy, cz, radius, r2, filter, inside);
            if (r != NOT_FOUND) return r;
        }
        return NOT_FOUND;
    }

    private Object findAnyAll(int node, Predicate<? super T> filter) {
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            for (int i = 0; i < count; i++) {
                Object v = values[start + i];
                if (filter == null || filter.test((T) v)) return v;
            }
            return NOT_FOUND;
        }
        for (int i = 0; i < 8; i++) {
            int enc = nodes[base + i];
            if (enc != 0) {
                Object r = findAnyAll(enc - 1, filter);
                if (r != NOT_FOUND) return r;
            }
        }
        return NOT_FOUND;
    }

    private void forEach(int node, Consumer<? super T> action) {
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            for (int i = 0; i < count; i++) action.accept((T) values[start + i]);
            return;
        }
        for (int i = 0; i < 8; i++) {
            int enc = nodes[base + i];
            if (enc != 0) forEach(enc - 1, action);
        }
    }
}
