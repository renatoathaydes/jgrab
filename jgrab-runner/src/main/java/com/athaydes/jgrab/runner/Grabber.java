package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.Dependency;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * A dependency grabber.
 */
public interface Grabber {

    /**
     * Grabs the requested dependencies.
     *
     * @param toGrab dependencies to grab
     * @return the local files containing the grabbed libraries.
     */
    List<File> grab( Collection<Dependency> toGrab );

}
