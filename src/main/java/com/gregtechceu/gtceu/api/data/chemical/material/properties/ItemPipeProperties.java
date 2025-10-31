package com.gregtechceu.gtceu.api.data.chemical.material.properties;

import com.gregtechceu.gtceu.utils.GTMath;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItemPipeProperties implements IMaterialProperty {

    /**
     * Items will try to take the path with the lowest priority
     * -- SETTER --
     * Sets the Priority of the item pipe
     * -- GETTER --
     * Retrieves the priority of the item pipe
     *
     * @return The item pipe priority
     * 
     * 
     */
    private int priority;

    /**
     * rate in stacks per sec
     * -- SETTER --
     * Sets the transfer rate of the item pipe
     *
     *
     * -- GETTER --
     * Retrieve the transfer rate of the item pipe
     *
     * @param transferRate The transfer rate
     * @return The transfer rate of the item pipe
     * 
     */
    private float transferRate;

    public ItemPipeProperties(int priority, float transferRate) {
        this.priority = priority;
        this.transferRate = transferRate;
    }

    /**
     * Default property constructor.
     */
    public ItemPipeProperties() {
        this(1, 0.25f);
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        if (!properties.hasProperty(PropertyKey.WOOD)) {
            properties.ensureSet(PropertyKey.INGOT, true);
        }

        if (properties.hasProperty(PropertyKey.FLUID_PIPE)) {
            throw new IllegalStateException(
                    "Material " + properties.getMaterial() +
                            " has both Fluid and Item Pipe Property, which is not allowed!");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemPipeProperties that = (ItemPipeProperties) o;
        return priority == that.priority && Float.compare(that.transferRate, transferRate) == 0;
    }

    @Override
    public int hashCode() {
        return GTMath.hashInts(priority, Float.hashCode(transferRate));
    }

    @Override
    public String toString() {
        return "ItemPipeProperties{" +
                "priority=" + priority +
                ", transferRate=" + transferRate +
                '}';
    }
}
