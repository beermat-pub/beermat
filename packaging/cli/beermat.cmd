@echo off
rem The beermat command line (docs/cli.md), run with this Beermat's own Java and Python.
rem It lives in the app's bin folder, which the installer can add to the PATH.
"%~dp0..\runtime\bin\java.exe" "-Dbeermat.python=%~dp0..\app\resources\python" -cp "%~dp0..\app\*" pub.beermat.cli.MainKt %*
