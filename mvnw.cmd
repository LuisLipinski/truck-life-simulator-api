@ECHO OFF
SETLOCAL
SET "SCRIPT_DIR=%~dp0"
SET "PROPS=%SCRIPT_DIR%.mvn\wrapper\maven-wrapper.properties"
IF NOT EXIST "%PROPS%" (
  ECHO Maven Wrapper properties not found: %PROPS% 1>&2
  EXIT /B 1
)

FOR /F "tokens=1,* delims==" %%A IN ('findstr /B "distributionUrl=" "%PROPS%"') DO SET "DIST_URL=%%B"
FOR /F "tokens=1,* delims==" %%A IN ('findstr /B "distributionSha256Sum=" "%PROPS%"') DO SET "DIST_SHA=%%B"
IF "%DIST_URL%"=="" (
  ECHO distributionUrl not configured 1>&2
  EXIT /B 1
)

FOR %%F IN ("%DIST_URL%") DO SET "DIST_FILE=%%~nxF"
SET "DIST_DIR=%DIST_FILE:-bin.zip=%"
IF "%MAVEN_USER_HOME%"=="" SET "MAVEN_USER_HOME=%USERPROFILE%\.m2"
SET "MAVEN_HOME=%MAVEN_USER_HOME%\wrapper\dists\%DIST_DIR%\truck-life-simulator"

IF NOT EXIST "%MAVEN_HOME%\bin\mvn.cmd" (
  SET "TMP_ZIP=%TEMP%\%DIST_FILE%"
  SET "TMP_DIR=%TEMP%\truck-life-maven-wrapper-%RANDOM%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing '%DIST_URL%' -OutFile '%TMP_ZIP%'"
  IF ERRORLEVEL 1 EXIT /B 1
  IF NOT "%DIST_SHA%"=="" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$actual=(Get-FileHash '%TMP_ZIP%' -Algorithm SHA256).Hash.ToLower(); if($actual -ne '%DIST_SHA%'){ throw 'Maven distribution checksum validation failed' }"
    IF ERRORLEVEL 1 EXIT /B 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%TMP_ZIP%' -DestinationPath '%TMP_DIR%' -Force"
  IF ERRORLEVEL 1 EXIT /B 1
  IF NOT EXIST "%MAVEN_USER_HOME%\wrapper\dists\%DIST_DIR%" MKDIR "%MAVEN_USER_HOME%\wrapper\dists\%DIST_DIR%"
  MOVE "%TMP_DIR%\%DIST_DIR%" "%MAVEN_HOME%" >NUL
  DEL /Q "%TMP_ZIP%" >NUL 2>&1
  RMDIR /S /Q "%TMP_DIR%" >NUL 2>&1
)

CALL "%MAVEN_HOME%\bin\mvn.cmd" %*
EXIT /B %ERRORLEVEL%
