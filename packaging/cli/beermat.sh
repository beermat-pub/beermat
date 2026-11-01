#!/bin/sh
# The beermat command line (docs/cli.md), run with this Beermat's own Java and Python.
# In the app's folder: bin/ on Linux (/opt/beermat/bin), Contents/app/bin/ on macOS. Tools → Install the
# beermat command links it from the PATH.
here=$(cd "$(dirname "$0")" && pwd)
for app in "$here/../lib/app" "$here/.."; do [ -d "$app/resources/python" ] && break; done
for java in "$here/../lib/runtime/bin/java" "$here/../../runtime/Contents/Home/bin/java"; do [ -x "$java" ] && break; done
exec "$java" "-Dbeermat.python=$app/resources/python" -cp "$app/*" pub.beermat.cli.MainKt "$@"
