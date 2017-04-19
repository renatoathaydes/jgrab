package com.athaydes.jgrab;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a dependency.
 */
@Target( ElementType.TYPE )
@Retention( RetentionPolicy.SOURCE )
@Repeatable( JGrabGroup.class )
public @interface JGrab {
    String group();

    String module();

    String version() default "";

    String classifier() default "";
}
