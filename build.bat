@echo off
rem Build debug APK.
rem Tools folder (JDK 17, Android SDK, Gradle cache). Override with ANDROID_TOOLS env var.
if not defined ANDROID_TOOLS set ANDROID_TOOLS=D:\Android
set JAVA_HOME=%ANDROID_TOOLS%\jdk17
set ANDROID_HOME=%ANDROID_TOOLS%\sdk
set GRADLE_USER_HOME=%ANDROID_TOOLS%\gradle-home
rem Use Windows certificate store (HTTPS-inspecting antivirus/proxy)
set GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT
if "%~1"=="" (call "%~dp0gradlew.bat" assembleDebug) else (call "%~dp0gradlew.bat" %*)
