package com.jme3x.jfx.injfx.processor;

import static com.ss.rlib.util.ObjectUtils.notNull;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3x.jfx.injfx.JmeOffscreenSurfaceContext;
import com.jme3x.jfx.injfx.JmeToJFXApplication;
import com.jme3x.jfx.injfx.input.JFXKeyInput;
import com.jme3x.jfx.injfx.input.JFXMouseInput;
import com.jme3x.jfx.injfx.transfer.FrameTransfer;
import com.jme3x.jfx.util.JFXPlatform;
import com.ss.rlib.logging.Logger;
import com.ss.rlib.logging.LoggerLevel;
import com.ss.rlib.logging.LoggerManager;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The base implementation of scene processor for transferring frames.
 *
 * @param <T> the type of JavaFX Node
 * @author JavaSaBr
 */
@SuppressWarnings("WeakerAccess")
public abstract class AbstractFrameTransferSceneProcessor<T extends Node> implements FrameTransferSceneProcessor {

    @NotNull
    protected static final Logger LOGGER = LoggerManager.getLogger(JFXPlatform.class);

    /**
     * The width listener.
     */
    @NotNull
    protected final ChangeListener<? super Number> widthListener;

    /**
     * The height listener.
     */
    @NotNull
    protected final ChangeListener<? super Number> heightListener;

    /**
     * The ration listener.
     */
    @NotNull
    protected final ChangeListener<? super Boolean> rationListener;

    /**
     * The flag to decide when we should resize.
     */
    @NotNull
    private final AtomicInteger reshapeNeeded;

    /**
     * The render manager.
     */
    @Nullable
    private RenderManager renderManager;

    /**
     * The source view port.
     */
    @Nullable
    private ViewPort viewPort;

    /**
     * The frame transfer.
     */
    @Nullable
    private FrameTransfer frameTransfer;

    /**
     * The transfer mode.
     */
    @NotNull
    private TransferMode transferMode;

    /**
     * The JME application.
     */
    @Nullable
    private volatile JmeToJFXApplication application;

    /**
     * The destination of jMe frames.
     */
    @Nullable
    protected volatile T destination;

    /**
     * The flag is true if this processor is main.
     */
    private volatile boolean main;

    private int askWidth;
    private int askHeight;

    private boolean askFixAspect;
    private boolean enabled;

    public AbstractFrameTransferSceneProcessor() {
        transferMode = TransferMode.ALWAYS;
        askWidth = 1;
        askHeight = 1;
        main = true;
        reshapeNeeded = new AtomicInteger(2);
        widthListener = (view, oldValue, newValue) -> notifyChangedWidth(newValue);
        heightListener = (view, oldValue, newValue) -> notifyChangedHeight(newValue);
        rationListener = (view, oldValue, newValue) -> notifyChangedRatio(newValue);
    }

    /**
     * Notify about that the ratio was changed.
     *
     * @param newValue the new value of the ratio.
     */
    protected void notifyChangedRatio(@NotNull final Boolean newValue) {
        notifyComponentResized(getDestinationWidth(), getDestinationHeight(), newValue);
    }

    /**
     * Notify about that the height was changed.
     *
     * @param newValue the new value of the height.
     */
    protected void notifyChangedHeight(@NotNull final Number newValue) {
        notifyComponentResized(getDestinationWidth(), newValue.intValue(), isPreserveRatio());
    }

    /**
     * Notify about that the width was changed.
     *
     * @param newValue the new value of the width.
     */
    protected void notifyChangedWidth(@NotNull final Number newValue) {
        notifyComponentResized(newValue.intValue(), getDestinationHeight(), isPreserveRatio());
    }

    /**
     * Gets the application.
     *
     * @return the application.
     */
    protected @NotNull JmeToJFXApplication getApplication() {
        return notNull(application);
    }

    /**
     * Gets the current destination.
     *
     * @return the current destination.
     */
    protected @NotNull T getDestination() {
        return notNull(destination);
    }

    /**
     * Checks of existing destination.
     *
     * @return true if destination is exists.
     */
    protected boolean hasDestination() {
        return destination != null;
    }

    /**
     * Checks of existing application.
     *
     * @return true if destination is exists.
     */
    protected boolean hasApplication() {
        return application != null;
    }

    /**
     * Gets the file transfer.
     *
     * @return the file transfer.
     */
    protected @Nullable FrameTransfer getFrameTransfer() {
        return frameTransfer;
    }

    /**
     * Sets the file transfer.
     *
     * @param frameTransfer the file transfer.
     */
    protected void setFrameTransfer(@Nullable final FrameTransfer frameTransfer) {
        this.frameTransfer = frameTransfer;
    }

    /**
     * Sets the destination.
     *
     * @param destination the destination.
     */
    protected void setDestination(@Nullable final T destination) {
        this.destination = destination;
    }

    /**
     * Sets the application.
     *
     * @param application the application.
     */
    protected void setApplication(@Nullable final JmeToJFXApplication application) {
        this.application = application;
    }

    /**
     * Gets the view port.
     *
     * @return the view port.
     */
    protected @NotNull ViewPort getViewPort() {
        return notNull(viewPort);
    }

    /**
     * Gets the render manager.
     *
     * @return the render manager.
     */
    protected @NotNull RenderManager getRenderManager() {
        return notNull(renderManager);
    }

    /**
     * Handle resizing.
     *
     * @param newWidth  the new width.
     * @param newHeight the new height.
     * @param fixAspect true if need to fix aspect.
     */
    protected void notifyComponentResized(int newWidth, int newHeight, boolean fixAspect) {

        newWidth = Math.max(newWidth, 1);
        newHeight = Math.max(newHeight, 1);

        if (askWidth == newWidth && askWidth == newHeight && askFixAspect == fixAspect) {
            return;
        }

        askWidth = newWidth;
        askHeight = newHeight;
        askFixAspect = fixAspect;
        reshapeNeeded.set(2);

        LOGGER.debug(this, this, processor -> "notify resized to " + processor.askWidth + "x" + processor.askHeight);
    }

    @Override
    public void reshape() {
        reshapeNeeded.set(2);
    }

    /**
     * Is preserve ratio.
     *
     * @return is preserve ratio.
     */
    protected boolean isPreserveRatio() {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets destination width.
     *
     * @return the destination width.
     */
    protected int getDestinationWidth() {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets destination height.
     *
     * @return the destination height.
     */
    protected int getDestinationHeight() {
        throw new UnsupportedOperationException();
    }

    /**
     * Bind this processor.
     *
     * @param destination the destination.
     * @param application the application.
     */
    public void bind(@NotNull final T destination, @NotNull final JmeToJFXApplication application) {
        bind(destination, application, destination);
    }

    /**
     * Bind this processor.
     *
     * @param destination the destination.
     * @param application the application.
     * @param viewPort    the view port.
     */
    public void bind(@NotNull final T destination, @NotNull final JmeToJFXApplication application,
                     @NotNull final ViewPort viewPort) {
        bind(destination, application, destination, viewPort, true);
    }


    /**
     * Bind this processor.
     *
     * @param destination the destination.
     * @param application the application.
     * @param inputNode   the input node.
     */
    public void bind(@NotNull final T destination, @NotNull final JmeToJFXApplication application,
                     @NotNull final Node inputNode) {

        final RenderManager renderManager = application.getRenderManager();

        final List<ViewPort> postViews = renderManager.getPostViews();
        if (postViews.isEmpty()) {
            throw new RuntimeException("the list of a post view is empty.");
        }

        bind(destination, application, inputNode, postViews.get(postViews.size() - 1), true);
    }

    /**
     * Bind this processor.
     *
     * @param destination the destination.
     * @param application the application.
     * @param inputNode   the input node.
     * @param viewPort    the view port.
     * @param main        true if this processor is main.
     */
    public void bind(@NotNull final T destination, @NotNull final JmeToJFXApplication application,
                     @NotNull final Node inputNode, @NotNull final ViewPort viewPort, final boolean main) {

        if (hasApplication()) {
            throw new RuntimeException("This process is already bonded.");
        }

        setApplication(application);
        setEnabled(true);

        this.main = main;
        this.viewPort = viewPort;
        this.viewPort.addProcessor(this);

        JFXPlatform.runInFXThread(() -> bindDestination(application, destination, inputNode));
    }

    /**
     * Bind this processor.
     *
     * @param application the application.
     * @param destination the destination.
     * @param inputNode   the input node.
     */
    protected void bindDestination(@NotNull final JmeToJFXApplication application, @NotNull final T destination,
                                   @NotNull final Node inputNode) {

        if (!Platform.isFxApplicationThread()) {
            throw new RuntimeException("this call is not from JavaFX thread.");
        }

        if (isMain()) {
            final JmeOffscreenSurfaceContext context = (JmeOffscreenSurfaceContext) application.getContext();
            final JFXMouseInput mouseInput = context.getMouseInput();
            mouseInput.bind(inputNode);
            final JFXKeyInput keyInput = context.getKeyInput();
            keyInput.bind(inputNode);
        }

        setDestination(destination);
        bindListeners();

        destination.setPickOnBounds(true);

        notifyComponentResized(getDestinationWidth(), getDestinationHeight(), isPreserveRatio());
    }

    /**
     * Bind listeners to current destination.
     */
    protected void bindListeners() {
    }

    /**
     * Unbind this processor from its current destination.
     */
    public void unbind() {

        if (viewPort != null) {
            viewPort.removeProcessor(this);
            viewPort = null;
        }

        JFXPlatform.runInFXThread(this::unbindDestination);
    }

    /**
     * Unbind this processor from destination.
     */
    protected void unbindDestination() {

        if (!Platform.isFxApplicationThread()) {
            throw new RuntimeException("this call is not from JavaFX thread.");
        }

        if (hasApplication() && isMain()) {
            final JmeOffscreenSurfaceContext context = (JmeOffscreenSurfaceContext) getApplication().getContext();
            final JFXMouseInput mouseInput = context.getMouseInput();
            mouseInput.unbind();
            final JFXKeyInput keyInput = context.getKeyInput();
            keyInput.unbind();
        }

        setApplication(null);

        if (hasDestination()) {
            unbindListeners();
            setDestination(null);
        }
    }

    /**
     * Unbind all listeners from destination.
     */
    protected void unbindListeners() {
    }

    @Override
    public boolean isMain() {
        return main;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Reshape the current frame transfer for the new size.
     *
     * @param width     the width.
     * @param height    the height.
     * @param fixAspect true if need to fix aspect ration.
     * @return the new frame transfer.
     */
    protected @NotNull FrameTransfer reshapeInThread(final int width, final int height, final boolean fixAspect) {

        if (LOGGER.isEnabled(LoggerLevel.DEBUG)) {
            LOGGER.debug(this, "Reshape in the jME thread to " + width + "x" + height);
        }

        reshapeCurrentViewPort(width, height);

        final ViewPort viewPort = getViewPort();
        final RenderManager renderManager = getRenderManager();
        final FrameBuffer frameBuffer = viewPort.getOutputFrameBuffer();

        final FrameTransfer frameTransfer = createFrameTransfer(frameBuffer, width, height);
        frameTransfer.initFor(renderManager.getRenderer(), isMain());

        if (isMain()) {
            final JmeOffscreenSurfaceContext context = (JmeOffscreenSurfaceContext) getApplication().getContext();
            context.setHeight(height);
            context.setWidth(width);
        }

        return frameTransfer;
    }

    /**
     * Create a new frame transfer.
     *
     * @param frameBuffer the frame buffer.
     * @param width       the width.
     * @param height      the height.
     * @return the new frame transfer.
     */
    protected @NotNull FrameTransfer createFrameTransfer(@NotNull final FrameBuffer frameBuffer, final int width,
                                                         final int height) {
        throw new UnsupportedOperationException();
    }

    /**
     * Reshape the current view port.
     *
     * @param width  the width.
     * @param height the height.
     */
    protected void reshapeCurrentViewPort(final int width, final int height) {

        if (LOGGER.isEnabled(LoggerLevel.DEBUG)) {
            LOGGER.debug(this, "reshape the current view port to " + width + "x" + height);
        }

        final ViewPort viewPort = getViewPort();
        final Camera camera = viewPort.getCamera();
        final int cameraAngle = getCameraAngle();
        final float aspect = (float) camera.getWidth() / camera.getHeight();

        if (isMain()) {
            getRenderManager().notifyReshape(width, height);
            camera.setFrustumPerspective(cameraAngle, aspect, 1f, 10000);
            return;
        }

        camera.resize(width, height, true);
        camera.setFrustumPerspective(cameraAngle, aspect, 1f, 10000);

        final List<SceneProcessor> processors = viewPort.getProcessors();
        final Optional<SceneProcessor> any = processors.stream()
                .filter(sceneProcessor -> !(sceneProcessor instanceof FrameTransferSceneProcessor))
                .findAny();

        if (!any.isPresent()) {

            final FrameBuffer frameBuffer = new FrameBuffer(width, height, 1);
            frameBuffer.setDepthBuffer(Image.Format.Depth);
            frameBuffer.setColorBuffer(Image.Format.RGBA8);
            frameBuffer.setSrgb(true);

            viewPort.setOutputFrameBuffer(frameBuffer);
        }

        for (final SceneProcessor sceneProcessor : processors) {
            if (!sceneProcessor.isInitialized()) {
                sceneProcessor.initialize(renderManager, viewPort);
            } else {
                sceneProcessor.reshape(viewPort, width, height);
            }
        }
    }

    /**
     * Gets camera angle.
     *
     * @return the camera angle.
     */
    protected int getCameraAngle() {
        final String angle = System.getProperty("jfx.frame.transfer.camera.angle", "45");
        return Integer.parseInt(angle);
    }

    @Override
    public void initialize(@NotNull final RenderManager renderManager, @NotNull final ViewPort viewPort) {
        this.renderManager = renderManager;
    }

    @Override
    public void reshape(@NotNull final ViewPort viewPort, final int w, final int h) {
    }

    @Override
    public boolean isInitialized() {
        return frameTransfer != null;
    }

    @Override
    public void preFrame(final float tpf) {

    }

    @Override
    public void postQueue(@NotNull final RenderQueue renderQueue) {

    }

    @Override
    public void postFrame(@Nullable final FrameBuffer out) {

        if (!isEnabled()) {
            return;
        }

        final FrameTransfer frameTransfer = getFrameTransfer();
        if (frameTransfer != null) {
            frameTransfer.copyFrameBufferToImage(getRenderManager());
        }

        // for the next frame
        if (hasDestination() && reshapeNeeded.get() > 0 && reshapeNeeded.decrementAndGet() >= 0) {

            if (frameTransfer != null) {
                frameTransfer.dispose();
            }

            setFrameTransfer(reshapeInThread(askWidth, askHeight, askFixAspect));
        }
    }

    @Override
    public void cleanup() {

        final FrameTransfer frameTransfer = getFrameTransfer();

        if (frameTransfer != null) {
            frameTransfer.dispose();
            setFrameTransfer(null);
        }
    }

    @Override
    public void setProfiler(final AppProfiler profiler) {
    }

    @Override
    public @NotNull TransferMode getTransferMode() {
        return transferMode;
    }

    @Override
    public void setTransferMode(@NotNull final TransferMode transferMode) {
        this.transferMode = transferMode;
    }
}
