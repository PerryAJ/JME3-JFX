package com.jme3x.jfx.injfx.input;

import static com.ss.rlib.util.linkedlist.LinkedListFactory.newLinkedList;
import com.jme3.cursors.plugins.JmeCursor;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3x.jfx.injfx.JmeOffscreenSurfaceContext;
import com.ss.rlib.util.linkedlist.LinkedList;
import javafx.collections.ObservableMap;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * The implementation of the {@link MouseInput} for using in the {@link ImageView}.
 *
 * @author JavaSaBr
 */
public class JFXMouseInput extends JFXInput implements MouseInput {

    @NotNull
    public static final String PROP_USE_LOCAL_COORDS = "JFX.mouseInput.useLocalCoords";

    @NotNull
    public static final String PROP_INVERSE_Y_COORD = "JFX.mouseInput.inverseYCoord";

    @NotNull
    private static final Map<MouseButton, Integer> MOUSE_BUTTON_TO_JME = new HashMap<>();

    static {
        MOUSE_BUTTON_TO_JME.put(MouseButton.PRIMARY, BUTTON_LEFT);
        MOUSE_BUTTON_TO_JME.put(MouseButton.SECONDARY, BUTTON_RIGHT);
        MOUSE_BUTTON_TO_JME.put(MouseButton.MIDDLE, BUTTON_MIDDLE);
    }

    /**
     * The scale factor for scrolling.
     */
    private static final int WHEEL_SCALE = 10;

    @NotNull
    private final EventHandler<MouseEvent> processMotion = this::processMotion;

    @NotNull
    private final EventHandler<MouseEvent> processPressed = this::processPressed;

    @NotNull
    private final EventHandler<MouseEvent> processReleased = this::processReleased;

    @NotNull
    private final EventHandler<ScrollEvent> processScroll = this::processScroll;

    @NotNull
    private final LinkedList<MouseMotionEvent> mouseMotionEvents;

    @NotNull
    private final LinkedList<MouseButtonEvent> mouseButtonEvents;

    private int mouseX;
    private int mouseY;
    private int mouseWheel;

    private boolean useLocalCoords;
    private boolean inverseYCoord;

    public JFXMouseInput(@NotNull final JmeOffscreenSurfaceContext context) {
        super(context);
        this.mouseMotionEvents = newLinkedList(MouseMotionEvent.class);
        this.mouseButtonEvents = newLinkedList(MouseButtonEvent.class);
    }

    @Override
    public void bind(@NotNull final Node node) {
        super.bind(node);

        node.addEventHandler(MouseEvent.MOUSE_MOVED, processMotion);
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, processPressed);
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, processReleased);
        node.addEventHandler(MouseEvent.MOUSE_DRAGGED, processMotion);
        node.addEventHandler(ScrollEvent.ANY, processScroll);

        final ObservableMap<Object, Object> properties = node.getProperties();

        useLocalCoords = properties.get(PROP_USE_LOCAL_COORDS) == Boolean.TRUE;
        inverseYCoord = properties.get(PROP_INVERSE_Y_COORD) == Boolean.TRUE;
    }

    @Override
    public void unbind() {

        if (hasNode()) {
            final Node node = getNode();
            node.removeEventHandler(MouseEvent.MOUSE_MOVED, processMotion);
            node.removeEventHandler(MouseEvent.MOUSE_DRAGGED, processMotion);
            node.removeEventHandler(MouseEvent.MOUSE_PRESSED, processPressed);
            node.removeEventHandler(MouseEvent.MOUSE_RELEASED, processReleased);
            node.removeEventHandler(ScrollEvent.ANY, processScroll);
        }

        super.unbind();
    }

    @Override
    protected void updateImpl() {

        final RawInputListener listener = getListener();

        while (!mouseMotionEvents.isEmpty()) {
            listener.onMouseMotionEvent(mouseMotionEvents.poll());
        }

        while (!mouseButtonEvents.isEmpty()) {
            listener.onMouseButtonEvent(mouseButtonEvents.poll());
        }
    }

    /**
     * Handle the scroll event.
     */
    private void processScroll(@NotNull final ScrollEvent mouseEvent) {
        onWheelScroll(mouseEvent.getDeltaX() * WHEEL_SCALE, mouseEvent.getDeltaY() * WHEEL_SCALE);
    }

    /**
     * Handle the mouse released event.
     */
    private void processReleased(@NotNull final MouseEvent mouseEvent) {
        onMouseButton(mouseEvent.getButton(), false);
    }

    /**
     * Handle the mouse pressed event.
     */
    private void processPressed(@NotNull final MouseEvent mouseEvent) {
        onMouseButton(mouseEvent.getButton(), true);
    }

    /**
     * Handle the mouse motion event.
     */
    private void processMotion(@NotNull final MouseEvent mouseEvent) {

        final double sceneX = mouseEvent.getSceneX();
        final double sceneY = mouseEvent.getSceneY();

        if (!useLocalCoords) {
            onCursorPos(sceneX, sceneY);
        } else {
            final Point2D point2D = getNode().sceneToLocal(sceneX, sceneY, true);
            onCursorPos(point2D.getX(), point2D.getY());
        }
    }

    private void onWheelScroll(final double xOffset, final double yOffset) {
        mouseWheel += yOffset;

        final MouseMotionEvent mouseMotionEvent = new MouseMotionEvent(mouseX, mouseY, 0, 0, mouseWheel, (int) Math.round(yOffset));
        mouseMotionEvent.setTime(getInputTimeNanos());

        EXECUTOR.addToExecute(() -> mouseMotionEvents.add(mouseMotionEvent));
    }

    private void onCursorPos(final double xpos, final double ypos) {

        int xDelta;
        int yDelta;

        int x = (int) Math.round(xpos);
        int y = 0;

        if(inverseYCoord) {
            if (node instanceof Region) {
                y = (int) Math.round(((Region) node).getHeight() - ypos);
            } else if (node instanceof Canvas) {
                y = (int) Math.round(((Canvas) node).getHeight() - ypos);
            } else if (node instanceof ImageView) {
                y = (int) Math.round(((ImageView) node).getFitHeight() - ypos);
            }
        } else {
            y = (int) Math.round(ypos);
        }

        if (mouseX == 0) mouseX = x;
        if (mouseY == 0) mouseY = y;

        xDelta = x - mouseX;
        yDelta = y - mouseY;

        mouseX = x;
        mouseY = y;

        if (xDelta == 0 && yDelta == 0) return;

        final MouseMotionEvent mouseMotionEvent = new MouseMotionEvent(x, y, xDelta, yDelta, mouseWheel, 0);
        mouseMotionEvent.setTime(getInputTimeNanos());

        EXECUTOR.addToExecute(() -> mouseMotionEvents.add(mouseMotionEvent));
    }

    private void onMouseButton(@NotNull final MouseButton button, final boolean pressed) {

        final MouseButtonEvent mouseButtonEvent = new MouseButtonEvent(convertButton(button), pressed, mouseX, mouseY);
        mouseButtonEvent.setTime(getInputTimeNanos());

        EXECUTOR.addToExecute(() -> mouseButtonEvents.add(mouseButtonEvent));
    }

    private int convertButton(@NotNull final MouseButton button) {
        final Integer result = MOUSE_BUTTON_TO_JME.get(button);
        return result == null ? 0 : result;
    }

    @Override
    public void setCursorVisible(final boolean visible) {
    }

    @Override
    public int getButtonCount() {
        return 3;
    }

    @Override
    public void setNativeCursor(@NotNull final JmeCursor cursor) {
    }
}
