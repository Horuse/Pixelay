package dev.horuse.pixelay.data;

/**
 * Describes how a {@link Canvas} maps its pixel grid onto the world.
 *
 * @param pixelWidth logical width of the plane in pixels
 * @param pixelHeight logical height of the plane in pixels
 * @param blockWidth world width of the plane in blocks
 * @param blockHeight world height of the plane in blocks
 */
public record CanvasResolution(double pixelWidth, double pixelHeight, double blockWidth, double blockHeight) {}
