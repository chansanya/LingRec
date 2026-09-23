package com.lingrec.core.enums;

/**
 * 用户交互行为类型枚举，定义各行为在兴趣画像累加与资源热度计算中的基准权重。
 */
public enum ActionType {
    /**
     * 浏览行为，不可取消。
     */
    VIEW(1.0),

    /**
     * 点赞行为，支持二次点击取消。
     */
    LIKE(3.0),

    /**
     * 收藏行为，支持二次点击取消。
     */
    FAVORITE(5.0);

    private final double weight;

    /**
     * 构造带权重的行为类型枚举项。
     *
     * @param weight 该行为参与画像归一化打分与资源热度累加的权重分值
     */
    ActionType(double weight) {
        this.weight = weight;
    }

    /**
     * 获取当前行为类型的权重分值。
     *
     * @return 权重浮点数值
     */
    public double getWeight() {
        return weight;
    }
}
