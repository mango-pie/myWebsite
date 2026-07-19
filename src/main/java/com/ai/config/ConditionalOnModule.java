package com.ai.config;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当 {@code app.modules.*} 中列出的模块均为 {@code true} 时注册 Bean。
 * 多模块时为 AND 关系，例如 {@code @ConditionalOnModule({"blog", "chat"})}。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnModuleCondition.class)
public @interface ConditionalOnModule {

    String[] value();

    boolean matchIfMissing() default true;
}
