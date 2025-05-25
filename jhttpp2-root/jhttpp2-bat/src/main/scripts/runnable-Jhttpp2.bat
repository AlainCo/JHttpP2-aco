@echo on
setlocal

if "%BASE%" == "" set BASE=%~dp0.
cd /d %BASE%


rem === config base
set CONFIGBASE=%BASE%\config


rem === CONFIG ============================


set CONFIG=%COMPUTERNAME%
if not "%1"=="" set CONFIG=%1
echo Configuration selected: "%CONFIG%"
set CONFIGDIR=%CONFIGBASE%\%CONFIG%
if exist "%CONFIGDIR%"\server.properties goto :existconfig
md "%CONFIGDIR%"
set CONFIGDEFAULT=%USERDOMAIN%-default
if exist "%CONFIGBASE%\%CONFIGDEFAULT%"\server.properties goto :havedefaultconfig
set CONFIGDEFAULT=..\config-default
:havedefaultconfig
echo Copy Config default from %CONFIGDEFAULT% to new %CONFIG%\.
copy "%CONFIGBASE%\%CONFIGDEFAULT%"\* "%CONFIGDIR%"\.
:existconfig
echo CONFIGDIR=%CONFIGDIR%

rem === LOG DIR
set LOGDIR=%TEMP%\jhttpp2-%CONFIG%
md %LOGDIR%

rem === jar =============
set jhttpp2_package=jhttpp2-rcp
set PLUGINSFROM=%BASE%\.
if NOT "%DEBUGTARGET%" == "" set PLUGINSFROM=%DEBUGTARGET%

rem === java config ===
set JAVA_CMD=java
if not exist "%CONFIGDIR%\JAVA_CMD.txt" goto donejavacmd
for /f "tokens=*" %%i in ( 'type "%CONFIGDIR%\JAVA_CMD.txt"') do set JAVA_CMD=%%i
echo JAVA_CMD.txt=%JAVA_CMD%
:donejavacmd
echo JAVA_CMD=%JAVA_CMD%

set JAVA_BASE=
if not exist "%CONFIGDIR%\JAVA_BASE.txt" goto donejavabase
for /f "tokens=*" %%i in ( 'type "%CONFIGDIR%\JAVA_BASE.txt"') do set JAVA_BASE=%%i
echo JAVA_BASE.txt=%JAVA_BASE%
:donejavabase



set JAVA_HOME=
if not exist "%CONFIGDIR%\JAVA_HOME.txt" goto donejavahome
for /f "tokens=*" %%i in ( 'type "%CONFIGDIR%\JAVA_HOME.txt"') do set JAVA_HOME=%%i
echo JAVA_HOME.txt=%JAVA_HOME%
:donejavahome
if not exist "%JAVA_HOME%\bin\java.exe" call :findjava
echo "JAVA_HOME=%JAVA_HOME%"
if not "%JAVA_HOME%" == "" set path=%path%;%JAVA_HOME%\bin;
set path=%path%;%~dp0..\PortableApps\CommonFiles\Java\bin

java -version
if errorlevel 1 goto errorjava

rem === choosing implementation 
if not exist "%CONFIGDIR%\ECLIPSE_HOME.txt" goto runjava
set ECLIPSE_HOME=
for /f "tokens=*" %%i in ( 'type "%CONFIGDIR%\ECLIPSE_HOME.txt"') do set ECLIPSE_HOME=%%i
echo ECLIPSE_HOME.txt=%JAVA_CMD%

if exist "%ECLIPSE_HOME%\eclipse.exe"  goto runeclipse
call :findeclipse
if not exist "%ECLIPSE_HOME%\eclipse.exe" ( del "%CONFIGDIR%\ECLIPSE_HOME.txt"&& set ECLIPSE_HOME=)
if "%ECLIPSE_HOME%" == "" goto runjava
echo %ECLIPSE_HOME%>"%CONFIGDIR%\ECLIPSE_HOME.txt"
goto runeclipse

rem == run eclipse ==
:runeclipse
echo running into Eclipse RCP: %ECLIPSE_HOME%
call "%BASE%"\runnable-jhttpp2-rcp.bat
goto endrun

rem == run java ==
:runjava


echo running into Java: %JAVA_HOME% command %JAVA_CMD%
rem start /min "jHTTPp2" /d "%CD%" cmd /c "%BASE%"\runnable-jhttpp2-java.bat
"%BASE%"\runnable-jhttpp2-java.bat
goto endrun

rem === end run ====
:endrun
endlocal
goto :eof

rem ==================================================
:errorjava
echo problem with java configuration
echo path=%PATH%
echo JAVA_HOME=%JAVA_HOME%
endlocal
goto :eof

rem =================================================
:findjava
set JHOME=


if "%JAVA_BASE%" == "" goto nojavabase
set PROGRAMBASE=%JAVA_BASE%
call :findjavaindir
if not "%JHOME%" == "" goto foundjava
:nojavabase


set PROGRAMBASE=%ProgramFiles%\Java
call :findjavaindir
if not "%JHOME%" == "" goto foundjava

set PROGRAMBASE=%ProgramFiles(x86)%\Java
call :findjavaindir
if not "%JHOME%" == "" goto foundjava


goto :eof
:foundjava
echo FOUND JAVA %JHOME%
set JAVA_HOME=%JHOME%
goto :eof

:findjavaindir
set JVERSION=8
call :findjavaversion
if not "%JHOME%" == "" goto foundjavaindir
set JVERSION=7
call :findjavaversion
if not "%JHOME%" == "" goto foundjavaindir
set JVERSION=6
call :findjavaversion
if not "%JHOME%" == "" goto foundjavaindir
goto :eof
:foundjavaindir
goto :eof

:findjavaversion
if exist "%PROGRAMBASE%\jre%JVERSION%" set JHOME=%PROGRAMBASE%\jre%JVERSION%
if not "%JHOME%" == "" goto foundjavaversion
for /d %%d in ("%PROGRAMBASE%"\jre1.%JVERSION%.* ) do set JHOME=%%d
if not "%JHOME%" == "" goto foundjavaversion
goto :eof
:foundjavaversion
goto :eof
rem ====================================
:findeclipse
set ECLIPSE_HOME=
for /d %%i in ( c:\developpement\EclipseJee* ) do if exist "%%i\eclipse.exe" set  ECLIPSE_HOME=%%i
if not "%ECLIPSE_HOME%" == "" goto :eof
for /d %%i in ( "%ProgramFiles%"\Eclipse* ) do if exist "%%i\eclipse.exe" set  ECLIPSE_HOME=%%i
if not "%ECLIPSE_HOME%" == "" goto :eof
if not "%ProgramFiles(x86)%" == "" for /d %%i in ( "%ProgramFiles(x86)%"\Eclipse* ) do if exist "%%i\eclipse.exe" set  ECLIPSE_HOME=%%i

 goto :eof
