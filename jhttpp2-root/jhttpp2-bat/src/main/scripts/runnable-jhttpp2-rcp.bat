@echo on
setlocal
if "%BASE%" == "" set BASE=%~dp0.
if "%CONFIG%" == "" set CONFIG=%BASE%
if "%CONFIGDIR%" == "" set CONFIG=%BASE%
if "%LOGDIR%" =="" set LOGDIR="%CONFIGDIR%"
if "%JAVA_HOME%" == "" set JAVA_HOME=C:\Developpement\JDK1.8_x64
if "%ECLIPSE_HOME%" == "" set ECLIPSE_HOME=C:\Developpement\EclipseJee47_x64
set PATH=%JAVA_HOME%\bin;%ECLIPSE_HOME%;%PATH%
call "%BASE%\install-jhttpp2-plugin.bat"

rem  -XX:+TraceClassLoading 
rem -Dexec.netstat=netstat -Dexec.pv=..\pv\pv.exe 
rem set NET_OPTS=-Dhttp.keepalive=true -Dhttp.maxConnections=1
set NET_OPTS=-Dhttp.keepalive=false -Dhttp.maxConnections=0
rem -Dnetworkaddress.cache.ttl=-1

set RAMOPTS=-Xmx256M -Xms128M
set  RAMOPTS=%RAMOPTS% -Xmn64m
set  RAMOPTS=%RAMOPTS% -Xss2m

goto afterjvmopts
set RAMOPTS=%RAMOPTS% -verbose:gc 
set RAMOPTS=%RAMOPTS% -XX:+PrintGCDateStamps 
set RAMOPTS=%RAMOPTS% -Xverify:none
set RAMOPTS=%RAMOPTS% -XX:+AggressiveOpts
set RAMOPTS=%RAMOPTS% -XX:+UseStringDeduplication

set RAMOPTS=%RAMOPTS% -XX:+UseG1GC
set RAMOPTS=%RAMOPTS% -XX:+CMSClassUnloadingEnabled 
set RAMOPTS=%RAMOPTS% -XX:+CMSParallelRemarkEnabled
set RAMOPTS=%RAMOPTS% -XX:+ScavengeBeforeFullGC 
set RAMOPTS=%RAMOPTS% -XX:+CMSScavengeBeforeRemark 
set RAMOPTS=%RAMOPTS% -XX:+ExplicitGCInvokesConcurrentAndUnloadsClasses


rem set RAMOPTS=%RAMOPTS% -XX:+PrintGCDetails
rem set RAMOPTS=%RAMOPTS% -XX:+UseConcMarkSweepGC
rem set  RAMOPTS=%RAMOPTS% -XX:+UseParallelGC


set JVM_TYPE=
if exist "%JAVA_HOME%\bin\server\jvm.dll" set JVM_TYPE=-server
:afterjvmopts
rem 
set JVM_ARGS=-showversion %JVM_TYPE% %RAMOPTS% %NET_OPTS%

cd /d %CONFIGDIR%
echo BASE=%BASE%
echo CONFIG=%CONFIG%
echo LOGDIR=%LOGDIR%
echo ECLIPSE_HOME=%ECLIPSE_HOME%
echo launching eclipse engine
@echo on
start "jHTTPp2" /D "%CD%" /min "%ECLIPSE_HOME%\eclipse.exe" -nosplash -application jhttpp2-rcp.application -consoleLog -noExit %CONFIGDIR%  %LOGDIR% -vmargs %JVM_ARGS%  
rem "%ECLIPSE_HOME%\eclipse.exe" -nosplash -application jhttpp2-rcp.application -consoleLog -noExit %CONFIGDIR%  %LOGDIR% -vmargs %JVM_ARGS%  
@echo off
timeout /t 5
endlocal