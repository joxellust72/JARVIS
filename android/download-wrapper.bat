@echo off
echo Descargando Gradle Wrapper JAR...
curl -L -o gradle\wrapper\gradle-wrapper.jar ^
  "https://github.com/gradle/gradle/raw/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
echo.
echo Listo. Ahora abre el proyecto en Android Studio.
pause
