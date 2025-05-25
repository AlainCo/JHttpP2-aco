@echo off
setlocal
set PORT=3128
netstat -a -n -p TCP | find " LISTENING" | find ":%PORT% "
if errorlevel 1 goto waitproxy
goto found

:waitproxy
start "jHTTPp2" /min "%~dp0runnable-jhttpp2.bat"
echo waiting for local proxy listening on port %PORT%

:loop
timeout /t 1
netstat -a -n -p TCP | find " LISTENING" | find ":%PORT% "
if errorlevel 1 goto loop

:found
echo FOUND PROXY
timeout /t 3
endlocal
goto :eof