package com.athaydes.jgrab;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Group of dependencies.
 */
@Target( ElementType.TYPE )
@Retention( RetentionPolicy.SOURCE )
public @interface JGrabGroup {
    JGrab[] value();
}
