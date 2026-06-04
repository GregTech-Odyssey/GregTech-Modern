package com.gregtechceu.gtceu.api.data.chemical;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.Supplier;

/**
 * This is some kind of Periodic Table, which can be used to determine "Properties" of the Materials.
 */
public class Element {

    /**
     * Amount of Protons
     */
    private long protons;
    /**
     * Amount of Neutrons
     */
    private long neutrons;
    /**
     * Amount of Half Life this Material has in Seconds. -1 for stable Materials
     */
    private long halfLifeSeconds;

    /**
     * Name of the Element
     */
    private String name;
    /**
     * Symbol of the Element
     */
    private Supplier<MutableComponent> symbol;

    public long mass() {
        return protons + neutrons;
    }

    public Element(long protons, long neutrons, long halfLifeSeconds, String name, String symbol) {
        this(protons, neutrons, halfLifeSeconds, name, () -> Component.literal(symbol));
    }

    public Element(long protons, long neutrons, long halfLifeSeconds, String name, Supplier<MutableComponent> symbol) {
        this.protons = protons;
        this.neutrons = neutrons;
        this.halfLifeSeconds = halfLifeSeconds;
        this.name = name;
        this.symbol = symbol;
    }

    /**
     * Amount of Protons
     */
    public long protons() {
        return this.protons;
    }

    /**
     * Amount of Protons
     */
    public void protons(final long protons) {
        this.protons = protons;
    }

    /**
     * Amount of Neutrons
     */
    public long neutrons() {
        return this.neutrons;
    }

    /**
     * Amount of Neutrons
     */
    public void neutrons(final long neutrons) {
        this.neutrons = neutrons;
    }

    /**
     * Amount of Half Life this Material has in Seconds. -1 for stable Materials
     */
    public long halfLifeSeconds() {
        return this.halfLifeSeconds;
    }

    /**
     * Amount of Half Life this Material has in Seconds. -1 for stable Materials
     */
    public void halfLifeSeconds(final long halfLifeSeconds) {
        this.halfLifeSeconds = halfLifeSeconds;
    }

    /**
     * Name of the Element
     */
    public String name() {
        return this.name;
    }

    /**
     * Name of the Element
     */
    public void name(final String name) {
        this.name = name;
    }

    /**
     * Symbol of the Element
     */
    public MutableComponent symbol() {
        return this.symbol.get();
    }

    /**
     * Symbol of the Element
     */
    public void symbol(final Supplier<MutableComponent> symbol) {
        this.symbol = symbol;
    }
}
