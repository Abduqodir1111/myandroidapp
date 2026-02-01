package com.example.myappandroid;

import android.net.Uri;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myappandroid.data.Person;
import java.util.ArrayList;
import java.util.List;

public class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.TreeViewHolder> {
    public interface OnPersonClickListener {
        void onPersonSingleClick(Person person);
        void onPersonDoubleClick(Person person);
    }

    public static class TreeNode {
        public final Person person;
        public final int depth;
        public final String relationLabel;

        public TreeNode(Person person, int depth, String relationLabel) {
            this.person = person;
            this.depth = depth;
            this.relationLabel = relationLabel;
        }
    }

    private final List<TreeNode> items = new ArrayList<>();
    private final OnPersonClickListener listener;

    public TreeAdapter(OnPersonClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<TreeNode> nodes) {
        items.clear();
        if (nodes != null) {
            items.addAll(nodes);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TreeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_person, parent, false);
        return new TreeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TreeViewHolder holder, int position) {
        TreeNode node = items.get(position);
        holder.bind(node, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TreeViewHolder extends RecyclerView.ViewHolder {
        private static final long DOUBLE_TAP_DELAY_MS = 300;
        private final View rowContainer;
        private final ImageView imageAvatar;
        private final TextView textRelation;
        private final TextView textName;
        private final TextView textMeta;
        private final int basePadding;
        private final int indentSize;
        private long lastClickTime;
        private Person currentPerson;
        private OnPersonClickListener currentListener;

        TreeViewHolder(@NonNull View itemView) {
            super(itemView);
            rowContainer = itemView.findViewById(R.id.rowContainer);
            imageAvatar = itemView.findViewById(R.id.imageAvatar);
            textRelation = itemView.findViewById(R.id.textRelation);
            textName = itemView.findViewById(R.id.textName);
            textMeta = itemView.findViewById(R.id.textMeta);
            basePadding = itemView.getResources().getDimensionPixelSize(R.dimen.tree_item_padding);
            indentSize = itemView.getResources().getDimensionPixelSize(R.dimen.tree_indent);
        }

        void bind(TreeNode node, OnPersonClickListener listener) {
            currentPerson = node.person;
            currentListener = listener;

            int leftPadding = basePadding + node.depth * indentSize;
            rowContainer.setPadding(leftPadding, basePadding, basePadding, basePadding);

            textRelation.setText(node.relationLabel);

            String displayName = PersonUtils.getDisplayName(node.person);
            textName.setText(displayName);

            String meta = PersonUtils.buildMetaLine(itemView.getContext(), node.person);
            if (meta == null || meta.trim().isEmpty()) {
                textMeta.setText("");
                textMeta.setVisibility(View.GONE);
            } else {
                textMeta.setText(meta);
                textMeta.setVisibility(View.VISIBLE);
            }

            if (node.person.photoUri != null && !node.person.photoUri.trim().isEmpty()) {
                imageAvatar.setImageURI(Uri.parse(node.person.photoUri));
            } else {
                imageAvatar.setImageResource(R.drawable.ic_person_placeholder);
            }

            itemView.setOnClickListener(v -> handleClick(v));
        }

        private void handleClick(View view) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastClickTime < DOUBLE_TAP_DELAY_MS) {
                lastClickTime = 0;
                view.removeCallbacks(singleClickRunnable);
                if (currentListener != null && currentPerson != null) {
                    currentListener.onPersonDoubleClick(currentPerson);
                }
            } else {
                lastClickTime = now;
                view.removeCallbacks(singleClickRunnable);
                view.postDelayed(singleClickRunnable, DOUBLE_TAP_DELAY_MS);
            }
        }

        private final Runnable singleClickRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentListener != null && currentPerson != null) {
                    currentListener.onPersonSingleClick(currentPerson);
                }
            }
        };
    }
}
