package com.orevault.orevault.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.frame.DoorPart;
import com.orevault.orevault.core.frame.FrameLight;
import com.orevault.orevault.core.frame.PortalDoor;
import com.orevault.orevault.core.frame.PortalDoor.Leaf;
import com.orevault.orevault.core.frame.PortalDoor.LeafFrame;
import com.orevault.orevault.core.frame.PortalDoor.Segment;
import com.orevault.orevault.core.frame.PortalDoor.Tile;
import com.orevault.orevault.core.frame.VaultPortalShape;
import com.orevault.orevault.core.frame.VaultPortalShape.Axis;
import com.orevault.orevault.core.frame.VaultPortalShape.Cell;
import com.orevault.orevault.vault.VaultOpenings;
import com.orevault.orevault.vault.VaultPortalBlock;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Draws every lit portal {@link PortalDoors} knows of, and the door over it (§3.2, E3-S13, #401): the two leaves, the
 * gem in the seat, and the portal once the door is not shut. The geometry is {@link PortalDoor}'s; this class only turns
 * its plane coordinates into vertices.
 *
 * <p>The portal block draws no model, because a model in the chunk mesh cannot be hidden per frame, and its translucent
 * faces, 4 px apart, would lie in front of a shut 3 px leaf and tint it. The leaves use the block atlas and the moving
 * block render types, as a piston's moving block does, so they take the level's light and vanilla's face shading.
 */
final class PortalDoorRenderer {
    /** How far the gem sits off the leaf's face, so it never fights the seat's pixels for depth. */
    private static final double GEM_LIFT = 1 / 512.0;
    private static final int GEM_SPRITE_PX = 16;
    private static final int GEM_PX = 4;
    private static final Identifier EDGE = sprite("vault_door_edge");
    private static final Identifier GEM = sprite("vault_door_gem");
    private static final Identifier PORTAL = sprite("vault_portal");
    /** By {@link DoorPart#ordinal()}, built once rather than joined per face. */
    private static final Identifier[] PARTS = partSprites();

    private PortalDoorRenderer() {
    }

    static void submit(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        Vec3 cam = camera.pos;
        Frustum frustum = camera.cullFrustum;
        double time = PortalDoors.time(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        Sprites sprites = Sprites.of(minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS));
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector out = event.getSubmitNodeCollector();

        for (PortalDoors.Opening opening : PortalDoors.openings()) {
            VaultPortalShape shape = opening.shape;
            double reach = PortalDoor.leafLength(shape.width()) + 1;
            AABB bounds = new AABB(shape.minX(), shape.minY(), shape.minZ(),
                    shape.minX() + (shape.axis() == Axis.X ? shape.width() : 1), shape.minY() + shape.height(),
                    shape.minZ() + (shape.axis() == Axis.Z ? shape.width() : 1)).inflate(reach, 0, reach);
            if (!frustum.isVisible(bounds)) {
                continue;
            }
            double angle = opening.door ? opening.swing.angleAt(time) : PortalDoor.OPEN_DEGREES;
            boolean shut = opening.door && opening.swing.isClosedAt(time);
            int side = opening.side;
            int tier = opening.tier;
            poseStack.pushPose();
            poseStack.translate(shape.minX() - cam.x, shape.minY() - cam.y, shape.minZ() - cam.z);
            if (opening.door) {
                out.submitCustomGeometry(poseStack, RenderTypes.cutoutMovingBlock(), (pose, buffer) ->
                        drawLeaves(new Quads(pose, buffer, shape), level, sprites, side, angle, tier));
            }
            if (!shut) {
                out.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) -> {
                    Quads quads = new Quads(pose, buffer, shape);
                    for (Cell cell : shape.cells()) {
                        drawPortalCell(quads, level, sprites, shape.axis(), cell, tier, cam);
                    }
                });
            }
            poseStack.popPose();
        }

        List<BlockPos> loose = PortalDoors.looseCells();
        for (BlockPos pos : loose) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof VaultPortalBlock portal) || !frustum.isVisible(new AABB(pos))) {
                continue;
            }
            Axis axis = state.getValue(VaultOpenings.AXIS) == Direction.Axis.X ? Axis.X : Axis.Z;
            VaultPortalShape one = new VaultPortalShape(axis, pos.getX(), pos.getY(), pos.getZ(), 1, 1);
            Cell cell = new Cell(pos.getX(), pos.getY(), pos.getZ());
            int tier = portal.tier();
            poseStack.pushPose();
            poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            out.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) ->
                    drawPortalCell(new Quads(pose, buffer, one), level, sprites, axis, cell, tier, cam));
            poseStack.popPose();
        }
    }

    /** One cell of the portal strip, a single face in the middle of the cell turned to the camera, unshaded (§3.2). */
    private static void drawPortalCell(Quads quads, ClientLevel level, Sprites sprites, Axis axis, Cell cell, int tier,
            Vec3 cam) {
        double along = axis == Axis.X ? cell.x() : cell.z();
        double plane = (axis == Axis.X ? cell.z() : cell.x()) + 0.5;
        double camAcross = axis == Axis.X ? cam.z : cam.x;
        int toward = camAcross >= plane ? 1 : -1;
        TextureAtlasSprite sprite = sprites.portal();
        int light = LevelRenderer.getLightCoords(level, new BlockPos(cell.x(), cell.y(), cell.z()));
        double y = cell.y();
        double[] normal = quads.direction(0, toward);
        quads.quad(new double[][] {
                        quads.plane(along, plane, y), quads.plane(along + 1, plane, y),
                        quads.plane(along + 1, plane, y + 1), quads.plane(along, plane, y + 1)},
                new float[][] {{sprite.getU0(), sprite.getV1()}, {sprite.getU1(), sprite.getV1()},
                        {sprite.getU1(), sprite.getV0()}, {sprite.getU0(), sprite.getV0()}},
                normal[0], normal[1], normal[2], FrameLight.portalColor(tier), light);
    }

    private static void drawLeaves(Quads quads, ClientLevel level, Sprites sprites, int side, double angle, int tier) {
        VaultPortalShape shape = quads.shape;
        int width = shape.width();
        int height = shape.height();
        double thick = PortalDoor.leafThickness();
        boolean nearIsFront = side == PortalDoor.frontSide(shape.axis());
        TextureAtlasSprite edge = sprites.edge();
        float edgeU0 = edge.getU((float) (0.5 - thick / 2));
        float edgeU1 = edge.getU((float) (0.5 + thick / 2));
        for (Leaf leaf : Leaf.values()) {
            LeafFrame frame = PortalDoor.leaf(shape, leaf, side, angle);
            double length = PortalDoor.leafLength(width);
            for (Segment segment : PortalDoor.segments(leaf, width)) {
                double s0 = PortalDoor.fromHinge(leaf, segment.from(), width);
                double s1 = PortalDoor.fromHinge(leaf, segment.to(), width);
                double run = segment.to() - segment.from();
                for (int row = 0; row < height; row++) {
                    int light = lightAt(quads, level, frame, (s0 + s1) / 2, thick / 2, row + 0.5);
                    face(quads, sprites, frame, segment, s0, s1, row, 0, -1, nearIsFront, light);
                    face(quads, sprites, frame, segment, s0, s1, row, thick, 1, !nearIsFront, light);
                }
                int topLight = lightAt(quads, level, frame, (s0 + s1) / 2, thick / 2, height - 0.5);
                int bottomLight = lightAt(quads, level, frame, (s0 + s1) / 2, thick / 2, 0.5);
                float v1 = edge.getV((float) run);
                quads.quad(new double[][] {
                                quads.at(frame, s0, 0, height), quads.at(frame, s1, 0, height),
                                quads.at(frame, s1, thick, height), quads.at(frame, s0, thick, height)},
                        new float[][] {{edgeU0, edge.getV0()}, {edgeU0, v1}, {edgeU1, v1}, {edgeU1, edge.getV0()}},
                        0, 1, 0, grey(PortalDoor.shade(0, 1, 0)), topLight);
                quads.quad(new double[][] {
                                quads.at(frame, s0, 0, 0), quads.at(frame, s1, 0, 0),
                                quads.at(frame, s1, thick, 0), quads.at(frame, s0, thick, 0)},
                        new float[][] {{edgeU0, edge.getV0()}, {edgeU0, v1}, {edgeU1, v1}, {edgeU1, edge.getV0()}},
                        0, -1, 0, grey(PortalDoor.shade(0, -1, 0)), bottomLight);
            }
            for (int row = 0; row < height; row++) {
                int light = lightAt(quads, level, frame, length / 2, thick / 2, row + 0.5);
                end(quads, frame, length, 1, row, thick, edge, edgeU0, edgeU1, light);
                end(quads, frame, 0, -1, row, thick, edge, edgeU0, edgeU1, light);
            }
            if (tier > 0) {
                gem(quads, sprites, frame, leaf, width, 0, -1, nearIsFront, tier,
                        lightAt(quads, level, frame, length, thick / 2, PortalDoor.gemTop()));
                gem(quads, sprites, frame, leaf, width, thick, 1, !nearIsFront, tier,
                        lightAt(quads, level, frame, length, thick / 2, PortalDoor.gemTop()));
            }
        }
    }

    /** One row of one segment on the face at {@code t}, whose outward normal is {@code facing} times the depth. */
    private static void face(Quads quads, Sprites sprites, LeafFrame frame, Segment segment, double s0, double s1,
            int row, double t, int facing, boolean front, int light) {
        VaultPortalShape shape = quads.shape;
        Tile tile = PortalDoor.tile(front, segment, row, shape.width(), shape.height());
        TextureAtlasSprite sprite = sprites.parts()[tile.part().ordinal()];
        float uFrom = sprite.getU((float) tile.uFrom());
        float uTo = sprite.getU((float) tile.uTo());
        double[] normal = quads.direction(facing * frame.depthAlong(), facing * frame.depthAcross());
        quads.quad(new double[][] {
                        quads.at(frame, s0, t, row), quads.at(frame, s1, t, row),
                        quads.at(frame, s1, t, row + 1), quads.at(frame, s0, t, row + 1)},
                new float[][] {{uFrom, sprite.getV1()}, {uTo, sprite.getV1()}, {uTo, sprite.getV0()},
                        {uFrom, sprite.getV0()}},
                normal[0], normal[1], normal[2], grey(PortalDoor.shade(normal[0], 0, normal[2])), light);
    }

    /** The end of a leaf at {@code s}: the free end faces along the leaf, the hinge end back toward the jamb. */
    private static void end(Quads quads, LeafFrame frame, double s, int facing, int row, double thick,
            TextureAtlasSprite edge, float u0, float u1, int light) {
        double[] normal = quads.direction(facing * frame.lengthAlong(), facing * frame.lengthAcross());
        quads.quad(new double[][] {
                        quads.at(frame, s, 0, row), quads.at(frame, s, thick, row),
                        quads.at(frame, s, thick, row + 1), quads.at(frame, s, 0, row + 1)},
                new float[][] {{u0, edge.getV1()}, {u1, edge.getV1()}, {u1, edge.getV0()}, {u0, edge.getV0()}},
                normal[0], normal[1], normal[2], grey(PortalDoor.shade(normal[0], 0, normal[2])), light);
    }

    /** This leaf's half of the signet gem, which always sits on the seam, lifted just off the face at {@code t}. */
    private static void gem(Quads quads, Sprites sprites, LeafFrame frame, Leaf leaf, int width, double t, int facing,
            boolean front, int tier, int light) {
        double half = PortalDoor.leafLength(width);
        double low = leaf == Leaf.LEFT ? PortalDoor.gemFrom(width) : half;
        double high = leaf == Leaf.LEFT ? half : PortalDoor.gemTo(width);
        double lifted = t + facing * GEM_LIFT;
        double s0 = PortalDoor.fromHinge(leaf, low, width);
        double s1 = PortalDoor.fromHinge(leaf, high, width);
        TextureAtlasSprite sprite = sprites.gem();
        float u0 = gemU(sprite, tier, PortalDoor.gemU(front, low, width));
        float u1 = gemU(sprite, tier, PortalDoor.gemU(front, high, width));
        float vTop = sprite.getV0();
        float vBottom = sprite.getV((float) GEM_PX / GEM_SPRITE_PX);
        double bottom = PortalDoor.gemBottom();
        double top = PortalDoor.gemTop();
        double[] normal = quads.direction(facing * frame.depthAlong(), facing * frame.depthAcross());
        quads.quad(new double[][] {
                        quads.at(frame, s0, lifted, bottom), quads.at(frame, s1, lifted, bottom),
                        quads.at(frame, s1, lifted, top), quads.at(frame, s0, lifted, top)},
                new float[][] {{u0, vBottom}, {u1, vBottom}, {u1, vTop}, {u0, vTop}},
                normal[0], normal[1], normal[2], grey(PortalDoor.shade(normal[0], 0, normal[2])), light);
    }

    /** The gem sprite holds one 4 by 4 gem per tier, side by side along its top row. */
    private static float gemU(TextureAtlasSprite sprite, int tier, double withinGem) {
        return sprite.getU((float) ((GEM_PX * (tier - 1) + GEM_PX * withinGem) / GEM_SPRITE_PX));
    }

    private static int lightAt(Quads quads, ClientLevel level, LeafFrame frame, double s, double t, double y) {
        double[] point = frame.point(s, t);
        VaultPortalShape shape = quads.shape;
        double x = shape.axis() == Axis.X ? point[0] : point[1];
        double z = shape.axis() == Axis.X ? point[1] : point[0];
        return LevelRenderer.getLightCoords(level,
                BlockPos.containing(x, shape.minY() + y, z));
    }

    private static int grey(double shade) {
        int level = (int) Math.round(255 * Math.max(0, Math.min(1, shade)));
        return 0xFF000000 | level << 16 | level << 8 | level;
    }

    private static Identifier[] partSprites() {
        DoorPart[] parts = DoorPart.values();
        Identifier[] ids = new Identifier[parts.length];
        for (DoorPart part : parts) {
            ids[part.ordinal()] = sprite("vault_" + part.tile());
        }
        return ids;
    }

    /**
     * The sprites one frame draws with, looked up once at the start of the frame. Never kept across frames, so a resource
     * reload, which rebuilds the atlas, needs nothing reset.
     */
    private record Sprites(TextureAtlasSprite[] parts, TextureAtlasSprite edge, TextureAtlasSprite gem,
            TextureAtlasSprite portal) {
        static Sprites of(TextureAtlas atlas) {
            TextureAtlasSprite[] parts = new TextureAtlasSprite[PARTS.length];
            for (int i = 0; i < PARTS.length; i++) {
                parts[i] = atlas.getSprite(PARTS[i]);
            }
            return new Sprites(parts, atlas.getSprite(EDGE), atlas.getSprite(GEM), atlas.getSprite(PORTAL));
        }
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(OreVault.MOD_ID, "block/" + name);
    }

    /**
     * Writes quads relative to the shape's lowest corner, which the pose is translated to, so the floats stay small. Plane
     * coordinates, along and across, become world x and z by the shape's axis.
     */
    private record Quads(PoseStack.Pose pose, VertexConsumer buffer, VaultPortalShape shape) {
        /** World-relative {@code {x, y, z}} of a leaf point, {@code y} above the sill. */
        double[] at(LeafFrame frame, double s, double t, double y) {
            double[] point = frame.point(s, t);
            return relative(point[0], point[1], y);
        }

        /** A horizontal plane direction as a world {@code {x, 0, z}}. */
        double[] direction(double along, double across) {
            return shape.axis() == Axis.X ? new double[] {along, 0, across} : new double[] {across, 0, along};
        }

        /** World-relative {@code {x, y, z}} of a plane point at world height {@code worldY}. */
        double[] plane(double along, double across, double worldY) {
            return relative(along, across, worldY - shape.minY());
        }

        private double[] relative(double along, double across, double y) {
            double x = shape.axis() == Axis.X ? along : across;
            double z = shape.axis() == Axis.X ? across : along;
            return new double[] {x - shape.minX(), y, z - shape.minZ()};
        }

        /**
         * One quad from relative corners. Culling keeps a face whose corners run anticlockwise seen from outside, so the
         * corners are taken in reverse when they run the other way about the intended normal.
         */
        void quad(double[][] p, float[][] uv, double nx, double ny, double nz, int color, int light) {
            double ax = p[1][0] - p[0][0];
            double ay = p[1][1] - p[0][1];
            double az = p[1][2] - p[0][2];
            double bx = p[2][0] - p[0][0];
            double by = p[2][1] - p[0][1];
            double bz = p[2][2] - p[0][2];
            double dot = (ay * bz - az * by) * nx + (az * bx - ax * bz) * ny + (ax * by - ay * bx) * nz;
            int[] order = dot >= 0 ? new int[] {0, 1, 2, 3} : new int[] {0, 3, 2, 1};
            for (int i : order) {
                buffer.addVertex(pose, (float) p[i][0], (float) p[i][1], (float) p[i][2])
                        .setColor(color)
                        .setUv(uv[i][0], uv[i][1])
                        .setLight(light);
            }
        }
    }
}
