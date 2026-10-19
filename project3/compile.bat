@echo off
REM Project 1 - Compilation Script (Windows)

echo === Compiling Project 1 ===
echo.

REM Create bin directory if it doesn't exist
if not exist bin mkdir bin

REM Compile source files
echo Compiling source files...
javac -d bin src\*.java

if %errorlevel% equ 0 (
    echo [SUCCESS] Source files compiled successfully
) else (
    echo [ERROR] Source compilation failed
    exit /b 1
)

REM Compile test files (if JUnit is available)
if exist test (
    echo.
    echo Compiling test files...
    
    if exist lib\junit-4.13.2.jar (
        javac -cp "bin;lib\junit-4.13.2.jar;lib\hamcrest-core-1.3.jar" -d bin test\*.java
        
        if %errorlevel% equ 0 (
            echo [SUCCESS] Test files compiled successfully
        ) else (
            echo [ERROR] Test compilation failed
            exit /b 1
        )
    ) else (
        echo [WARNING] JUnit not found in lib\ directory
        echo   To compile tests, download:
        echo   - junit-4.13.2.jar
        echo   - hamcrest-core-1.3.jar
        echo   and place them in lib\ directory
    )
)

echo.
echo === Compilation Complete ===
echo.
echo To run the benchmark:
echo   java -cp bin Benchmark
echo.
echo To run provided tests:
echo   java -cp "bin;lib\junit-4.13.2.jar;lib\hamcrest-core-1.3.jar" org.junit.runner.JUnitCore ProvidedTests
echo.
echo To run your tests:
echo   java -cp "bin;lib\junit-4.13.2.jar;lib\hamcrest-core-1.3.jar" org.junit.runner.JUnitCore StudentTests
