# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

- require token to accept requests in daemon.
- save dependencies cache periodically (1 hour intervals).

## [2.0.0] - 2024-04-28

### Changed

- Minimum required Java version changed from 8 to 11.
- Replaced Apache Ivy with [JBuild](https://github.com/renatoathaydes/jbuild/) for resolving Maven dependencies.
- Consider Maven local repository (before trying Maven Central) to resolve dependencies.
- No longer use JCenter to resolve dependencies.
- Use `JGRAB_HOME` env var to find JGrab home if available, otherwise default to `$HOME/.jgrab/`.
- Cache resolved classpaths on JGrab Home (not using Ivy repository anymore).
- Cache `ClassLoader` for resolve classpaths in memory.
- No longer allow applications to access the JGrab's own `ClassLoader`.
- Better parsing of CLI options and error reporting.
- Embed JGrab jar in jgrab-client so binary is self-contained.

## [0.6.0] - 2017-05-16

### Added

- Initial JGrab Release.
