package com.virjar.vscrawler.core.processor.configurableprocessor.annotiondriven;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.lang.reflect.Field;

/**
 * Created by virjar on 2017/12/10.
 * *
 *
 * @author virjar
 * @since 0.2.1
 */
@AllArgsConstructor
class FetchTaskBean {
    @Getter
    private Field field;
    @Getter
    private ModelSelector modelSelector;

    @Getter
    private boolean newSeed = false;

    @Getter
    private Class helpClazz;
}
