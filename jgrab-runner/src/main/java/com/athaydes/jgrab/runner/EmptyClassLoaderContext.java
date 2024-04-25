package com.athaydes.jgrab.runner;

import com.athaydes.osgiaas.api.env.ClassLoaderContext;

import java.util.Collection;
import java.util.List;

enum EmptyClassLoaderContext implements ClassLoaderContext {
    INSTANCE;

    @Override
    public ClassLoader getClassLoader() {
        return ClassLoader.getPlatformClassLoader();
    }

    @Override
    public Collection<String> getClassesIn( String s ) {
        return List.of();
    }
}
