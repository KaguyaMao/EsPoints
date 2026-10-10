package com.example.espoints.client;

import com.example.espoints.network.NetworkHandler;
import com.example.espoints.network.PlaceTacticalMarkerMessage;
import com.example.espoints.util.EspetroTeamBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.esradial.client.AuiRadialRenderer;
import org.esradial.client.RadialLayouts;
import org.esradial.client.RadialMenuData;
import org.esradial.core.RadialSession;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** An embedded screen host for the same AUI tree; the clicked map anchor stays immutable. */
public final class TacticalMapMarkerWheel {
    private AuiRadialRenderer renderer;
    private RadialSession<RadialMenuData.Visual> session;
    private RadialMenuData menu;
    private Map<net.minecraft.resources.ResourceLocation, RadialMenuData> pages;
    private final ArrayDeque<RadialMenuData> history = new ArrayDeque<>();
    private Screen host;
    private ResourceKey<Level> dimension;
    public boolean active() { return session != null; }
    public boolean open(double worldX, double worldZ) {
        close();
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen == null
            || !Double.isFinite(worldX) || !Double.isFinite(worldZ)) return false;
        if (!EspetroTeamBridge.canPlaceTacticalMarkerClientHint(mc.player)) {
            mc.player.displayClientMessage(Component.literal("§c当前身份不能放置战术标点。"), true); return false;
        }
        host = mc.screen; dimension = mc.level.dimension();
        pages = TacticalMarkRadialController.buildMenuTree(type -> {
            if (valid() && EspetroTeamBridge.canPlaceTacticalMarkerClientHint(mc.player))
                NetworkHandler.INSTANCE.sendToServer(new PlaceTacticalMarkerMessage(type, worldX, worldZ));
        }, id -> {
            history.push(menu); menu = RadialLayouts.apply(pages.get(id)); session.push(menu.page());
        }).stream().collect(Collectors.toMap(RadialMenuData::id, Function.identity()));
        menu = RadialLayouts.apply(pages.get(TacticalMarkRadialController.pageId("")));
        renderer = new AuiRadialRenderer();
        renderer.setInputHint("左键选择 · 内圈返回 · Esc取消");
        try {
            if (!renderer.open(menu)) { close(); return false; }
            session = new RadialSession<>(menu.page(), reason -> close());
            session.seedPrimary(GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);
            return true;
        } catch (RuntimeException error) {
            com.example.espoints.util.ModLogger.warn("地图轮盘打开失败: " + error); close(); return false;
        }
    }
    private boolean valid() {
        var mc = Minecraft.getInstance();
        return mc.screen == host && mc.player != null && !mc.player.isDeadOrDying() && mc.level != null
            && mc.level.dimension().equals(dimension) && mc.isWindowActive();
    }
    public void tick() { if (active() && !valid()) close(); }
    public void render(GuiGraphics graphics) {
        if (!active()) return;
        if (!valid()) { close(); return; }
        try {
            updateHover();
            var path = new java.util.ArrayList<String>();
            history.descendingIterator().forEachRemaining(p -> path.add(p.title().getString()));
            path.add(menu.title().getString());
            renderer.setNavigation(String.join(" / ", path), !history.isEmpty());
            renderer.update(menu, session, 1); renderer.render(graphics);
        } catch (RuntimeException error) {
            com.example.espoints.util.ModLogger.warn("地图轮盘绘制失败: " + error); close();
        }
    }
    public boolean mouse(int button, boolean pressed) {
        if (!active()) return false;
        if (!valid()) { close(); return true; }
        updateHover();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (pressed && renderer.isBackButtonHovered(menu, !history.isEmpty())) {
                back(); if (session != null) session.seedPrimary(true);
            } else session.updatePrimary(pressed);
        }
        return true;
    }
    public boolean key(int key) {
        if (!active()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return false;
    }
    private void back() {
        if (history.isEmpty()) return;
        session.back(); menu = history.pop(); session.replace(menu.page());
    }
    private void updateHover() {
        if (renderer.isBackButtonHovered(menu, !history.isEmpty())) session.hover(0, 0);
        else session.hover(renderer.mouseX(), renderer.mouseY());
    }
    public void close() {
        if (renderer != null) renderer.close();
        renderer = null; session = null; menu = null; pages = null; host = null; dimension = null; history.clear();
    }
}
