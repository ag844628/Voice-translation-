@echo off
setlocal

set APP_HOME=%~dp0
if "%APP_HOME:~-1%"=="\" set APP_HOME=%APP_HOME:~0,-1%

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if not exist "%CLASSPATH%" (
  echo ERROR: gradle-wrapper.jar is missing. Please generate the Gradle wrapper with:
  echo   gradle wrapper
  echo or download it from the official Gradle distribution and place it at:
  echo   %CLASSPATH%
  exit /b 1
)

set JAVA_OPTS=-Xmx64m -Xms256m

"%JAVA_HOME%\bin\java" %JAVA_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
