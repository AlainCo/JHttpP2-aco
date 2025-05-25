@echo off
set PORT=3128
echo waiting for local proxy listening on port %PORT%
:loop
netstat -a -n -p TCP | find " LISTENING" | find ":%PORT% "
if errorlevel 1 ( timeout /t 1 && goto loop ) 
