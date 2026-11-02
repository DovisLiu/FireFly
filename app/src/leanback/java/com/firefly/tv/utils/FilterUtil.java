package com.firefly.tv.utils;

import com.firefly.tv.bean.Filter;
import com.firefly.tv.bean.Value;

import java.util.List;
import java.util.regex.Pattern;

/**
 * leanback 独有：筛选行的显示规则（手机 flavor 不引用，故放本 flavor 的 utils，不动 main 共享代码）。
 */
public class FilterUtil {

    // 排序/热度族 = 源 filters 里「按什么排」那一组（排序/热度/最近/最新/积分/评分…），T-190-b 要求不显示。
    // 刻意不含 type/class/area/year/lang：这三族正是刘先生要保留的分类与地区。
    private static final Pattern SORT_FAMILY = Pattern.compile("排序|排行|热度|人气|最新|最近|积分|评分|评价|sort|order|hot|click|score|rating|renqi", Pattern.CASE_INSENSITIVE);

    public static boolean isSortFamily(Filter filter) {
        if (match(filter.getKey()) || match(filter.getName())) return true;
        List<Value> values = filter.getValue();
        int hit = 0;
        for (Value value : values) {
            if (value == null) continue;
            if (match(value.getN()) || match(value.getV())) hit++;
        }
        // 名称不含关键词的源只能靠取值判：要求「≥2 项命中 + 过半 + 该行取值 ≤8 项」三个条件同时成立。
        // 项数上限是给分类/地区行兜底——这两族通常十几二十项，个别取值带「最新/热门」字样时不该整行消失；
        // 排序行永远是短列表（人气/最新/评分这种三五项）。
        return values.size() <= 8 && hit >= 2 && hit * 2 >= values.size();
    }

    private static boolean match(String text) {
        return text != null && SORT_FAMILY.matcher(text).find();
    }
}
