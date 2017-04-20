package com.athaydes.jgrab.runner;

import com.athaydes.jgrab.JGrab;

import java.io.File;
import java.util.Collection;

/**
 * A dependency grabber.
 */
public interface Grabber {

    /**
     * Grabs the requested dependencies, putting the jar files for all dependencies
     * inside the provided directory.
     *
     * @param grabs dependencies to grab
     * @param dir   directory to put all grabbed dependencies
     */
    void grab( Collection<JGrab> grabs, File dir );

}
