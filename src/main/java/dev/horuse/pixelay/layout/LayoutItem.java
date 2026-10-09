package dev.horuse.pixelay.layout;

/**
 * Anything that can be put into a layout: a container ({@link Box}) or a canvas component, which is
 * wrapped into a leaf automatically. Lets {@code row(title, spacer(), button)} mix both freely.
 */
public interface LayoutItem {}
