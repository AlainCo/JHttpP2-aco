@echo off
setlocal
set BASE=%~dp0
if "%ECLIPSE_HOME%" == "" set ECLIPSE_HOME=C:\Developpement\EclipseJee47_x64
if "%PLUGINSFROM%"=="" set PLUGINSFROM=%BASE%\.
set PLUGINSTO=%ECLIPSE_HOME%\dropins
if "%jhttpp2_package%"=="" set jhttpp2_package=jhttpp2-rcp
echo.
for %%I in ( "%PLUGINSFROM%"\%jhttpp2_package%*.jar ) do (set JARFROMNAME=%%~nI&&set JAR=%%I)
echo latest jar: %JARFROMNAME%
echo.
if exist "%PLUGINSTO%\%JARFROMNAME%.jar" goto alreadyexist
echo installing plugin "%JARFROMNAME%" ...
@echo on
for %%I  in ( "%PLUGINSTO%"\%jhttpp2_package%*.jar ) do (echo move "%%I" "%%I.old" && move "%%I" "%%I.old")
for %%I  in ( "%PLUGINSTO%"\*jhttpp2?rcp*.jar ) do (echo move "%%I" "%%I.old" && move "%%I" "%%I.old")
copy "%JAR%" "%PLUGINSTO%"\.
@echo off
rem timeout /t 5
rem pause
:fin
endlocal
goto :eof
:alreadyexist
echo latest plugin "%JARFROMNAME%" already installed
goto fin