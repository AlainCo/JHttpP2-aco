@echo off
setlocal
if "%BASE%" == "" set BASE=%~dp0.
if "%CONFIG%" == "" set CONFIG=%BASE%
if "%CONFIGDIR%" == "" set CONFIG=%BASE%
if "%LOGDIR%" =="" set LOGDIR="%CONFIGDIR%"
if "%JAVA_HOME%" == "" set JAVA_HOME=C:\Developpement\JDK1.8_x64
if "%ECLIPSE_HOME%" == "" set ECLIPSE_HOME=C:\Developpement\EclipseJee47_x64
if "%jhttpp2_package%"=="" set jhttpp2_package=jhttpp2-rcp
if "%PLUGINSFROM%"=="" set PLUGINSFROM=%BASE%\.
if "%JAVA_CMD%" == "" set JAVA_CMD=java

set PATH=%JAVA_HOME%\bin;%ECLIPSE_HOME%;%PATH%


rem  -XX:+TraceClassLoading 
rem -Dexec.netstat=netstat -Dexec.pv=..\pv\pv.exe 
rem set NET_OPTS=-Dhttp.keepalive=true -Dhttp.maxConnections=1
set NET_OPTS=-Dhttp.keepalive=false -Dhttp.maxConnections=0
rem -Dnetworkaddress.cache.ttl=-1

set RAMOPTS=-Xmx256M -Xms48M
set  RAMOPTS=%RAMOPTS% -Xmn128m
set  RAMOPTS=%RAMOPTS% -Xss2m

set RAMOPTS=%RAMOPTS% -verbose:gc 
set RAMOPTS=%RAMOPTS% -XX:+PrintGCDateStamps 

rem set RAMOPTS=%RAMOPTS% -XX:+PrintGCDetails
rem set RAMOPTS=%RAMOPTS% -XX:+UseConcMarkSweepGC
rem set  RAMOPTS=%RAMOPTS% -XX:+UseParallelGC
set  RAMOPTS=%RAMOPTS% -XX:+UseG1GC
set RAMOPTS=%RAMOPTS% -XX:+CMSClassUnloadingEnabled 
set RAMOPTS=%RAMOPTS% -XX:+CMSParallelRemarkEnabled
set RAMOPTS=%RAMOPTS% -XX:+ScavengeBeforeFullGC 
set RAMOPTS=%RAMOPTS% -XX:+CMSScavengeBeforeRemark 
set RAMOPTS=%RAMOPTS% -XX:+ExplicitGCInvokesConcurrentAndUnloadsClasses
set RAMOPTS=%RAMOPTS% -XX:+UseStringDeduplication
set RAMOPTS=%RAMOPTS% -XX:+AggressiveOpts
set RAMOPTS=%RAMOPTS% -Xverify:none

set DEBUG_OPTS=
if NOT "%JDWP_PORT%" == "" set DEBUG_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,address=%JDWP_PORT%,server=y,suspend=y
echo DEBUG_OPTS=%DEBUG_OPTS%

set JVM_TYPE=
if exist "%JAVA_HOME%\bin\server\jvm.dll" set JVM_TYPE=-server
set JVM_ARGS=-showversion %JVM_TYPE% %RAMOPTS% %NET_OPTS% %DEBUG_OPTS%


echo BASE=%BASE%
echo CONFIG=%CONFIG%
echo LOGDIR=%LOGDIR%
echo JAVA_HOME=%JAVA_HOME%

echo JAVA_CMD=%JAVA_CMD%

for %%I in ( "%PLUGINSFROM%"\%jhttpp2_package%*.jar ) do (echo jar found: %%~nI &&  set JAR=%%I)
echo JAR=%JAR%

cd /d %CONFIGDIR%
echo CD=%CD%

rem -Dexec.netstat=netstat -Dexec.pv=..\pv\pv.exe 
title jHTTPp2
@echo on
"%JAVA_HOME%\bin\%JAVA_CMD%" %JVM_ARGS%  -jar "%JAR%" "%CONFIGDIR%" "%LOGDIR%"
@echo off
pause
rem "%JAVA_HOME%\bin\%JAVA_CMD%" %JVM_ARGS%  -jar "%base%\runnable-jhttpp2.jar"
endlocal
goto :eof
:errorjava
echo problem with java configuration
echo path=%PATH%
echo JAVA_HOME=%JAVA_HOME%
endlocal
timeout /t 3
goto :eof
