package com.example.babybedapp.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 分析响应模型
 */
public class AnalyzeResponse {
    @SerializedName("risk")
    public String risk;

    @SerializedName("summary")
    public String summary;

    @SerializedName("suggestions")
    public List<String> suggestions;

    public AnalyzeResponse() {
    }

    /**
     * 获取风险等级颜色
     */
    public int getRiskColor() {
        if (risk == null)
            return 0xFF888888; // 灰色
        switch (risk.toLowerCase()) {
            case "low":
            case "green":
                return 0xFF2E7D32; // 绿色
            case "medium":
            case "yellow":
                return 0xFFF9A825; // 黄色
            case "high":
            case "red":
                return 0xFFC62828; // 红色
            default:
                return 0xFF888888; // 灰色
        }
    }

    /**
     * 获取风险等级中文
     */
    public String getRiskText() {
        if (risk == null)
            return "未知";
        switch (risk.toLowerCase()) {
            case "low":
            case "green":
                return "低风险";
            case "medium":
            case "yellow":
                return "中等风险";
            case "high":
            case "red":
                return "高风险";
            default:
                return risk;
        }
    }

    @Override
    public String toString() {
        return String.format("AnalyzeResponse{risk=%s, summary=%s, suggestions=%d条}",
                risk, summary, suggestions != null ? suggestions.size() : 0);
    }
}
