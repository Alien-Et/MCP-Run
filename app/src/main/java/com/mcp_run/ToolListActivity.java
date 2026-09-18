package com.mcp_run;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import android.app.AlertDialog;
import com.mcp_run.tools.MCPTool;
import com.mcp_run.tools.ToolRegistry;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 工具列表页面 - Material Design UI
 */
public class ToolListActivity extends AppCompatActivity {

    private ToolRegistry toolRegistry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tool_list);

        toolRegistry = new ToolRegistry(this);


        LinearLayout container = findViewById(R.id.toolsContainer);
        populateToolList(container);
    }

    private void populateToolList(LinearLayout container) {
        for (String category : toolRegistry.getAllCategories()) {
            TextView catTitle = new TextView(this);
            catTitle.setText(category);
            catTitle.setTextSize(15);
            catTitle.setTypeface(null, Typeface.BOLD);
            catTitle.setTextColor(ContextCompat.getColor(this, R.color.primary));
            catTitle.setPadding(4, 28, 0, 10);
            container.addView(catTitle);

            for (MCPTool tool : toolRegistry.getToolsByCategory(category)) {
                View card = createToolCard(tool, category);
                container.addView(card);
            }
        }

        TextView stats = new TextView(this);
        stats.setText("共 " + toolRegistry.getToolCount() + " 个工具 · "
                + toolRegistry.getAllCategories().size() + " 个分类");
        stats.setTextSize(13);
        stats.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
        stats.setGravity(android.view.Gravity.CENTER);
        stats.setPadding(0, 28, 0, 32);
        container.addView(stats);
    }

    private View createToolCard(MCPTool tool, String category) {
        Context ctx = this;

        MaterialCardView card = new MaterialCardView(ctx);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 10);
        card.setLayoutParams(params);
        card.setRadius(16f);
        card.setCardElevation(0f);
        card.setStrokeWidth(1);
        card.setStrokeColor(ContextCompat.getColor(ctx, R.color.card_stroke));
        card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.card_background));
        card.setClickable(true);
        card.setFocusable(true);
        card.setRippleColor(android.content.res.ColorStateList.valueOf(0x1A328FFC));

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(16, 16, 16, 16);

        // 工具名称
        TextView nameText = new TextView(ctx);
        nameText.setText(tool.getName());
        nameText.setTextSize(15);
        nameText.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
        nameText.setTypeface(null, Typeface.BOLD);
        content.addView(nameText);

        // 工具描述
        TextView descText = new TextView(ctx);
        descText.setText(tool.getDescription());
        descText.setTextSize(13);
        descText.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        descText.setPadding(0, 8, 0, 0);
        descText.setLineSpacing(4, 1);
        content.addView(descText);

        // 分类标签
        TextView catLabel = new TextView(ctx);
        catLabel.setText(category);
        catLabel.setTextSize(11);
        catLabel.setTextColor(ContextCompat.getColor(ctx, R.color.primary_light));
        catLabel.setPadding(0, 10, 0, 0);
        content.addView(catLabel);

        card.addView(content);
        card.setOnClickListener(v -> showToolDetail(tool));

        return card;
    }

    private void showToolDetail(MCPTool tool) {
        String category = toolRegistry.getCategory(tool.getName());

        StringBuilder msg = new StringBuilder();
        msg.append("分类: ").append(category).append("\n\n");
        msg.append("功能描述:\n").append(tool.getDescription()).append("\n\n");
        msg.append("参数说明:\n");

        try {
            JSONObject schema = tool.getInputSchema();
            if (schema == null) {
                msg.append("  无参数\n");
            } else {
                JSONObject props = schema.optJSONObject("properties");
                if (props != null && props.names() != null && props.names().length() > 0) {
                    JSONArray names = props.names();
                    for (int i = 0; i < names.length(); i++) {
                        String key = names.getString(i);
                        JSONObject prop = props.optJSONObject(key);
                        if (prop == null) continue;
                        
                        String desc = prop.optString("description", "");
                        String type = prop.optString("type", "");
                        String def = "";
                        if (prop.has("default")) {
                            def = " (默认: " + prop.get("default") + ")";
                        }
                        boolean required = false;
                        JSONArray requiredArr = schema.optJSONArray("required");
                        if (requiredArr != null) {
                            for (int j = 0; j < requiredArr.length(); j++) {
                                if (requiredArr.getString(j).equals(key)) {
                                    required = true;
                                    break;
                                }
                            }
                        }
                        msg.append("  · ").append(key)
                                .append(required ? " [必填]" : "")
                                .append(" (").append(type).append(")").append(def).append("\n");
                        if (!desc.isEmpty()) {
                            msg.append("    ").append(desc).append("\n");
                        }
                    }
                } else {
                    msg.append("  无参数\n");
                }
            }
        } catch (Exception e) {
            msg.append("  无参数\n");
        }

        new AlertDialog.Builder(this)
                .setTitle(tool.getName())
                .setMessage(msg.toString())
                .setPositiveButton("知道了", null)
                .show();
    }
}
