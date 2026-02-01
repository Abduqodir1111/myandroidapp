package com.abduqodir.qfamily.ui.tree;

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
import com.abduqodir.qfamily.R;
import com.abduqodir.qfamily.data.Person;
import com.abduqodir.qfamily.ui.tree.model.TreeData;
import com.abduqodir.qfamily.ui.tree.model.TreeEdge;
import com.abduqodir.qfamily.ui.tree.model.TreeNode;
import java.io.InputStream;
import java.util.ArrayDeque;
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
    private static final int DIM_ALPHA = 110;
    private static final float FOCUS_SCALE = 1.04f;

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
    private final List<LineSegment> edgeSegments = new ArrayList<>();
    private final Set<Long> focusNodeIds = new HashSet<>();
    private final Set<Long> focusChildEdgeIds = new HashSet<>();
    private final Set<String> focusSpouseEdgeKeys = new HashSet<>();
    private final Map<String, List<Long>> coupleChildrenByKey = new HashMap<>();

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
    private boolean isDragging;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

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
        rebuildEdgeSegments();
        rebuildFocusSet();
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
        rebuildFocusSet();
        animateSelection();
        invalidate();
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
        if (edgeSegments.isEmpty()) {
            return;
        }
        boolean focusActive = selectedId != -1L && !focusNodeIds.isEmpty();
        int originalAlpha = linePaint.getAlpha();
        for (LineSegment segment : edgeSegments) {
            if (focusActive) {
                if (segment.type == LineSegment.TYPE_PARENT) {
                    if (segment.pairKey != null) {
                        linePaint.setAlpha(isPairInFocus(segment.pairKey) ? 255 : DIM_ALPHA);
                    } else {
                        linePaint.setAlpha(focusChildEdgeIds.contains(segment.childId) ? 255 : DIM_ALPHA);
                    }
                } else {
                    linePaint.setAlpha(focusSpouseEdgeKeys.contains(segment.edgeKey) ? 255 : DIM_ALPHA);
                }
            } else if (originalAlpha != 255) {
                linePaint.setAlpha(255);
            }
            canvas.drawLine(segment.x1, segment.y1, segment.x2, segment.y2, linePaint);
        }
        linePaint.setAlpha(originalAlpha);
    }

    private void rebuildEdgeSegments() {
        edgeSegments.clear();
        coupleChildrenByKey.clear();
        if (treeData == null || treeData.edges == null || treeData.edges.isEmpty()) {
            return;
        }
        Map<Long, List<TreeNode>> parentsByChild = new HashMap<>();
        Set<String> spousePairs = new HashSet<>();
        Map<String, ParentPair> pairMap = new HashMap<>();

        for (TreeEdge edge : treeData.edges) {
            TreeNode from = nodeMap.get(edge.fromId);
            TreeNode to = nodeMap.get(edge.toId);
            if (from == null || to == null) {
                continue;
            }
            if (from.level == to.level) {
                String key = edgeKey(from.person.id, to.person.id);
                spousePairs.add(key);
                continue;
            }
            TreeNode parent = from.level < to.level ? from : to;
            TreeNode child = from.level < to.level ? to : from;
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
            TreeNode parentA = parents.get(0);
            TreeNode parentB = null;
            for (int i = 1; i < parents.size(); i++) {
                if (parents.get(i).person.id != parentA.person.id) {
                    parentB = parents.get(i);
                    break;
                }
            }
            if (parentB != null) {
                if (parentB.x < parentA.x) {
                    TreeNode tmp = parentA;
                    parentA = parentB;
                    parentB = tmp;
                }
                String pairKey = edgeKey(parentA.person.id, parentB.person.id);
                coupleChildrenByKey
                        .computeIfAbsent(pairKey, key -> new ArrayList<>())
                        .add(child.person.id);
                ParentPair pair = pairMap.get(pairKey);
                if (pair == null) {
                    pair = new ParentPair(pairKey, parentA, parentB);
                    pairMap.put(pairKey, pair);
                }
                pair.children.add(child);
            } else {
                addSingleParentSegments(child, parentA);
            }
        }

        for (ParentPair pair : pairMap.values()) {
            addParentPairSegments(pair);
        }

        if (!spousePairs.isEmpty()) {
            Set<String> added = new HashSet<>();
            for (String key : spousePairs) {
                if (coupleChildrenByKey.containsKey(key)) {
                    continue;
                }
                if (added.add(key)) {
                    String[] parts = key.split(":", 2);
                    if (parts.length != 2) {
                        continue;
                    }
                    long firstId;
                    long secondId;
                    try {
                        firstId = Long.parseLong(parts[0]);
                        secondId = Long.parseLong(parts[1]);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    TreeNode first = nodeMap.get(firstId);
                    TreeNode second = nodeMap.get(secondId);
                    if (first != null && second != null) {
                        addSpouseSegment(first, second, key);
                    }
                }
            }
        }
    }

    private void addSingleParentSegments(TreeNode child, TreeNode parent) {
        long childId = child.person.id;
        float childTop = child.y - baseRadius;
        float parentBottom = parent.y + baseRadius;
        addParentLineForChild(childId, parent.x, parentBottom, child.x, childTop);
    }

    private void addParentPairSegments(ParentPair pair) {
        if (pair.children.isEmpty()) {
            return;
        }
        TreeNode leftParent = pair.leftParent;
        TreeNode rightParent = pair.rightParent;
        float parentY = (leftParent.y + rightParent.y) / 2f;
        float childY = 0f;
        for (TreeNode child : pair.children) {
            childY += child.y;
        }
        childY /= pair.children.size();

        float junctionY = childY + (parentY - childY) * 0.55f;
        float leftBottom = leftParent.y + baseRadius;
        float rightBottom = rightParent.y + baseRadius;

        addParentLineForPair(pair.key, leftParent.x, leftBottom, leftParent.x, junctionY);
        addParentLineForPair(pair.key, rightParent.x, rightBottom, rightParent.x, junctionY);
        addParentLineForPair(pair.key, leftParent.x, junctionY, rightParent.x, junctionY);

        for (TreeNode child : pair.children) {
            float childTop = child.y - baseRadius;
            addParentLineForPair(pair.key, child.x, junctionY, child.x, childTop);
        }
    }

    private void addSpouseSegment(TreeNode first, TreeNode second, String edgeKey) {
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
        addSpouseLine(edgeKey, startX, startY, endX, endY);
    }

    private String edgeKey(long firstId, long secondId) {
        long min = Math.min(firstId, secondId);
        long max = Math.max(firstId, secondId);
        return min + ":" + max;
    }

    private void addParentLineForChild(long childId, float x1, float y1, float x2, float y2) {
        edgeSegments.add(LineSegment.forParentChild(childId, x1, y1, x2, y2));
    }

    private void addParentLineForPair(String pairKey, float x1, float y1, float x2, float y2) {
        edgeSegments.add(LineSegment.forParentPair(pairKey, x1, y1, x2, y2));
    }

    private void addSpouseLine(String edgeKey, float x1, float y1, float x2, float y2) {
        edgeSegments.add(LineSegment.forSpouse(edgeKey, x1, y1, x2, y2));
    }

    private static class LineSegment {
        static final int TYPE_PARENT = 1;
        static final int TYPE_SPOUSE = 2;

        final int type;
        final long childId;
        final String edgeKey;
        final String pairKey;
        final float x1;
        final float y1;
        final float x2;
        final float y2;

        private LineSegment(int type, long childId, String edgeKey, String pairKey,
                            float x1, float y1, float x2, float y2) {
            this.type = type;
            this.childId = childId;
            this.edgeKey = edgeKey;
            this.pairKey = pairKey;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        static LineSegment forParentChild(long childId, float x1, float y1, float x2, float y2) {
            return new LineSegment(TYPE_PARENT, childId, null, null, x1, y1, x2, y2);
        }

        static LineSegment forParentPair(String pairKey, float x1, float y1, float x2, float y2) {
            return new LineSegment(TYPE_PARENT, -1L, null, pairKey, x1, y1, x2, y2);
        }

        static LineSegment forSpouse(String edgeKey, float x1, float y1, float x2, float y2) {
            return new LineSegment(TYPE_SPOUSE, -1L, edgeKey, null, x1, y1, x2, y2);
        }
    }

    private void drawNodes(Canvas canvas) {
        boolean focusActive = selectedId != -1L && !focusNodeIds.isEmpty();
        for (TreeNode node : treeData.nodes) {
            boolean inFocus = !focusActive || focusNodeIds.contains(node.person.id);
            int alpha = inFocus ? 255 : DIM_ALPHA;
            nodePaint.setAlpha(alpha);
            initialsPaint.setAlpha(alpha);
            photoPaint.setAlpha(alpha);
            float radius = baseRadius;
            boolean selected = node.person.id == selectedId;
            if (focusActive && inFocus) {
                radius *= FOCUS_SCALE;
            }
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
        nodePaint.setAlpha(255);
        initialsPaint.setAlpha(255);
        photoPaint.setAlpha(255);
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
                activePointerId = event.getPointerId(0);
                lastTouchX = event.getX(0);
                lastTouchY = event.getY(0);
                isDragging = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!scaleDetector.isInProgress()) {
                    int index = event.getActionIndex();
                    activePointerId = event.getPointerId(index);
                    lastTouchX = event.getX(index);
                    lastTouchY = event.getY(index);
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                int pointerIndex = event.getActionIndex();
                int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    int newIndex = pointerIndex == 0 ? 1 : 0;
                    if (newIndex < event.getPointerCount()) {
                        activePointerId = event.getPointerId(newIndex);
                        lastTouchX = event.getX(newIndex);
                        lastTouchY = event.getY(newIndex);
                    } else {
                        activePointerId = MotionEvent.INVALID_POINTER_ID;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() > 1 || scaleDetector.isInProgress()) {
                    int focusIndex = event.findPointerIndex(activePointerId);
                    if (focusIndex >= 0) {
                        lastTouchX = event.getX(focusIndex);
                        lastTouchY = event.getY(focusIndex);
                    }
                    break;
                }
                int index = event.findPointerIndex(activePointerId);
                if (index < 0) {
                    break;
                }
                float dx = event.getX(index) - lastTouchX;
                float dy = event.getY(index) - lastTouchY;
                if (!isDragging) {
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        isDragging = true;
                    }
                }
                if (isDragging) {
                    translateX += dx;
                    translateY += dy;
                    postInvalidateOnAnimation();
                }
                lastTouchX = event.getX(index);
                lastTouchY = event.getY(index);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                break;
            default:
                break;
        }

        return scaleHandled || gestureHandled || true;
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
                setSelectedPersonId(-1L);
                if (listener != null) {
                    listener.onNodeSelected(null);
                }
            }
            return;
        }
        if (tapped.person.id == selectedId) {
            setSelectedPersonId(-1L);
            if (listener != null) {
                listener.onNodeSelected(null);
            }
        } else {
            setSelectedPersonId(tapped.person.id);
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

    private void rebuildFocusSet() {
        focusNodeIds.clear();
        focusChildEdgeIds.clear();
        focusSpouseEdgeKeys.clear();
        if (selectedId == -1L || treeData == null || treeData.edges == null) {
            return;
        }
        TreeNode selectedNode = nodeMap.get(selectedId);
        if (selectedNode == null) {
            return;
        }

        Map<Long, List<Long>> parentsByChild = new HashMap<>();
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        Map<Long, List<Long>> spousesByPerson = new HashMap<>();

        for (TreeEdge edge : treeData.edges) {
            TreeNode from = nodeMap.get(edge.fromId);
            TreeNode to = nodeMap.get(edge.toId);
            if (from == null || to == null) {
                continue;
            }
            if (from.level == to.level) {
                spousesByPerson
                        .computeIfAbsent(from.person.id, id -> new ArrayList<>())
                        .add(to.person.id);
                spousesByPerson
                        .computeIfAbsent(to.person.id, id -> new ArrayList<>())
                        .add(from.person.id);
                continue;
            }
            TreeNode parent = from.level < to.level ? from : to;
            TreeNode child = from.level < to.level ? to : from;
            parentsByChild
                    .computeIfAbsent(child.person.id, id -> new ArrayList<>())
                    .add(parent.person.id);
            childrenByParent
                    .computeIfAbsent(parent.person.id, id -> new ArrayList<>())
                    .add(child.person.id);
        }

        focusNodeIds.add(selectedId);

        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(selectedId);
        visited.add(selectedId);
        while (!queue.isEmpty()) {
            long current = queue.poll();
            List<Long> parents = parentsByChild.get(current);
            if (parents == null) {
                continue;
            }
            for (Long parentId : parents) {
                if (visited.add(parentId)) {
                    focusNodeIds.add(parentId);
                    queue.add(parentId);
                }
            }
        }

        queue.clear();
        visited.clear();
        queue.add(selectedId);
        visited.add(selectedId);
        while (!queue.isEmpty()) {
            long current = queue.poll();
            List<Long> children = childrenByParent.get(current);
            if (children == null) {
                continue;
            }
            for (Long childId : children) {
                if (visited.add(childId)) {
                    focusNodeIds.add(childId);
                    queue.add(childId);
                }
            }
        }

        List<Long> spouses = spousesByPerson.get(selectedId);
        if (spouses != null) {
            for (Long spouseId : spouses) {
                focusNodeIds.add(spouseId);
            }
        }

        for (Map.Entry<Long, List<Long>> entry : parentsByChild.entrySet()) {
            long childId = entry.getKey();
            if (!focusNodeIds.contains(childId)) {
                continue;
            }
            List<Long> parents = entry.getValue();
            if (parents == null) {
                continue;
            }
            for (Long parentId : parents) {
                if (focusNodeIds.contains(parentId)) {
                    focusChildEdgeIds.add(childId);
                    break;
                }
            }
        }

        Set<String> spouseKeys = new HashSet<>();
        for (Map.Entry<Long, List<Long>> entry : spousesByPerson.entrySet()) {
            long personId = entry.getKey();
            List<Long> spouseIds = entry.getValue();
            if (spouseIds == null) {
                continue;
            }
            for (Long spouseId : spouseIds) {
                String key = edgeKey(personId, spouseId);
                if (spouseKeys.add(key)
                        && !coupleChildrenByKey.containsKey(key)
                        && focusNodeIds.contains(personId)
                        && focusNodeIds.contains(spouseId)) {
                    focusSpouseEdgeKeys.add(key);
                }
            }
        }
    }

    private boolean isPairInFocus(String pairKey) {
        if (pairKey == null || focusNodeIds.isEmpty()) {
            return false;
        }
        String[] parts = pairKey.split(":", 2);
        if (parts.length == 2) {
            try {
                long first = Long.parseLong(parts[0]);
                long second = Long.parseLong(parts[1]);
                if (focusNodeIds.contains(first) || focusNodeIds.contains(second)) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        List<Long> children = coupleChildrenByKey.get(pairKey);
        if (children == null) {
            return false;
        }
        for (Long childId : children) {
            if (focusNodeIds.contains(childId)) {
                return true;
            }
        }
        return false;
    }

    private static class ParentPair {
        final String key;
        final TreeNode leftParent;
        final TreeNode rightParent;
        final List<TreeNode> children = new ArrayList<>();

        ParentPair(String key, TreeNode leftParent, TreeNode rightParent) {
            this.key = key;
            this.leftParent = leftParent;
            this.rightParent = rightParent;
        }
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
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float prevScale = scaleFactor;
            float newScale = clamp(scaleFactor * detector.getScaleFactor(), MIN_SCALE, MAX_SCALE);
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float worldX = (focusX - centerX - translateX) / prevScale;
            float worldY = (focusY - centerY - translateY) / prevScale;

            scaleFactor = newScale;
            translateX = focusX - centerX - worldX * scaleFactor;
            translateY = focusY - centerY - worldY * scaleFactor;
            postInvalidateOnAnimation();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

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

