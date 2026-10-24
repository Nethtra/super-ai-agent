package com.wty.superaiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 17自定义上下文增强器 位置在检索后 增强阶段
 *
 * @author 王天一
 * @version 1.0
 */
public class FitnessAppContextualQueryAugmenterFactory {

    public static ContextualQueryAugmenter create() {

        PromptTemplate promptTemplate = new PromptTemplate("没有上下文时，婉拒用户返回不知道");
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)//不允许空上下文 其实默认也是false，设置为true时大模型才会根据自身能力回答
                .emptyContextPromptTemplate(promptTemplate)//空上下文时给ai的提示词
                .build();
    }
}
