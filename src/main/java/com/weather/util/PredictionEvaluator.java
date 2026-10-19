package com.weather.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预测回测评估器（滚动原点 / walk-forward）。
 *
 * 把最近 horizon 天当作"真实值"，对每个目标日仅用其之前的历史做单步预测，
 * 再与三条曲线比较误差：① AI 预测 ② 持久性基线(=前一日实际) ③ 官方预报(历史 data_type=2 行)。
 * 用 MAE/RMSE 及其相对基线的技巧分 skill = 1 − RMSE_模型 / RMSE_基线 客观衡量可靠性。
 */
@Component
public class PredictionEvaluator {

    @Autowired
    private AiPredictionUtil aiPredictionUtil;

    private static final int MIN_TRAIN = 7;

    /**
     * @param actual   升序的历史"实际值"序列（只含已发生日期）
     * @param official 与 actual 等长、按日期对齐的"官方预报值"，缺失处以 null；可为 null
     * @param horizon  回测的目标天数（如 7）
     */
    public Map<String, Object> evaluate(double[] actual, Double[] official, int horizon) {
        Map<String, Object> out = new LinkedHashMap<>();
        int total = actual.length;
        if (total < MIN_TRAIN + 2 || horizon <= 0) {
            out.put("available", false);
            out.put("reason", "历史样本不足，无法可靠回测");
            out.put("historyDays", total);
            return out;
        }
        out.put("available", true);
        out.put("historyDays", total);
        out.put("horizon", horizon);

        int firstOrigin = Math.max(MIN_TRAIN, total - horizon);
        List<Double> mErr = new java.util.ArrayList<>();
        List<Double> pErr = new java.util.ArrayList<>();
        List<Double> oErr = new java.util.ArrayList<>();
        double actualSum = 0;
        int sampleDays = 0;

        for (int j = firstOrigin; j < total; j++) {
            double[] train = java.util.Arrays.copyOfRange(actual, 0, j);
            double ai = aiPredictionUtil.predictValues(train, 1).get(0).doubleValue();
            double act = actual[j];
            mErr.add(ai - act);
            double persist = actual[j - 1];
            pErr.add(persist - act);
            if (official != null && j < official.length && official[j] != null) {
                oErr.add(official[j] - act);
            }
            actualSum += act;
            sampleDays++;
        }

        double[] model = stats(mErr);
        double[] persistence = stats(pErr);
        out.put("sampleDays", sampleDays);
        out.put("modelMae", model[0]);
        out.put("modelRmse", model[1]);
        out.put("persistenceMae", persistence[0]);
        out.put("persistenceRmse", persistence[1]);
        out.put("skillVsPersistenceRmse", skill(model[1], persistence[1]));
        out.put("skillVsPersistenceMae", skill(model[0], persistence[0]));
        out.put("meanAbsActual", safeMean(java.util.Collections.singletonList(actualSum / Math.max(1, sampleDays))));

        if (!oErr.isEmpty()) {
            double[] officialStats = stats(oErr);
            out.put("officialMae", officialStats[0]);
            out.put("officialRmse", officialStats[1]);
            out.put("skillVsOfficialRmse", skill(model[1], officialStats[1]));
            out.put("officialCoveragePct", 100.0 * oErr.size() / sampleDays);
        } else {
            out.put("officialMae", null);
            out.put("officialRmse", null);
            out.put("skillVsOfficialRmse", null);
            out.put("officialCoveragePct", 0.0);
        }
        return out;
    }

    /** 返回 [MAE, RMSE]。 */
    private double[] stats(List<Double> errs) {
        int n = errs.size();
        if (n == 0) return new double[]{0, 0};
        double sAbs = 0, sSq = 0;
        for (double e : errs) { sAbs += Math.abs(e); sSq += e * e; }
        return new double[]{round(sAbs / n), round(Math.sqrt(sSq / n))};
    }

    private Double skill(double model, double base) {
        if (base <= 1e-9) return null;
        return round(1 - model / base);
    }

    private double safeMean(List<Double> l) {
        return l.isEmpty() ? 0 : round(l.get(0));
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
