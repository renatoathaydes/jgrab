package com.athaydes.jgrab.daemon;

import com.athaydes.jgrab.code.JavaCode;

interface Request {

}

enum StatelessRequest implements Request {
    DO_NOTHING, DIE
}

final class CodeRunRequest implements Request {
    public final JavaCode code;
    public final String[] args;

    public CodeRunRequest( JavaCode code, String[] args ) {
        this.code = code;
        this.args = args;
    }
}
