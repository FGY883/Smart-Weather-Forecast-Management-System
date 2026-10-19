package com.weather.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 城市名校验：搜索/添加城市时只接受“城市”，拒绝“省份/自治区”等省级名称。
 *
 * 规则：
 *  - 以“市”结尾视为城市，放行（如 吉林市、广州市）。
 *  - 去掉 省/自治区/壮族/回族/维吾尔/特别行政区/自治州/地区/盟 等后缀后，
 *    若命中省级名称集合，则判为省份，拒绝。
 *  - 直辖市（北京/上海/天津/重庆）属于城市，不在拒绝集合内。
 */
public final class CityNameGuard {

    private CityNameGuard() {}

    /** 23 省 + 5 自治区（省级），输入这些名称视为无效 */
    private static final Set<String> PROVINCES = new HashSet<>(Arrays.asList(
            "河北", "山西", "辽宁", "吉林", "黑龙江", "江苏", "浙江", "安徽", "福建", "江西",
            "山东", "河南", "湖北", "湖南", "广东", "海南", "四川", "贵州", "云南", "陕西",
            "甘肃", "青海", "台湾",
            "内蒙古", "广西", "西藏", "宁夏", "新疆"
    ));

    private static final String[] SUFFIXES = {
            "特别行政区", "自治区", "自治州", "维吾尔", "壮族", "回族", "地区", "盟", "省"
    };

    /** 是否为省级名称（应被拒绝）。 */
    public static boolean isProvince(String name) {
        if (name == null) return false;
        String n = name.trim();
        if (n.isEmpty()) return false;
        if (n.endsWith("市")) return false; // 明确市级，放行
        String core = n;
        for (String s : SUFFIXES) {
            core = core.replace(s, "");
        }
        core = core.trim();
        return PROVINCES.contains(n) || PROVINCES.contains(core);
    }

    /** 统一的“仅支持城市”提示语。 */
    public static String message() {
        return "仅支持输入城市名称（如：广州、深圳、杭州），省份名称无效，请具体到城市。";
    }
}
