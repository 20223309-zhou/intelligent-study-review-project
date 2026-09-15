package com.zhou.review.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;
import lombok.Setter;

@Getter
public enum QuestionConfigEnum {
    SINGLE_CHOICE("单选题", "SINGLE_CHOICE"),
    MULTIPLE_CHOICE("多选题", "MULTIPLE_CHOICE"),
    TRUE_FALSE("判断题", "TRUE_FALSE"),
    SHORT_ANSWER("简答题", "SHORT_ANSWER"),
    MATERIAL("材料题", "MATERIAL"),
    BLANK_FILLING("填空题", "BLANK_FILLING"),
    PROOF_QUESTION("证明题", "PROOF_QUESTION"),
    ESSAY("论述题", "ESSAY");

    private final String text;

    private final String value;

    QuestionConfigEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static QuestionConfigEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (QuestionConfigEnum anEnum : QuestionConfigEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
