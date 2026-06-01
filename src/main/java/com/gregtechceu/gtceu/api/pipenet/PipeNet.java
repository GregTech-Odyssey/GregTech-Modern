package com.gregtechceu.gtceu.api.pipenet;

import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.PosUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import com.fast.fastcollection.O2IOpenCacheHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.Getter;

import java.util.ArrayDeque;
import java.util.Deque;

public abstract class PipeNet<NodeDataType> {

    protected final LevelPipeNet<NodeDataType, PipeNet<NodeDataType>> worldData;
    private final Long2ObjectOpenHashMap<Node<NodeDataType>> nodeByBlockPos = new Long2ObjectOpenHashMap<>();

    private final Long2IntOpenHashMap ownedChunks = new Long2IntOpenHashMap();
    @Getter
    boolean isValid = false;

    public PipeNet(LevelPipeNet<NodeDataType, ? extends PipeNet<NodeDataType>> Level) {
        // noinspection unchecked
        this.worldData = (LevelPipeNet<NodeDataType, PipeNet<NodeDataType>>) Level;
    }

    public LongSet getContainedChunks() {
        return ownedChunks.keySet();
    }

    public ServerLevel getLevel() {
        return worldData.serverLevel;
    }

    /**
     * Is called when any connection of any pipe in the net changes
     */
    public void onPipeConnectionsUpdate() {}

    public void onNeighbourUpdate(BlockPos fromPos) {}

    public Long2ObjectOpenHashMap<Node<NodeDataType>> getAllNodes() {
        return nodeByBlockPos;
    }

    public Node<NodeDataType> getNodeAt(long blockPos) {
        return nodeByBlockPos.get(blockPos);
    }

    public boolean containsNode(long blockPos) {
        return nodeByBlockPos.containsKey(blockPos);
    }

    protected void addNodeSilently(long nodePos, Node<NodeDataType> node) {
        this.nodeByBlockPos.put(nodePos, node);
        checkAddedInChunk(nodePos);
    }

    protected void addNode(long nodePos, Node<NodeDataType> node) {
        addNodeSilently(nodePos, node);
        worldData.setDirty();
    }

    protected Node<NodeDataType> removeNodeWithoutRebuilding(long nodePos) {
        Node<NodeDataType> removedNode = this.nodeByBlockPos.remove(nodePos);
        ensureRemovedFromChunk(nodePos);
        worldData.setDirty();
        return removedNode;
    }

    public void removeNode(BlockPos nodePos, long posLong) {
        if (nodeByBlockPos.containsKey(posLong)) {
            Node<NodeDataType> selfNode = removeNodeWithoutRebuilding(posLong);
            rebuildNetworkOnNodeRemoval(nodePos, selfNode);
        }
    }

    protected void checkAddedInChunk(long nodePos) {
        long chunkPos = PosUtils.getChunkLong(nodePos);
        int oldValue = this.ownedChunks.addTo(chunkPos, 1);
        if (oldValue == 0 && isValid()) {
            this.worldData.addPipeNetToChunk(chunkPos, this);
        }
    }

    protected void ensureRemovedFromChunk(long nodePos) {
        long chunkPos = PosUtils.getChunkLong(nodePos);
        int oldValue = this.ownedChunks.containsKey(chunkPos) ? ownedChunks.addTo(chunkPos, -1) : 0;
        if (oldValue == 1) {
            this.ownedChunks.remove(chunkPos);
            if (isValid()) {
                this.worldData.removePipeNetFromChunk(chunkPos, this);
            }
        }
    }

    public void updateBlockedConnections(BlockPos nodePos, long posLong, Direction facing, boolean isBlocked) {
        Node<NodeDataType> selfNode = getNodeAt(posLong);
        if (selfNode == null) return;
        if (selfNode.isBlocked(facing) == isBlocked) {
            return;
        }

        setBlocked(selfNode, facing, isBlocked);
        BlockPos offsetPos = nodePos.relative(facing);
        long offsetPosLong = offsetPos.asLong();
        PipeNet<NodeDataType> pipeNetAtOffset = worldData.getNetFromPos(offsetPos, offsetPosLong);
        if (pipeNetAtOffset == null) {
            return;
        }
        // if we are on that side of node too
        // and it is blocked now
        if (pipeNetAtOffset == this) {
            // if side was unblocked, well, there is really nothing changed in this e-net
            // if it is blocked now, but was able to connect with neighbour node before, try split networks
            if (isBlocked) {
                // need to unblock node before doing canNodesConnectCheck
                setBlocked(selfNode, facing, false);
                if (canNodesConnect(selfNode, facing, getNodeAt(offsetPosLong))) {
                    // now block again to call findAllConnectedBlocks
                    setBlocked(selfNode, facing, true);
                    Long2ObjectOpenHashMap<Node<NodeDataType>> thisENet = findAllConnectedBlocks(posLong);
                    if (!nodeByBlockPos.equals(thisENet)) {
                        // node visibility has changed, split network into 2
                        // node that code below is similar to removeNodeInternal, but only for 2 networks, and without
                        // node removal
                        PipeNet<NodeDataType> newPipeNet = worldData.createNetInstance();
                        thisENet.keySet().forEach(this::removeNodeWithoutRebuilding);
                        newPipeNet.transferNodeData(thisENet, this);
                        worldData.addPipeNet(newPipeNet);
                    }
                }
            }
            // there is another network on that side
            // if this is an unblock, and we can connect with their node, merge them

        } else if (!isBlocked) {
            Node<NodeDataType> neighbourNode = pipeNetAtOffset.getNodeAt(offsetPosLong);
            // check connection availability from both networks
            if (canNodesConnect(selfNode, facing, neighbourNode) &&
                    pipeNetAtOffset.canNodesConnect(neighbourNode, facing.getOpposite(), selfNode)) {
                // so, side is unblocked now, and nodes can connect, merge two networks
                // our network consumes other one
                uniteNetworks(pipeNetAtOffset);
            }
        }
        worldData.setDirty();
    }

    private void setBlocked(Node<NodeDataType> selfNode, Direction facing, boolean isBlocked) {
        if (!isBlocked) {
            selfNode.openConnections |= 1 << facing.ordinal();
        } else {
            selfNode.openConnections &= ~(1 << facing.ordinal());
        }
    }

    protected final void uniteNetworks(PipeNet<NodeDataType> unitedPipeNet) {
        Long2ObjectOpenHashMap<Node<NodeDataType>> allNodes = new Long2ObjectOpenHashMap<>(unitedPipeNet.nodeByBlockPos);
        worldData.removePipeNet(unitedPipeNet);
        allNodes.keySet().forEach(unitedPipeNet::removeNodeWithoutRebuilding);
        transferNodeData(allNodes, unitedPipeNet);
    }

    private boolean areNodeBlockedConnectionsCompatible(Node<NodeDataType> first, Direction firstFacing,
                                                        Node<NodeDataType> second) {
        return !first.isBlocked(firstFacing) && !second.isBlocked(firstFacing.getOpposite());
    }

    private boolean areMarksCompatible(int mark1, int mark2) {
        return mark1 == mark2 || mark1 == Node.DEFAULT_MARK || mark2 == Node.DEFAULT_MARK;
    }

    /**
     * Checks if given nodes can connect
     * Note that this logic should equal with block connection logic
     * for proper work of network
     */
    protected final boolean canNodesConnect(Node<NodeDataType> first, Direction firstFacing, Node<NodeDataType> second) {
        return areNodeBlockedConnectionsCompatible(first, firstFacing, second) &&
                areMarksCompatible(first.mark, second.mark);
    }

    // we need to search only this network
    protected Long2ObjectOpenHashMap<Node<NodeDataType>> findAllConnectedBlocks(long startPos) {
        Long2ObjectOpenHashMap<Node<NodeDataType>> observedSet = new Long2ObjectOpenHashMap<>();
        Node<NodeDataType> firstNode = getNodeAt(startPos);
        observedSet.put(startPos, firstNode);
        BlockPos.MutableBlockPos currentPos = new BlockPos.MutableBlockPos(BlockPos.getX(startPos), BlockPos.getY(startPos), BlockPos.getZ(startPos));
        Deque<Direction> moveStack = new ArrayDeque<>();
        main:
        while (true) {
            for (Direction facing : GTUtil.DIRECTIONS) {
                currentPos.move(facing);
                var currentPosLong = currentPos.asLong();
                Node<NodeDataType> secondNode = getNodeAt(currentPosLong);
                // if there is node, and it can connect with previous node, add it to list, and set previous node as
                // current
                if (secondNode != null && canNodesConnect(firstNode, facing, secondNode) && !observedSet.containsKey(currentPosLong)) {
                    observedSet.put(currentPosLong, secondNode);
                    firstNode = secondNode;
                    moveStack.push(facing.getOpposite());
                    continue main;
                } else currentPos.move(facing.getOpposite());
            }
            if (!moveStack.isEmpty()) {
                currentPos.move(moveStack.pop());
                firstNode = getNodeAt(currentPos.asLong());
            } else break;
        }
        return observedSet;
    }

    // called when node is removed to rebuild network
    protected void rebuildNetworkOnNodeRemoval(BlockPos nodePos, Node<NodeDataType> selfNode) {
        int amountOfConnectedSides = 0;
        for (Direction facing : GTUtil.DIRECTIONS) {
            BlockPos offsetPos = nodePos.relative(facing);
            if (containsNode(offsetPos.asLong()))
                amountOfConnectedSides++;
        }
        // if we are connected only on one side or not connected at all, we don't need to find connected blocks
        // because they are only on on side or doesn't exist at all
        // this saves a lot of performance in big networks, which are quite big to depth-first them fastly
        if (amountOfConnectedSides >= 2) {
            for (Direction facing : GTUtil.DIRECTIONS) {
                long offsetPos = nodePos.relative(facing).asLong();
                Node<NodeDataType> secondNode = getNodeAt(offsetPos);
                if (secondNode == null || !canNodesConnect(selfNode, facing, secondNode)) {
                    // if there isn't any neighbour node, or it wasn't connected with us, just skip it
                    continue;
                }
                Long2ObjectOpenHashMap<Node<NodeDataType>> thisENet = findAllConnectedBlocks(offsetPos);
                if (nodeByBlockPos.equals(thisENet)) {
                    // if cable on some direction contains all nodes of this network
                    // the network didn't change so keep it as is
                    break;
                } else {
                    // and use them to create new network with caching active nodes set
                    PipeNet<NodeDataType> energyNet = worldData.createNetInstance();
                    // remove blocks that aren't connected with this network
                    thisENet.keySet().forEach(this::removeNodeWithoutRebuilding);
                    energyNet.transferNodeData(thisENet, this);
                    worldData.addPipeNet(energyNet);
                }
            }
        }
        if (nodeByBlockPos.isEmpty()) {
            // if this energy net is empty now, remove it
            worldData.removePipeNet(this);
        }
        worldData.setDirty();
    }

    /**
     * Called during network split when one net needs to transfer some of it's nodes to another one
     * Use this for diving old net contents according to node amount of new network
     * For example, for fluid pipes it would remove amount of fluid contained in old nodes
     * from parent network and add it to it's own tank, keeping network contents when old network is split
     * Note that it should be called when parent net doesn't have transferredNodes in allNodes already
     */
    protected void transferNodeData(Long2ObjectOpenHashMap<Node<NodeDataType>> transferredNodes,
                                    PipeNet<NodeDataType> parentNet) {
        transferredNodes.forEach(this::addNodeSilently);
        worldData.setDirty();
    }

    /**
     * Serializes node data into specified tag compound
     * Used for writing persistent node data
     */
    protected abstract void writeNodeData(NodeDataType nodeData, CompoundTag tagCompound);

    /**
     * Deserializes node data from specified tag compound
     * Used for reading persistent node data
     */
    protected abstract NodeDataType readNodeData(CompoundTag tagCompound);

    public CompoundTag serializeNBT() {
        CompoundTag compound = new CompoundTag();
        compound.put("Nodes", serializeAllNodeList(nodeByBlockPos));
        return compound;
    }

    public void deserializeNBT(CompoundTag nbt) {
        this.nodeByBlockPos.clear();
        this.ownedChunks.clear();
        deserializeAllNodeList(nbt.getCompound("Nodes"));
    }

    protected void deserializeAllNodeList(CompoundTag compound) {
        ListTag allNodesList = compound.getList("NodeIndexes", Tag.TAG_COMPOUND);
        ListTag wirePropertiesList = compound.getList("WireProperties", Tag.TAG_COMPOUND);
        Int2ObjectMap<NodeDataType> readProperties = new Int2ObjectOpenHashMap<>();

        for (int i = 0; i < wirePropertiesList.size(); i++) {
            CompoundTag propertiesTag = wirePropertiesList.getCompound(i);
            int wirePropertiesIndex = propertiesTag.getInt("index");
            NodeDataType nodeData = readNodeData(propertiesTag);
            readProperties.put(wirePropertiesIndex, nodeData);
        }

        for (int i = 0; i < allNodesList.size(); i++) {
            CompoundTag nodeTag = allNodesList.getCompound(i);
            long pos = nodeTag.getLong("pos");
            int wirePropertiesIndex = nodeTag.getInt("index");
            NodeDataType nodeData = readProperties.get(wirePropertiesIndex);
            int openConnections = nodeTag.getInt("open");
            int mark = nodeTag.getInt("mark");
            boolean isNodeActive = nodeTag.getBoolean("active");
            addNodeSilently(pos, new Node<>(nodeData, openConnections, mark, isNodeActive));
        }
    }

    protected CompoundTag serializeAllNodeList(Long2ObjectOpenHashMap<Node<NodeDataType>> allNodes) {
        CompoundTag compound = new CompoundTag();
        ListTag allNodesList = new ListTag();
        ListTag wirePropertiesList = new ListTag();
        Object2IntMap<NodeDataType> alreadyWritten = new O2IOpenCacheHashMap<>();
        int currentIndex = 0;

        for (var entry : allNodes.long2ObjectEntrySet()) {
            long nodePos = entry.getLongKey();
            Node<NodeDataType> node = entry.getValue();
            CompoundTag nodeTag = new CompoundTag();
            nodeTag.putLong("pos", nodePos);
            int wirePropertiesIndex = alreadyWritten.getOrDefault(node.data, -1);
            if (wirePropertiesIndex == -1) {
                wirePropertiesIndex = currentIndex;
                alreadyWritten.put(node.data, wirePropertiesIndex);
                currentIndex++;
            }
            nodeTag.putInt("index", wirePropertiesIndex);
            if (node.mark != Node.DEFAULT_MARK) {
                nodeTag.putInt("mark", node.mark);
            }
            if (node.openConnections > 0) {
                nodeTag.putInt("open", node.openConnections);
            }
            if (node.isActive) {
                nodeTag.putBoolean("active", true);
            }
            allNodesList.add(nodeTag);
        }

        for (NodeDataType nodeData : alreadyWritten.keySet()) {
            int wirePropertiesIndex = alreadyWritten.getInt(nodeData);
            CompoundTag propertiesTag = new CompoundTag();
            propertiesTag.putInt("index", wirePropertiesIndex);
            writeNodeData(nodeData, propertiesTag);
            wirePropertiesList.add(propertiesTag);
        }

        compound.put("NodeIndexes", allNodesList);
        compound.put("WireProperties", wirePropertiesList);
        return compound;
    }
}
