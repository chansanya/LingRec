package com.lingrec.model.enums;

public enum ActionType {
    VIEW(1.0),
    LIKE(3.0),
    FAVORITE(5.0);

    private final double weight;

    /**
     * 创建带画像与热度权重的行为类型。
     *
     * @param weight 该行为参与画像计算和热度累加的权重
     */
    ActionType(double weight) {
        this.weight = weight;
    }

    /**
     * 获取行为权重。
     *
     * @return 画像计算和资源热度累加使用的权重
     */
    public double getWeight() {
        return weight;
    }
}
