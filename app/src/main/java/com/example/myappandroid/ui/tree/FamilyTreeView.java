package com.example.myappandroid.ui.tree;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Shader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.annotation.Nullable;
import com.example.myappandroid.R;
import com.example.myappandroid.data.Person;
import com.example.myappandroid.ui.tree.model.TreeData;
import com.example.myappandroid.ui.tree.model.TreeEdge;
import com.example.myappandroid.ui.tree.model.TreeNode;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.LruCache;

public class FamilyTreeView extends View {
    public interface OnNodeInteractionListener {
        void onNodeSelected(@Nullable Person person);
        void onNodeDoubleTap(Person person);
        void onAddRelativeRequested(Person person);
    }

    private static final float MIN_SCALE = 0.6f;
    private static final float MAX_SCALE = 2.5f;
    private static final long ANIM_DURATION_MS = 180L;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint initialsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plusStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private TreeData treeData;
    private final Map<Long, TreeNode> nodeMap = new HashMap<>();
    private OnNodeInteractionListener listener;

    private float translateX;
    private float translateY;
    private float scaleFactor = 1f;
    private float baseRadius;
    private float plusRadius;
    private float nameTextHeight;
    private int bitmapSizePx;
    private float selectedScale = 1f;
    private ValueAnimator selectionAnimator;
    private long selectedId = -1L;

    private float lastTouchX;
    private float lastTouchY;
    private final float touchSlop;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private final LruCache<String, Bitmap> bitmapCache;
    private final Set<String> loadingUris = new HashSet<>();
    private final ExecutorService bitmapExecutor = Executors.newFixedThreadPool(2);

    public FamilyTreeView(Context context) {
        this(context, null);
    }

    public FamilyTreeView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FamilyTreeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        baseRadius = getResources().getDimension(R.dimen.tree_node_radius);
        plusRadius = getResources().getDimension(R.dimen.tree_plus_radius);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        linePaint.setColor(getResources().getColor(R.color.tree_line_color, null));
        linePaint.setStrokeWidth(getResources().getDimension(R.dimen.tree_line_width));

        nodePaint.setColor(getResources().getColor(R.color.tree_node_color, null));
        nodePaint.setStyle(Paint.Style.FILL);

        haloPaint.setColor(getResources().getColor(R.color.tree_halo_color, null));
        haloPaint.setStyle(Paint.Style.STROKE);
        haloPaint.setStrokeWidth(getResources().getDimension(R.dimen.tree_halo_width));

        initialsPaint.setColor(getResources().getColor(R.color.tree_initials_color, null));
        initialsPaint.setTextSize(getResources().getDimension(R.dimen.tree_initials_size));
        initialsPaint.setTextAlign(Paint.Align.CENTER);

        namePaint.setColor(getResources().getColor(R.color.tree_name_color, null));
        namePaint.setTextSize(getResources().getDimension(R.dimen.tree_name_size));
        namePaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics nameMetrics = namePaint.getFontMetrics();
        nameTextHeight = nameMetrics.descent - nameMetrics.ascent;

        bitmapSizePx = Math.max(1, Math.round(baseRadius * 2f));
        int maxMemoryKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSizeKb = maxMemoryKb / 16;
        bitmapCache = new LruCache<String, Bitmap>(cacheSizeKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };

        plusPaint.setColor(getResources().getColor(R.color.tree_plus_color, null));
        plusPaint.setStyle(Paint.Style.FILL);

        plusStrokePaint.setColor(getResources().getColor(R.color.tree_plus_icon_color, null));
        plusStrokePaint.setStyle(Paint.Style.STROKE);
        plusStrokePaint.setStrokeWidth(getResources().getDimension(R.dimen.tree_plus_stroke));
        plusStrokePaint.setStrokeCap(Paint.Cap.ROUND);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void setOnNodeInteractionListener(OnNodeInteractionListener listener) {
        this.listener = listener;
    }

    public void setTreeData(TreeData treeData) {
        this.treeData = treeData;
        nodeMap.clear();
        if (treeData != null && treeData.nodes != null) {
            for (TreeNode node : treeData.nodes) {
                nodeMap.put(node.person.id, node);
            }
        }
        invalidate();
    }

    public void setViewportState(float translateX, float translateY, float scaleFactor) {
        this.translateX = translateX;
        this.translateY = translateY;
        this.scaleFactor = clamp(scaleFactor, MIN_SCALE, MAX_SCALE);
        invalidate();
    }

    public float getTranslateXValue() {
        return translateX;
    }

    public float getTranslateYValue() {
        return translateY;
    }

    public float getScaleFactorValue() {
        return scaleFactor;
    }

    public void setSelectedPersonId(long personId) {
        if (personId == selectedId) {
            return;
        }
        selectedId = personId;
        animateSelection();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (treeData == null || treeData.nodes == null) {
            return;
        }

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        canvas.save();
        canvas.translate(centerX + translateX, centerY + translateY);
        canvas.scale(scaleFactor, scaleFactor);

        drawEdges(canvas);
        drawNodes(canvas);

        canvas.restore();
    }

    private void drawEdges(Canvas canvas) {
        if (treeData.edges == null || treeData.edges.isEmpty()) {
            return;
        }

        Map<Long, List<TreeNode>> parentsByChild = new HashMap<>();
        Set<String> spouseKeys = new HashSet<>();

        for (TreeEdge edge : treeData.edges) {
            TreeNode from = nodeMap.get(edge.fromId);
            TreeNode to = nodeMap.get(edge.toId);
            if (from == null || to == null) {
                continue;
            }

            if (from.level == to.level) {
                String key = edgeKey(from.person.id, to.person.id);
                if (spouseKeys.add(key)) {
                    drawSpouseLine(canvas, from, to);
                }
                continue;
            }

            TreeNode parent;
            TreeNode child;
            if (from.level < to.level) {
                parent = from;
                child = to;
            } else {
                parent = to;
                child = from;
            }
            parentsByChild
                    .computeIfAbsent(child.person.id, id -> new ArrayList<>())
                    .add(parent);
        }

        for (Map.Entry<Long, List<TreeNode>> entry : parentsByChild.entrySet()) {
            TreeNode child = nodeMap.get(entry.getKey());
            if (child == null) {
                continue;
            }
            List<TreeNode> parents = entry.getValue();
            if (parents == null || parents.isEmpty()) {
                continue;
            }
            drawParentConnectors(canvas, child, parents);
        }
    }

    private void drawParentConnectors(Canvas canvas, TreeNode child, List<TreeNode> parents) {
        float childX = child.x;
        float childY = child.y;
        float childTop = childY - baseRadius;

        if (parents.size() == 1) {
            TreeNode parent = parents.get(0);
            float parentBottom = parent.y + baseRadius;
            canvas.drawLine(childX, childTop, childX, parentBottom, linePaint);
            return;
        }

        float parentY = 0f;
        for (TreeNode parent : parents) {
            parentY += parent.y;
        }
        parentY /= parents.size();

        float junctionY = childY + (parentY - childY) * 0.55f;
        canvas.drawLine(childX, childTop, childX, junctionY, linePaint);

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        for (TreeNode parent : parents) {
            minX = Math.min(minX, parent.x);
            maxX = Math.max(maxX, parent.x);
        }
        canvas.drawLine(minX, junctionY, maxX, junctionY, linePaint);
        for (TreeNode parent : parents) {
            float parentBottom = parent.y + baseRadius;
            canvas.drawLine(parent.x, junctionY, parent.x, parentBottom, linePaint);
        }
    }

    private void drawSpouseLine(Canvas canvas, TreeNode first, TreeNode second) {
        float dx = second.x - first.x;
        float dy = second.y - first.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 1f) {
            return;
        }
        float offset = baseRadius * 0.95f;
        if (dist <= offset * 2f) {
            return;
        }
        float ux = dx / dist;
        float uy = dy / dist;
        float startX = first.x + ux * offset;
        float startY = first.y + uy * offset;
        float endX = second.x - ux * offset;
        float endY = second.y - uy * offset;
        canvas.drawLine(startX, startY, endX, endY, linePaint);
    }

    private String edgeKey(long firstId, long secondId) {
        long min = Math.min(firstId, secondId);
        long max = Math.max(firstId, secondId);
        return min + ":" + max;
    }

    private void drawNodes(Canvas canvas) {
        for (TreeNode node : treeData.nodes) {
            float radius = baseRadius;
            boolean selected = node.person.id == selectedId;
            if (selected) {
                radius *= selectedScale;
                canvas.drawCircle(node.x, node.y, radius + baseRadius * 0.25f, haloPaint);
            }
            canvas.drawCircle(node.x, node.y, radius, nodePaint);

            String initials = node.initials;
            if (initials.isEmpty()) {
                initials = getResources().getString(R.string.initials_placeholder);
            }
            if (node.person.photoUri != null && !node.person.photoUri.trim().isEmpty()) {
                Bitmap avatar = getOrLoadBitmap(node.person.photoUri);
                if (avatar != null) {
                    float left = node.x - baseRadius;
                    float top = node.y - baseRadius;
                    canvas.drawBitmap(avatar, left, top, photoPaint);
                } else {
                    canvas.drawText(initials, node.x, node.y + initialsPaint.getTextSize() / 3f, initialsPaint);
                }
            } else {
                canvas.drawText(initials, node.x, node.y + initialsPaint.getTextSize() / 3f, initialsPaint);
            }

            if (selected) {
                String name = node.fullName;
                if (name != null && !name.isEmpty()) {
                    float textY = node.y + radius + baseRadius * 0.9f + nameTextHeight;
                    canvas.drawText(name, node.x, textY, namePaint);
                }
                drawPlusBadge(canvas, node, radius);
            }
        }
    }

    private void drawPlusBadge(Canvas canvas, TreeNode node, float radius) {
        float badgeX = node.x + radius * 0.72f;
        float badgeY = node.y - radius * 0.72f;
        canvas.drawCircle(badgeX, badgeY, plusRadius, plusPaint);
        float half = plusRadius * 0.5f;
        canvas.drawLine(badgeX - half, badgeY, badgeX + half, badgeY, plusStrokePaint);
        canvas.drawLine(badgeX, badgeY - half, badgeX, badgeY + half, plusStrokePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean scaleHandled = scaleDetector.onTouchEvent(event);
        boolean gestureHandled = gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        translateX += dx;
                        translateY += dy;
                        postInvalidateOnAnimation();
                    }
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                }
                break;
            default:
                break;
        }

        return scaleHandled || gestureHandled || super.onTouchEvent(event);
    }

    private void handleSingleTap(float x, float y) {
        float worldX = toWorldX(x);
        float worldY = toWorldY(y);

        if (selectedId != -1L) {
            TreeNode selectedNode = nodeMap.get(selectedId);
            if (selectedNode != null && isOnPlusBadge(worldX, worldY, selectedNode)) {
                if (listener != null) {
                    listener.onAddRelativeRequested(selectedNode.person);
                }
                return;
            }
        }

        TreeNode tapped = findNodeAtWorld(worldX, worldY);
        if (tapped == null) {
            if (selectedId != -1L) {
                selectedId = -1L;
                animateSelection();
                if (listener != null) {
                    listener.onNodeSelected(null);
                }
            }
            return;
        }
        if (tapped.person.id == selectedId) {
            selectedId = -1L;
            animateSelection();
            if (listener != null) {
                listener.onNodeSelected(null);
            }
        } else {
            selectedId = tapped.person.id;
            animateSelection();
            if (listener != null) {
                listener.onNodeSelected(tapped.person);
            }
        }
    }

    private void handleDoubleTap(float x, float y) {
        TreeNode tapped = findNodeAtWorld(toWorldX(x), toWorldY(y));
        if (tapped != null && listener != null) {
            listener.onNodeDoubleTap(tapped.person);
        }
    }

    private TreeNode findNodeAtWorld(float worldX, float worldY) {
        if (treeData == null || treeData.nodes == null) {
            return null;
        }
        float radius = baseRadius * 1.2f;
        for (TreeNode node : treeData.nodes) {
            float dx = worldX - node.x;
            float dy = worldY - node.y;
            if (dx * dx + dy * dy <= radius * radius) {
                return node;
            }
        }
        return null;
    }

    private boolean isOnPlusBadge(float worldX, float worldY, TreeNode node) {
        float radius = baseRadius;
        float badgeX = node.x + radius * 0.72f;
        float badgeY = node.y - radius * 0.72f;
        float dx = worldX - badgeX;
        float dy = worldY - badgeY;
        float hit = plusRadius * 1.3f;
        return dx * dx + dy * dy <= hit * hit;
    }

    private float toWorldX(float screenX) {
        float centerX = getWidth() / 2f;
        return (screenX - centerX - translateX) / scaleFactor;
    }

    private float toWorldY(float screenY) {
        float centerY = getHeight() / 2f;
        return (screenY - centerY - translateY) / scaleFactor;
    }

    private void animateSelection() {
        float target = selectedId == -1L ? 1f : 1.08f;
        if (selectionAnimator != null) {
            selectionAnimator.cancel();
        }
        selectionAnimator = ValueAnimator.ofFloat(selectedScale, target);
        selectionAnimator.setDuration(ANIM_DURATION_MS);
        selectionAnimator.addUpdateListener(animation -> {
            selectedScale = (float) animation.getAnimatedValue();
            postInvalidateOnAnimation();
        });
        selectionAnimator.start();
    }

    private Bitmap getOrLoadBitmap(String uriString) {
        Bitmap cached = bitmapCache.get(uriString);
        if (cached != null) {
            return cached;
        }
        if (loadingUris.add(uriString)) {
            bitmapExecutor.execute(() -> {
                Bitmap loaded = decodeCircleBitmap(uriString, bitmapSizePx);
                if (loaded != null) {
                    bitmapCache.put(uriString, loaded);
                }
                loadingUris.remove(uriString);
                postInvalidateOnAnimation();
            });
        }
        return null;
    }

    private Bitmap decodeCircleBitmap(String uriString, int size) {
        try {
            Bitmap decoded = decodeSampledBitmap(uriString, size, size);
            if (decoded == null) {
                return null;
            }
            Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            android.graphics.BitmapShader shader = new android.graphics.BitmapShader(decoded, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix();
            float scale = Math.max((float) size / decoded.getWidth(), (float) size / decoded.getHeight());
            float dx = (size - decoded.getWidth() * scale) * 0.5f;
            float dy = (size - decoded.getHeight() * scale) * 0.5f;
            matrix.setScale(scale, scale);
            matrix.postTranslate(dx, dy);
            shader.setLocalMatrix(matrix);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(shader);
            float radius = size / 2f;
            canvas.drawCircle(radius, radius, radius, paint);
            decoded.recycle();
            return output;
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap decodeSampledBitmap(String uriString, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream boundsStream = getContext().getContentResolver().openInputStream(android.net.Uri.parse(uriString))) {
                if (boundsStream == null) {
                    return null;
                }
                BitmapFactory.decodeStream(boundsStream, null, options);
            }
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            try (InputStream decodeStream = getContext().getContentResolver().openInputStream(android.net.Uri.parse(uriString))) {
                if (decodeStream == null) {
                    return null;
                }
                return BitmapFactory.decodeStream(decodeStream, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        bitmapExecutor.shutdownNow();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = clamp(scaleFactor, MIN_SCALE, MAX_SCALE);
            postInvalidateOnAnimation();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            handleSingleTap(e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            handleDoubleTap(e.getX(), e.getY());
            return true;
        }
    }
}
