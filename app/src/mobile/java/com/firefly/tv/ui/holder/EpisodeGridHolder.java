package com.firefly.tv.ui.holder;

import androidx.annotation.NonNull;

import com.firefly.tv.bean.Episode;
import com.firefly.tv.databinding.AdapterEpisodeGridBinding;
import com.firefly.tv.ui.adapter.EpisodeAdapter;
import com.firefly.tv.ui.base.BaseEpisodeHolder;
import com.firefly.tv.utils.EpisodeNameUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EpisodeGridHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeGridBinding binding;

    public EpisodeGridHolder(@NonNull AdapterEpisodeGridBinding binding, EpisodeAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
    }

    @Override
    public void initView(Episode item) {
        binding.text.setSelected(item.isSelected());
        binding.text.setText(episodeLabel(item.getName()));
        binding.text.setOnClickListener(v -> listener.onItemClick(item));
    }

    // β39 / beta70 批 1 第 3 项：手机集卡标题＝与 TV 同一条四步裁剪（从 leanback DetailActivity 的私有方法
    // **复制一份到 mobile 侧**，按 A 令不下沉 main——下沉会让 TV 包必须重出）。
    // 与改前的差别：原写法是 `getDesc().concat(getName())`，把源侧 desc（常为"第1集"/"HD"之类）整段拼进标题；
    // 现在 desc 一个字都不进标题（A 明令）。本文件只改显示入参，`Episode.name` 字段一字不动。
    // 四个 Pattern 与判据原文照搬 TV（含两条实测教训）：
    //   · 不留"裸数字兜底"——`Util.getNumber` 的 replaceAll("\\D+","") 会把预告片的年份 2026 当成集号；
    //   · SP／PV 用 (?=\d) 前瞻而不是 \b——(?i) 下 "sp01" 的 p|0 两侧都是 word 字符，\b 不成立。
    // β31 A4：大小前缀／扩展名后缀两步下沉到共享 EpisodeNameUtil.stripSizeAndExt（与 EpisodeHoriHolder 共用，扩展名表补 mp3）；
    //          非正片判定与「第N集」提取两步仍留本件，行为不变。
    private static final Pattern EP_LABEL_NON_FEATURE = Pattern.compile(
            "(?i)(预告|花絮|特别篇|彩蛋|幕后|访谈|主题曲|片头|片尾|OST|TRAILER|SPOILER|MV|SP(?=\\d)|PV(?=\\d))");
    private static final Pattern EP_LABEL_MARK = Pattern.compile("(?i)(?:EP|第|\\bE)\\s*(\\d{1,3})");

    private static String episodeLabel(String name) {
        if (name == null || name.isEmpty()) return "";
        String stripped = EpisodeNameUtil.stripSizeAndExt(name);
        if (stripped.isEmpty()) return name;                     // 剥完为空（异常集名）⇒ 回落原名，绝不显示空卡
        if (EP_LABEL_NON_FEATURE.matcher(stripped).find()) return stripped;   // 非正片⇒保留原名主体
        Matcher m = EP_LABEL_MARK.matcher(stripped);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n > 0) return "第" + n + "集";                     // E00 之类不造号，走回落
        }
        return stripped;                                          // 无显式集号标记⇒保留剥后原名，不猜号
    }
}
