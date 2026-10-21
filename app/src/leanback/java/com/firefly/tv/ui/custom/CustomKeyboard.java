package com.firefly.tv.ui.custom;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.firefly.tv.R;
import com.firefly.tv.databinding.ActivitySearchBinding;
import com.firefly.tv.setting.Setting;
import com.firefly.tv.ui.adapter.KeyboardAdapter;
import com.firefly.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class CustomKeyboard implements KeyboardAdapter.OnClickListener {

    private static final int MAX_BUFFER = 12;

    private final ActivitySearchBinding binding;
    private final Callback callback;
    private KeyboardAdapter adapter;
    private boolean pinyinMode;
    private final StringBuilder buffer = new StringBuilder();
    private final List<String> candidates = new ArrayList<>();

    public static void init(Callback callback, ActivitySearchBinding binding) {
        new CustomKeyboard(callback, binding).initView();
    }

    public CustomKeyboard(Callback callback, ActivitySearchBinding binding) {
        this.callback = callback;
        this.binding = binding;
    }

    private void initView() {
        binding.keyboard.setItemAnimator(null);
        binding.keyboard.setHasFixedSize(false);
        binding.keyboard.addItemDecoration(new SpaceItemDecoration(7, 8));
        binding.keyboard.setAdapter(adapter = new KeyboardAdapter(this));
        pinyinMode = Setting.isKbPinyin();
    }

    @Override
    public void onTextClick(String text) {
        if (text.isEmpty()) return;
        if (!pinyinMode) {
            insertText(text);
            return;
        }
        char c = text.charAt(0);
        if (Character.isLetter(c)) {
            if (buffer.length() >= MAX_BUFFER) return;
            buffer.append(Character.toLowerCase(c));
            refreshCandidates();
        } else if (Character.isDigit(c)) {
            int index = c - '1';
            if (index >= 0 && index < candidates.size()) {
                commitCandidate(candidates.get(index));
            } else if (c == '0') {
                clearBuffer();
            } else {
                insertText(text);
            }
        } else {
            insertText(text);
        }
    }

    @Override
    public void onIconClick(int resId) {
        StringBuilder sb = new StringBuilder(binding.keyword.getText().toString());
        int cursor = binding.keyword.getSelectionStart();
        if (resId == R.drawable.ic_setting_home) callback.showDialog();
        else if (resId == R.drawable.ic_keyboard_remote) callback.onRemote();
        else if (resId == R.drawable.ic_keyboard_search) callback.onSearch();
        else if (resId == R.drawable.ic_keyboard_left) onMoveLeft(cursor);
        else if (resId == R.drawable.ic_keyboard_right) onMoveRight(cursor);
        else if (resId == R.drawable.ic_keyboard_back) onBackspace(sb, cursor);
        else if (resId == R.drawable.ic_keyboard) togglePinyin();
    }

    // 上游为拼音/注音双布局切换（台湾用户习惯）；流萤面向大陆用户，键盘固定字母，
    // 切换键改为「拼音选字模式」与「直接输入模式」切换
    private void togglePinyin() {
        pinyinMode = !pinyinMode;
        Setting.putKbPinyin(pinyinMode);
        clearBuffer();
    }

    private void refreshCandidates() {
        LinearLayout bar = binding.candidateBar;
        bar.removeAllViews();
        candidates.clear();
        if (pinyinMode && buffer.length() > 0) {
            candidates.addAll(PinyinEngine.get().match(buffer.toString()));
            int primary = ResUtil.getColor(R.color.icon_primary);
            int accent = ContextCompat.getColor(binding.keyword.getContext(), R.color.brand_primary);
            if (candidates.isEmpty()) {
                bar.addView(newCandidateView(buffer + " 无匹配", primary, false));
            } else {
                for (int i = 0; i < candidates.size(); i++) {
                    String label = (i + 1) + " " + candidates.get(i);
                    bar.addView(newCandidateView(label, i == 0 ? accent : primary, i == 0));
                }
            }
            binding.candidateScroll.setVisibility(View.VISIBLE);
        } else {
            binding.candidateScroll.setVisibility(View.GONE);
        }
    }

    private TextView newCandidateView(String label, int color, boolean highlighted) {
        TextView view = new TextView(binding.keyword.getContext());
        view.setText(label);
        view.setTextSize(22);
        view.setTextColor(color);
        view.setPadding(18, 6, 18, 6);
        view.setGravity(Gravity.CENTER);
        if (highlighted) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.argb(38, 123, 94, 167));
            bg.setCornerRadius(10);
            view.setBackground(bg);
        }
        return view;
    }

    private void commitCandidate(String character) {
        insertText(character);
        clearBuffer();
    }

    private void clearBuffer() {
        buffer.setLength(0);
        candidates.clear();
        refreshCandidates();
    }

    private void insertText(String text) {
        StringBuilder sb = new StringBuilder(binding.keyword.getText().toString());
        int cursor = binding.keyword.getSelectionStart();
        if (binding.keyword.length() > 19) return;
        sb.insert(cursor, text);
        binding.keyword.setText(sb.toString());
        binding.keyword.setSelection(cursor + text.length());
    }

    private void onMoveLeft(int cursor) {
        binding.keyword.setSelection(--cursor < 0 ? 0 : cursor);
    }

    private void onMoveRight(int cursor) {
        binding.keyword.setSelection(++cursor > binding.keyword.length() ? binding.keyword.length() : cursor);
    }

    private void onBackspace(StringBuilder sb, int cursor) {
        if (pinyinMode && buffer.length() > 0) {
            buffer.deleteCharAt(buffer.length() - 1);
            refreshCandidates();
            return;
        }
        if (cursor <= 0) return;
        sb.deleteCharAt(cursor - 1);
        binding.keyword.setText(sb.toString());
        binding.keyword.setSelection(cursor - 1);
    }

    @Override
    public boolean onLongClick(int resId) {
        if (resId != R.drawable.ic_keyboard_back) return false;
        clearBuffer();
        binding.keyword.setText("");
        return true;
    }

    public interface Callback {

        void showDialog();

        void onRemote();

        void onSearch();
    }
}
